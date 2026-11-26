// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.adb

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * Wireless-debugging pairing and the on-demand ADB shell connection.
 *
 * The connection exists only to start the shell-identity monitor; the monitor outlives it.
 * The app can reuse an existing loopback listener if explicitly enabled and verified. It never
 * starts the system's classic TCP listener.
 */
class AirModePairingManager(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val notificationController: AirModeNotificationController,
    private val log: PairingLog
) {
    enum class PairingState { IDLE, DISCOVERING, AWAITING_CODE, PAIRING, CONNECTING, CONNECTED }
    enum class PairingTrustState { UNPAIRED, PAIRED, PAIRING_REQUIRED }
    enum class PairingInput { NOTIFICATION, APP }

    data class ActiveAirEndpoint(val host: String, val port: Int, val source: String)

    private companion object {
        const val HEALTH_CHECK_MARKER = "jiumi_adb_ready"
        const val HEALTH_CHECK_COMMAND = "echo $HEALTH_CHECK_MARKER"
        const val LOCAL_ADB_HOST = "127.0.0.1"
        const val TLS_PAIRING_SERVICE = "_adb-tls-pairing._tcp"
        const val TLS_CONNECT_SERVICE = "_adb-tls-connect._tcp"
        const val PAIRING_DISCOVERY_REFRESH_MS = 8_000L
        const val PAIRING_DISCOVERY_RESTART_GAP_MS = 250L
        const val PAIRING_DISCOVERY_RESTARTS = 2
        const val PAIRING_SERVICE_TIMEOUT_MS = 20_000L
        const val PAIRING_TIMEOUT_MS = 20_000L
        const val PAIRING_KEY_SETTLE_MS = 1_000L
        const val CONNECT_TIMEOUT_MS = 25_000L
        const val CONNECT_GRACE_MS = 5_000L
        const val CONNECT_GRACE_AFTER_ENABLE_MS = 8_000L
        const val KEY_PAIRED = "adb_paired"
        const val KEY_PAIRING_REQUIRED = "adb_pairing_required"
        const val KEY_LAST_CONNECT_PORT = "adb_last_connect_port"
        const val KEY_PERSISTENCE = "adb_persistence"
        const val KEY_LOCAL_PORT = "adb_local_port"
        const val KEY_LOCAL_PORT_BOOT = "adb_local_port_boot"
        const val KEY_LOOPBACK_PORT = "adb_loopback_port"
        const val KEY_LOOPBACK_BOOT = "adb_loopback_boot"
        const val LOCAL_PORT_SOURCE = "loopback"
        const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    }

    private val prefs = context.getSharedPreferences("jiumi_adb", Context.MODE_PRIVATE)

    private val _persistenceEnabled = MutableStateFlow(prefs.getBoolean(KEY_PERSISTENCE, false))
    val persistenceEnabled: StateFlow<Boolean> = _persistenceEnabled.asStateFlow()

    val hasLocalAdbPort: Boolean get() = _persistenceEnabled.value && verifiedPort() != null

    /**
     * Whether adbd may be restarted now. Opening or closing the local port restarts adbd, and the
     * system then ends every process adbd started, the monitor among them, wherever it detached to.
     * The app says no while the monitor runs; the change waits for the next start or stop.
     */
    @Volatile var adbdRestartAllowed: () -> Boolean = { true }

    /** A classic listener previously opened by Jiumi may still need to be closed. */
    val localListenerClosePending: Boolean get() = savedLocalPort() != null
    @Volatile private var automaticReconnectSuspended = true
    private val _state = MutableStateFlow(PairingState.IDLE)
    val state: StateFlow<PairingState> = _state.asStateFlow()
    private val _pairingTrustState = MutableStateFlow(initialPairingTrustState())
    val pairingTrustState: StateFlow<PairingTrustState> = _pairingTrustState.asStateFlow()
    val isPairingRequired: Boolean
        get() = _pairingTrustState.value == PairingTrustState.PAIRING_REQUIRED
    val isShellReady: Boolean
        get() = _state.value == PairingState.CONNECTED && activeConnection != null

    // Blocking shell reads run on recyclable threads so a timed-out read never pins a caller.
    private val shellTimeoutExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "adb-shell-timeout").apply { isDaemon = true }
    }

    private val _failure = MutableStateFlow<AirPairingFailure?>(null)
    val failure: StateFlow<AirPairingFailure?> = _failure.asStateFlow()

    val isPreviouslyPaired: Boolean
        get() = _pairingTrustState.value == PairingTrustState.PAIRED && privateKeyFile().exists()

    private var pendingPairingEndpoint: InetSocketAddress? = null
    private var pairingClient: AirPairingClient? = null
    @Volatile private var activeConnection: AirAdbClient? = null
    @Volatile private var activeEndpoint: ActiveAirEndpoint? = null
    private var pairingTimeoutJob: Job? = null
    private var discoveryTimeoutJob: Job? = null
    private var queuedPairCode: String? = null
    @Volatile private var pairingInput = PairingInput.NOTIFICATION
    // A callback has not established Wi-Fi availability at process startup.
    @Volatile private var wifiTransportAvailable: Boolean? = null
    private val _wirelessTransportReady = MutableStateFlow(false)
    val wirelessTransportReady: StateFlow<Boolean> = _wirelessTransportReady.asStateFlow()

    // Serialize discovery callbacks, attempt completion, and cancellation on the manager.
    private var pairingDiscovery: AirMdnsDiscovery? = null
    private var pairingDiscoveryRefreshJob: Job? = null
    private var pairingDiscoveryGeneration = 0L
    private var pairingAttemptGeneration = 0L
    private var connectDiscovery: AirMdnsDiscovery? = null
    private var connectScanJob: Job? = null
    private var pendingConnection: AirAdbClient? = null
    private val connectionMutex = Mutex()
    private val connectAttemptGeneration = AtomicLong()

    init {
        if (isPairingRequired) {
            prefs.edit().remove(KEY_PAIRED).remove(KEY_LAST_CONNECT_PORT).putBoolean(KEY_PAIRING_REQUIRED, true).apply()
            _failure.value = AirPairingFailure.PAIRING_REQUIRED
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(ADB_WIFI_ENABLED), false, object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) = updateWirelessTransportReady()
                }
            )
        }.onFailure { log.event("could not observe Wireless debugging availability: ${it.javaClass.simpleName}") }
    }

    private fun privateKeyFile(): File = File(context.filesDir, "adb_key")
    private fun publicKeyFile(): File = File(context.filesDir, "adb_key.pub")

    private fun initialPairingTrustState(): PairingTrustState = when {
        prefs.getBoolean(KEY_PAIRING_REQUIRED, false) -> PairingTrustState.PAIRING_REQUIRED
        prefs.getBoolean(KEY_PAIRED, false) && privateKeyFile().exists() -> PairingTrustState.PAIRED
        prefs.getBoolean(KEY_PAIRED, false) -> PairingTrustState.PAIRING_REQUIRED
        else -> PairingTrustState.UNPAIRED
    }

    private fun loadOrCreateAdbKey(replaceInvalidKey: Boolean = false): AirAdbKey =
        AirAdbKey.loadOrCreate(privateKeyFile(), publicKeyFile(), replaceInvalidKey) { error ->
            log.event("local ADB key rejected: ${error.diagnostic}")
        }

    /**
     * Starts looking for the system's pairing dialog, or switches an ongoing search to [input]. An
     * ongoing search keeps its endpoint; one that went quiet is started again.
     */
    @Synchronized
    private fun startDiscovery(input: PairingInput): Boolean {
        if (_state.value in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE)) {
            if (input == PairingInput.APP) {
                if (pairingInput != input) {
                    pairingInput = input
                    notificationController.cancel()
                    _failure.value = null
                }
            } else {
                if (!showPairingCodeNotification(alert = false)) return false
                pairingInput = input
            }
            resumeIdleSearch()
            return true
        }
        if (_state.value != PairingState.IDLE) return false
        if (input == PairingInput.NOTIFICATION) {
            val availability = notificationController.availability()
            if (availability != AirPairingNotificationAvailability.AVAILABLE) {
                _failure.value = pairingNotificationFailure(availability)
                log.event("pairing discovery withheld: notification=${availability.name.lowercase()}")
                return false
            }
        }
        automaticReconnectSuspended = false
        _failure.value = null
        queuedPairCode = null
        pendingPairingEndpoint = null
        stopConnectAttempt()
        pairingInput = input
        _state.value = PairingState.DISCOVERING
        log.event(
            "pairing discovery started input=${input.name.lowercase()} manufacturer=${android.os.Build.MANUFACTURER} " +
                "model=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT} " +
                "wireless=${isWirelessDebuggingEnabled()}"
        )
        if (input == PairingInput.APP) {
            notificationController.cancel()
        } else if (!showPairingCodeNotification(alert = false)) {
            // Prepare a silent RemoteInput while foreground so it remains usable if frozen.
            // Alert only after discovering the service opened by the system pairing-code dialog.
            return false
        }
        // Connecting before pairing grants trust can reject the new key and reset this flow.
        startPairingDiscovery()
        return true
    }

    /** A search that went quiet (see [startPairingDiscovery]) listens again; a running one carries on. */
    private fun resumeIdleSearch() {
        if (_state.value == PairingState.DISCOVERING && queuedPairCode == null &&
            pairingDiscoveryRefreshJob?.isActive != true) startPairingDiscovery()
    }

    /** No endpoint yet: looking for the dialog, or holding a submitted code until the dialog appears. */
    private fun isSearchingForPairingEndpoint(): Boolean =
        pendingPairingEndpoint == null && (_state.value == PairingState.DISCOVERING ||
            (_state.value == PairingState.PAIRING && queuedPairCode != null))

    private fun startPairingDiscovery() {
        pairingDiscoveryRefreshJob?.cancel()
        installPairingDiscovery()
        pairingDiscoveryRefreshJob = appScope.launch {
            repeat(PAIRING_DISCOVERY_RESTARTS) {
                delay(PAIRING_DISCOVERY_REFRESH_MS)
                synchronized(this@AirModePairingManager) {
                    if (!isActive || !isSearchingForPairingEndpoint()) return@launch
                    pairingDiscoveryGeneration++
                    pairingDiscovery?.stop()
                    pairingDiscovery = null
                }
                delay(PAIRING_DISCOVERY_RESTART_GAP_MS)
                synchronized(this@AirModePairingManager) {
                    if (!isActive || !isSearchingForPairingEndpoint()) return@launch
                    installPairingDiscovery()
                }
            }
            delay(PAIRING_DISCOVERY_REFRESH_MS)
            synchronized(this@AirModePairingManager) {
                // Stop listening and release the multicast lock. The state stays DISCOVERING, since the dialog
                // was not found; opening Wireless debugging again or submitting a code listens again.
                if (isActive && _state.value == PairingState.DISCOVERING && queuedPairCode == null) {
                    stopPairingDiscovery()
                    log.event("pairing discovery paused without an endpoint")
                }
            }
        }
    }

    private fun installPairingDiscovery() {
        val generation = ++pairingDiscoveryGeneration
        pairingDiscovery?.stop()
        pairingDiscovery = AirMdnsDiscovery(
            context = context,
            appScope = appScope,
            serviceType = TLS_PAIRING_SERVICE,
            onResolved = { host, port ->
                synchronized(this) {
                    if (generation != pairingDiscoveryGeneration || !isSearchingForPairingEndpoint()) {
                        return@AirMdnsDiscovery
                    }
                    val endpoint = InetSocketAddress.createUnresolved(host, port)
                    val queued = queuedPairCode
                    if (queued != null) {
                        doExecutePairing(endpoint, queued, rediscoverOnEndpointFailure = false)
                    } else {
                        pendingPairingEndpoint = endpoint
                        moveToAwaitingCode()
                    }
                }
            },
            onDiagnostic = { detail -> log.event("pairing mDNS: $detail") }
        ).also { it.start() }
    }

    private fun stopPairingDiscovery() {
        pairingDiscoveryGeneration++
        pairingDiscoveryRefreshJob?.cancel()
        pairingDiscoveryRefreshJob = null
        pairingDiscovery?.stop()
        pairingDiscovery = null
    }

    private fun pairingInProgress(): Boolean =
        _state.value in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE, PairingState.PAIRING)

    /**
     * Called as Wireless debugging is opened for pairing. The system gives its code dialog a new port
     * each time it opens, so a found endpoint is dropped and the search starts again.
     */
    @Synchronized
    fun preparePairingSettings() {
        if (_state.value !in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE) || queuedPairCode != null) return
        if (pairingInput == PairingInput.NOTIFICATION && !showPairingCodeNotification(alert = false)) return
        pendingPairingEndpoint = null
        _state.value = PairingState.DISCOVERING
        startPairingDiscovery()
    }

    @Synchronized
    fun stopDiscovery() {
        if (!pairingInProgress()) return
        automaticReconnectSuspended = true
        clearPairingAttempt()
        _failure.value = AirPairingFailure.PAIRING_REQUIRED.takeIf { isPairingRequired }
        notificationController.cancel()
        _state.value = PairingState.IDLE
    }

    /** Leaving the in-app code pad ends a search it started, but not a code already submitted. */
    @Synchronized
    fun stopInAppPairing() {
        if (pairingInput == PairingInput.APP && queuedPairCode == null &&
            _state.value in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE)) stopDiscovery()
    }

    private fun clearPairingAttempt() {
        pairingAttemptGeneration++
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
        pairingClient?.runCatching { close() }
        pairingClient = null
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = null
        queuedPairCode = null
        pendingPairingEndpoint = null
        stopPairingDiscovery()
    }

    /**
     * Opens an ADB shell with the trusted key. [restoreWirelessDebugging] turns the system switch
     * back on and is reserved for an explicit user request; background paths never change it.
     */
    @Synchronized
    fun reconnect(restoreWirelessDebugging: Boolean): Boolean {
        if (!isPreviouslyPaired) return false
        if (isShellReady) return true
        if (_state.value != PairingState.IDLE) return _state.value == PairingState.CONNECTING
        automaticReconnectSuspended = false
        if (wifiTransportAvailable == false && !hasLocalAdbPort) {
            _failure.value = AirPairingFailure.WIFI_UNAVAILABLE
            return false
        }
        _failure.value = null
        _state.value = PairingState.CONNECTING
        startConnectDiscovery(restoreWirelessDebugging)
        return true
    }

    @Synchronized
    fun onWifiNetworkChanged(trigger: String, wifiAvailable: Boolean) {
        if (wifiTransportAvailable == wifiAvailable) return
        wifiTransportAvailable = wifiAvailable
        updateWirelessTransportReady()
        if (!isPreviouslyPaired || automaticReconnectSuspended || pairingInProgress()) return
        log.event("wifi transport changed trigger=${trigger.take(40)} available=$wifiAvailable state=${_state.value}")
        // The local channel does not ride on Wi-Fi: a shell on it, or one being opened, carries on.
        if (hasLocalAdbPort && (activeEndpoint?.source == LOCAL_PORT_SOURCE || _state.value == PairingState.CONNECTING)) {
            if (!wifiAvailable && _failure.value == AirPairingFailure.WIFI_UNAVAILABLE) _failure.value = null
            return
        }
        stopConnectAttempt()
        markShellDown("Wi-Fi transport changed")
        if (wifiAvailable || hasLocalAdbPort) {
            _failure.value = null
            _state.value = PairingState.CONNECTING
            startConnectDiscovery(restoreWirelessDebugging = false)
        } else {
            _failure.value = AirPairingFailure.WIFI_UNAVAILABLE
        }
    }

    fun hasSecureSettingsPermission(): Boolean =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    @Synchronized
    private fun updateWirelessTransportReady() {
        _wirelessTransportReady.value = wifiTransportAvailable == true && isWirelessDebuggingEnabled() != false
    }

    fun isWirelessDebuggingEnabled(): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED) == 1
    }.getOrNull()

    private fun restoreWirelessDebugging(): Boolean {
        if (!isPreviouslyPaired || wifiTransportAvailable == false || !hasSecureSettingsPermission()) return false
        if (isWirelessDebuggingEnabled() == true) return true
        log.event("wireless debugging restored on user request")
        return runCatching { Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED, 1) }.getOrDefault(false)
    }

    @Synchronized
    fun requestPairingFromNotification(session: String?, input: String?, hasResults: Boolean) {
        fun record(result: String) = log.event(
            "pairing notification input result=$result has_results=$hasResults input_length=${input?.length ?: -1}"
        )
        if (pairingInput != PairingInput.NOTIFICATION || session == null ||
            session != notificationController.currentPairingSession()) {
            record("stale_session")
            return
        }
        if (queuedPairCode != null ||
            _state.value in setOf(PairingState.PAIRING, PairingState.CONNECTING, PairingState.CONNECTED)) {
            record("already_in_progress")
            return
        }
        val code = input?.trim()
        val failure = when {
            input == null -> AirPairingFailure.NOTIFICATION_INPUT_MISSING
            code == null || !isPairingCode(code) -> AirPairingFailure.INVALID_PAIRING_CODE
            else -> null
        }
        if (failure != null) {
            record(if (!hasResults) "missing_results" else if (input == null) "missing_field" else "invalid_input")
            _failure.value = failure
            showPairingCodeNotification(alert = true)
            return
        }
        if (_state.value == PairingState.IDLE && !isPreviouslyPaired) {
            automaticReconnectSuspended = false
            stopConnectAttempt()
            _state.value = PairingState.DISCOVERING
            record("session_restored")
        } else {
            record("received")
        }
        requestPairing(checkNotNull(code))
    }

    @Synchronized
    fun submitInAppPairingCode(code: String): Boolean {
        if (pairingInput != PairingInput.APP ||
            _state.value !in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE)) return false
        if (!isPairingCode(code)) {
            _failure.value = AirPairingFailure.INVALID_PAIRING_CODE
            return false
        }
        requestPairing(code)
        return true
    }

    private fun isPairingCode(code: String) = code.length == 6 && code.all { it in '0'..'9' }

    private fun requestPairing(code: String) {
        if (_state.value !in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE)) {
            _failure.value = AirPairingFailure.PAIRING_REQUEST_UNAVAILABLE
            runCatching { notificationController.showPairingUnavailable() }
            return
        }
        _failure.value = null
        val endpoint = pendingPairingEndpoint
        if (endpoint != null) {
            // Some devices advertise the pairing service only when the code dialog opens.
            doExecutePairing(endpoint, code, rediscoverOnEndpointFailure = true)
        } else {
            waitForPairingService(code)
        }
    }

    private fun waitForPairingService(code: String) {
        queuedPairCode = code
        pendingPairingEndpoint = null
        val attemptGeneration = pairingAttemptGeneration
        if (pairingInput == PairingInput.NOTIFICATION) {
            runCatching { notificationController.showPairingWaitingForService(PAIRING_SERVICE_TIMEOUT_MS) }
        }
        // The code is in: show it as pairing while the dialog's endpoint is still being looked for.
        _state.value = PairingState.PAIRING
        if (pairingDiscoveryRefreshJob?.isActive != true) startPairingDiscovery()
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = appScope.launch {
            delay(PAIRING_SERVICE_TIMEOUT_MS)
            synchronized(this@AirModePairingManager) {
                if (pairingAttemptGeneration == attemptGeneration && queuedPairCode != null) {
                    awaitNewPairingCode(
                        AirPairingFailure.PAIRING_SERVICE_NOT_FOUND,
                        "pairing endpoint discovery timed out after code submission"
                    )
                }
            }
        }
    }

    private fun moveToAwaitingCode() {
        // A new dialog was found: an earlier attempt's failure no longer applies.
        _failure.value = null
        if (pairingInput == PairingInput.NOTIFICATION && !showPairingCodeNotification(alert = true)) return
        stopPairingDiscovery()
        _state.value = PairingState.AWAITING_CODE
    }

    private fun showPairingCodeNotification(alert: Boolean): Boolean {
        val postResult = notificationController.showPairingRequest(_failure.value, alert = alert)
        if (postResult.posted) return true
        log.event("pairing code notification unavailable: ${postResult.availability.name.lowercase()} ${postResult.detail.take(160)}")
        stopDiscovery()
        _failure.value = pairingNotificationFailure(postResult.availability)
        return false
    }

    private fun pairingNotificationFailure(availability: AirPairingNotificationAvailability): AirPairingFailure =
        when (availability) {
            AirPairingNotificationAvailability.PERMISSION_REQUIRED -> AirPairingFailure.NOTIFICATION_PERMISSION_REQUIRED
            AirPairingNotificationAvailability.APP_NOTIFICATIONS_DISABLED -> AirPairingFailure.APP_NOTIFICATIONS_DISABLED
            AirPairingNotificationAvailability.CHANNEL_DISABLED -> AirPairingFailure.NOTIFICATION_CHANNEL_DISABLED
            AirPairingNotificationAvailability.POST_FAILED,
            AirPairingNotificationAvailability.AVAILABLE -> AirPairingFailure.NOTIFICATION_POST_FAILED
        }

    /**
     * Ends a failed attempt and waits for a new code. The endpoint is dropped, since a new code means a
     * new dialog and port, so the state is DISCOVERING; the next code or settings visit listens again.
     */
    private fun awaitNewPairingCode(failure: AirPairingFailure, diagnostic: String) {
        clearPairingAttempt()
        _failure.value = failure
        _state.value = PairingState.DISCOVERING
        log.event(diagnostic)
        if (pairingInput == PairingInput.NOTIFICATION) showPairingCodeNotification(alert = true)
    }

    private fun doExecutePairing(endpoint: InetSocketAddress, code: String, rediscoverOnEndpointFailure: Boolean) {
        clearPairingAttempt()
        val attemptGeneration = pairingAttemptGeneration
        _failure.value = null
        if (pairingInput == PairingInput.NOTIFICATION) {
            runCatching { notificationController.showPairingSubmitted(PAIRING_TIMEOUT_MS) }
        }
        _state.value = PairingState.PAIRING
        pairingTimeoutJob = appScope.launch {
            delay(PAIRING_TIMEOUT_MS)
            synchronized(this@AirModePairingManager) {
                if (pairingAttemptGeneration == attemptGeneration) {
                    awaitNewPairingCode(AirPairingFailure.PAIRING_TIMEOUT, "pairing timed out waiting for the system service")
                }
            }
        }
        appScope.launch(Dispatchers.IO) {
            val result = runCatching {
                synchronized(this@AirModePairingManager) {
                    if (pairingAttemptGeneration != attemptGeneration) return@launch
                }
                val key = loadOrCreateAdbKey(replaceInvalidKey = true)
                val client = synchronized(this@AirModePairingManager) {
                    if (pairingAttemptGeneration != attemptGeneration) return@launch
                    AirPairingClient(endpoint.hostString, endpoint.port, code, key).also { pairingClient = it }
                }
                client.pair()
            }
            synchronized(this@AirModePairingManager) {
                if (pairingAttemptGeneration != attemptGeneration) return@launch
                val error = result.exceptionOrNull()
                when {
                    result.getOrNull() == true -> {
                        clearPairingAttempt()
                        markPairingTrusted()
                        if (pairingInput == PairingInput.NOTIFICATION) {
                            runCatching { notificationController.showPairingSucceeded() }
                        }
                        _state.value = PairingState.CONNECTING
                        startConnectDiscovery(restoreWirelessDebugging = false, afterPairing = true)
                    }
                    error is PairingEndpointException && rediscoverOnEndpointFailure -> {
                        clearPairingAttempt()
                        log.event("previously discovered pairing endpoint unavailable; rediscovering")
                        waitForPairingService(code)
                    }
                    error == null -> awaitNewPairingCode(
                        AirPairingFailure.PAIRING_REJECTED, "pairing rejected by the system service"
                    )
                    else -> awaitNewPairingCode(
                        if (error is SocketTimeoutException || error.cause is SocketTimeoutException) AirPairingFailure.PAIRING_TIMEOUT
                        else AirPairingFailure.PAIRING_CONNECTION_FAILED,
                        "pairing connection failed: ${error::class.java.simpleName}: ${error.message.orEmpty().take(120)}"
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiIpAddress(): String? = runCatching {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val ip = wm?.connectionInfo?.ipAddress?.takeIf { it != 0 } ?: return null
        "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }.getOrNull()

    private fun getAdbWifiPort(): Int? = runCatching {
        Settings.Global.getString(context.contentResolver, "adb_wifi_port")?.toIntOrNull()?.takeIf { it > 0 }
    }.getOrNull()

    private fun isConnectionBlockedByPairingRequirement(): Boolean =
        !isPreviouslyPaired || _state.value in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE, PairingState.PAIRING)

    @Synchronized
    private fun failConnection(generation: Long, failure: AirPairingFailure, diagnostic: String) {
        if (!isCurrentConnectAttempt(generation)) return
        automaticReconnectSuspended = true
        stopConnectAttempt()
        _failure.value = failure
        _state.value = PairingState.IDLE
        log.event(diagnostic)
        runCatching { notificationController.showConnectionFailure(failure) }
    }

    @Synchronized
    private fun markPairingRequired(generation: Long, diagnostic: String) {
        if (!isCurrentConnectAttempt(generation)) return
        automaticReconnectSuspended = true
        stopConnectAttempt()
        prefs.edit().remove(KEY_PAIRED).remove(KEY_LAST_CONNECT_PORT).putBoolean(KEY_PAIRING_REQUIRED, true).apply()
        _pairingTrustState.value = PairingTrustState.PAIRING_REQUIRED
        _failure.value = AirPairingFailure.PAIRING_REQUIRED
        _state.value = PairingState.IDLE
        log.event(diagnostic)
        runCatching { notificationController.showPairingRequired() }
    }

    private fun markPairingTrusted(connectPort: Int? = null) {
        val editor = prefs.edit().putBoolean(KEY_PAIRED, true).remove(KEY_PAIRING_REQUIRED)
        if (connectPort != null) editor.putInt(KEY_LAST_CONNECT_PORT, connectPort)
        editor.apply()
        _pairingTrustState.value = PairingTrustState.PAIRED
    }

    private fun isCurrentConnectAttempt(generation: Long): Boolean =
        generation == connectAttemptGeneration.get() && _state.value == PairingState.CONNECTING

    private fun stopConnectAttempt() {
        connectAttemptGeneration.incrementAndGet()
        connectScanJob?.cancel()
        connectScanJob = null
        pendingConnection?.abort()
        pendingConnection = null
        connectDiscovery?.stop()
        connectDiscovery = null
    }

    @Synchronized
    private fun markShellDown(message: String, connection: AirAdbClient? = activeConnection) {
        if (connection !== activeConnection) {
            connection?.close()
            return
        }
        activeConnection?.close()
        activeConnection = null
        activeEndpoint = null
        if (_state.value == PairingState.CONNECTED || _state.value == PairingState.CONNECTING) {
            _state.value = PairingState.IDLE
        }
        log.event("shell down: ${message.take(200)}")
    }

    private suspend fun doConnect(
        host: String,
        port: Int,
        source: String,
        generation: Long,
        key: AirAdbKey,
        allowLocalAuthorization: Boolean
    ): Boolean = connectionMutex.withLock {
        val local = source == LOCAL_PORT_SOURCE
        val connection = synchronized(this) {
            if (!isCurrentConnectAttempt(generation)) return@withLock false
            AirAdbClient(host, port, key, allowUserAuthorization = local && allowLocalAuthorization, requireTls = !local)
                .also { pendingConnection = it }
        }
        try {
            connection.connect()
            currentCoroutineContext().ensureActive()
            val healthOutput = runCatching { connection.shell(HEALTH_CHECK_COMMAND) }.getOrElse { error ->
                if (!local) failConnection(generation, AirPairingFailure.ADB_HEALTH_CHECK_FAILED,
                    "ADB health check failed: ${error::class.java.simpleName}")
                return@withLock false
            }
            if (healthOutput.exitCode != 0 || healthOutput.output.lineSequence().none { it.trim() == HEALTH_CHECK_MARKER }) {
                if (!local) failConnection(generation, AirPairingFailure.ADB_HEALTH_RESPONSE_MISSING, "ADB health check missing marker")
                return@withLock false
            }
            val identityOutput = runCatching { connection.shell("id -u") }.getOrElse { error ->
                if (!local) failConnection(generation, AirPairingFailure.ADB_IDENTITY_CHECK_FAILED,
                    "ADB identity check failed: ${error::class.java.simpleName}")
                return@withLock false
            }
            val uid = identityOutput.uid
            if (uid != AdbShellResult.ANDROID_SHELL_UID) {
                val failure = if (uid == null) AirPairingFailure.ADB_IDENTITY_RESPONSE_MISSING else AirPairingFailure.ADB_WRONG_IDENTITY
                val diagnostic = "ADB shell identity check rejected source=$source uid=$uid exit=${identityOutput.exitCode}"
                if (local) forgetVerifiedPort()
                if (!local || wifiTransportAvailable == false) failConnection(generation, failure, diagnostic)
                else log.event(diagnostic)
                return@withLock false
            }
            currentCoroutineContext().ensureActive()
            val loopback = if (local || _persistenceEnabled.value) loopbackPort(connection) else null
            synchronized(this) {
                if (!isCurrentConnectAttempt(generation)) return@withLock false
                if (local && loopback != port) {
                    forgetVerifiedPort()
                    return@withLock false
                }
                if (!local && _persistenceEnabled.value) {
                    val boot = bootCount()
                    if (loopback == null || boot < 0) forgetVerifiedPort()
                    else prefs.edit().putInt(KEY_LOOPBACK_PORT, loopback).putInt(KEY_LOOPBACK_BOOT, boot).apply()
                }
                activeConnection = connection
                activeEndpoint = ActiveAirEndpoint(host, port, source)
                markPairingTrusted(connectPort = port.takeUnless { local })
                _failure.value = null
                log.event("ADB shell connected source=$source uid=$uid")
                _state.value = PairingState.CONNECTED
                true
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!local && AirAdbConnectionFailurePolicy.requiresPairing(error)) throw error
            if (isCurrentConnectAttempt(generation)) {
                log.event("ADB connection failed source=$source: ${error.javaClass.simpleName}")
            }
            false
        } finally {
            synchronized(this) {
                if (pendingConnection === connection) pendingConnection = null
                if (activeConnection !== connection) connection.close()
            }
        }
    }

    private fun startConnectDiscovery(restoreWirelessDebugging: Boolean, afterPairing: Boolean = false) {
        if (isConnectionBlockedByPairingRequirement() || automaticReconnectSuspended) return
        stopConnectAttempt()
        val generation = connectAttemptGeneration.get()
        connectScanJob = appScope.launch(Dispatchers.IO) {
            val deadline = launch {
                delay(CONNECT_TIMEOUT_MS)
                failConnection(generation, AirPairingFailure.ADB_ENDPOINT_NOT_FOUND, "ADB connection timed out")
            }
            try {
                // Android persists the newly authorized key after the pairing exchange completes.
                if (afterPairing) delay(PAIRING_KEY_SETTLE_MS)
                connectToAvailableEndpoint(generation, restoreWirelessDebugging, afterPairing)
            } catch (error: CancellationException) {
                throw error
            } catch (_: InvalidAdbPrivateKeyException) {
                markPairingRequired(generation, "local ADB private key is invalid; explicit re-pairing required")
            } catch (error: Exception) {
                failConnection(generation, AirPairingFailure.ADB_CONNECTION_FAILED,
                    "ADB connection setup failed: ${error.javaClass.simpleName}")
            } finally {
                deadline.cancel()
                synchronized(this@AirModePairingManager) {
                    if (generation == connectAttemptGeneration.get()) {
                        connectDiscovery?.stop()
                        connectDiscovery = null
                        connectScanJob = null
                    }
                }
            }
        }
    }

    private suspend fun connectToAvailableEndpoint(
        generation: Long,
        restoreWirelessDebugging: Boolean,
        afterPairing: Boolean
    ) {
        val key by lazy { loadOrCreateAdbKey() }
        var retryNewAuthorization = afterPairing
        suspend fun tryEndpoint(host: String, port: Int, source: String): Boolean {
            while (isCurrentConnectAttempt(generation)) {
                currentCoroutineContext().ensureActive()
                try {
                    return doConnect(host, port, source, generation, key, allowLocalAuthorization = restoreWirelessDebugging)
                } catch (error: Exception) {
                    if (!AirAdbConnectionFailurePolicy.requiresPairing(error)) throw error
                    if (!retryNewAuthorization) {
                        markPairingRequired(generation, "ADB authorization rejected: ${error.message.orEmpty().take(120)}")
                        return false
                    }
                    retryNewAuthorization = false
                    log.event("waiting for Android to register the newly paired key")
                    delay(PAIRING_KEY_SETTLE_MS)
                }
            }
            return false
        }

        val localPort = verifiedPort()?.takeIf { _persistenceEnabled.value }
        if (localPort != null && tryEndpoint(LOCAL_ADB_HOST, localPort, LOCAL_PORT_SOURCE)) return
        if (wifiTransportAvailable == false) {
            val failure = if (localPort == null) AirPairingFailure.WIFI_UNAVAILABLE else AirPairingFailure.LOCAL_ADB_CONNECTION_FAILED
            failConnection(generation, failure, "connect skipped: Wi-Fi unavailable")
            return
        }
        currentCoroutineContext().ensureActive()
        val wirelessEnabled = isWirelessDebuggingEnabled()
        val turnedOn = restoreWirelessDebugging && wirelessEnabled == false && restoreWirelessDebugging()
        if (wirelessEnabled == false && !turnedOn) {
            failConnection(generation, AirPairingFailure.WIRELESS_DEBUGGING_OFF, "connect skipped: wireless debugging off")
            return
        }
        val ports = AirAdbPortPolicy.connectPorts(
            lastPort = prefs.getInt(KEY_LAST_CONNECT_PORT, -1).takeIf { it in 1..65535 },
            settingsPort = getAdbWifiPort()
        )
        // A working saved loopback port needs neither mDNS nor a multicast lock.
        for (port in ports) {
            if (tryEndpoint(LOCAL_ADB_HOST, port, "port_scan")) return
        }
        val advertised = Channel<Pair<String, Int>>(Channel.CONFLATED)
        synchronized(this) {
            if (!isCurrentConnectAttempt(generation)) return
            connectDiscovery = AirMdnsDiscovery(
                context = context, appScope = appScope, serviceType = TLS_CONNECT_SERVICE,
                onResolved = { host, port -> advertised.trySend(host to port) },
                onDiagnostic = { detail -> log.event("connect mDNS: $detail") }
            ).also { it.start() }
        }
        val wifiHost = getWifiIpAddress()
        if (wifiHost != null) {
            for (port in ports) {
                if (tryEndpoint(wifiHost, port, "port_scan")) return
            }
        }
        val grace = if (turnedOn) CONNECT_GRACE_AFTER_ENABLE_MS else CONNECT_GRACE_MS
        val endpoint = withTimeoutOrNull(grace) { advertised.receive() }
        // Discovery checks that the port now has a listener. It can become ready after a cached-port attempt.
        if (endpoint != null && tryEndpoint(endpoint.first, endpoint.second, "mdns")) return
        failConnection(generation, AirPairingFailure.ADB_ENDPOINT_NOT_FOUND, "ADB connect discovery finished without a usable endpoint")
    }

    private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** The local port opened in this boot; a restart closes it, so one from an earlier boot is ignored. */
    private fun savedLocalPort(): Int? = prefs.getInt(KEY_LOCAL_PORT, -1)
        .takeIf { it in 49152..65535 && bootCount() >= 0 && prefs.getInt(KEY_LOCAL_PORT_BOOT, -2) == bootCount() }

    fun isUsbDebuggingEnabled(): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED) == 1
    }.getOrNull()

    fun setPersistenceEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_PERSISTENCE, on).apply()
        _persistenceEnabled.value = on
        if (!on) forgetVerifiedPort()
        log.event("loopback reuse ${if (on) "enabled" else "disabled"}")
    }

    private fun verifiedPort(): Int? = prefs.getInt(KEY_LOOPBACK_PORT, -1)
        .takeIf { it in 1..65535 && bootCount() >= 0 && prefs.getInt(KEY_LOOPBACK_BOOT, -2) == bootCount() }

    private fun forgetVerifiedPort() {
        prefs.edit().remove(KEY_LOOPBACK_PORT).remove(KEY_LOOPBACK_BOOT).apply()
    }

    private fun loopbackPort(connection: AirAdbClient): Int? = runCatching {
        val addresses = connection.shell("getprop service.adb.listen_addrs")
        val listeners = connection.shell("ss -ltn")
        if (addresses.exitCode == 0 && listeners.exitCode == 0) {
            LoopbackAdbPort.verified(addresses.output, listeners.output)
        } else null
    }.getOrNull()

    /**
     * Turns persistence off and closes the local port, by asking adbd to return to USB mode. The port
     * stays recorded when that cannot be confirmed, so it can be closed later.
     */
    suspend fun disablePersistence(): Boolean {
        setPersistenceEnabled(false)
        if (!adbdRestartAllowed()) {
            log.event("local listener close deferred: adbd restart not allowed now")
            return false
        }
        return closeLocalListener()
    }

    /** Closes a port left open by [disablePersistence] while adbd could not be restarted. */
    suspend fun closePendingLocalListener(): Boolean =
        if (localListenerClosePending && adbdRestartAllowed()) closeLocalListener() else false

    private suspend fun closeLocalListener(): Boolean = connectionMutex.withLock {
        val port = savedLocalPort() ?: run {
            prefs.edit().remove(KEY_LOCAL_PORT).remove(KEY_LOCAL_PORT_BOOT).apply()
            return@withLock true
        }
        val connection = activeConnection ?: AirAdbClient(LOCAL_ADB_HOST, port, loadOrCreateAdbKey(), allowUserAuthorization = true)
        val closed = runCatching {
            if (connection !== activeConnection) connection.connect()
            val legacyPort = connection.shell("getprop service.adb.tcp.port")
            if (legacyPort.exitCode != 0) return@runCatching false
            if (legacyPort.output.trim().toIntOrNull() != port) return@runCatching true
            val addresses = connection.shell("getprop service.adb.listen_addrs")
            if (addresses.exitCode != 0) return@runCatching false
            if (addresses.output.isNotBlank()) return@runCatching true
            connection.restartUsb()
            repeat(20) {
                if (runCatching { java.net.Socket().use { it.connect(InetSocketAddress(LOCAL_ADB_HOST, port), 250) } }.isFailure) return@runCatching true
                delay(250)
            }
            false
        }.getOrDefault(false)
        if (connection !== activeConnection) connection.runCatching { close() }
        log.event("local listener close port=$port closed=$closed")
        if (closed) {
            prefs.edit().remove(KEY_LOCAL_PORT).remove(KEY_LOCAL_PORT_BOOT).apply()
            markShellDown("local listener closed")
        } else {
            _failure.value = AirPairingFailure.LOCAL_PORT_CLOSE_FAILED
        }
        closed
    }

    /** Runs a command on the live shell. A transport failure closes the connection. */
    fun shell(command: String, timeoutMs: Long = 15_000L): AdbShellResult? {
        val conn = activeConnection ?: return null
        val future = shellTimeoutExecutor.submit<AdbShellResult> { conn.shell(command) }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            markShellDown("shell timeout after ${timeoutMs}ms", conn)
            null
        } catch (error: Exception) {
            if (error is InterruptedException) {
                future.cancel(true)
                Thread.currentThread().interrupt()
            }
            markShellDown("shell error: ${(error.cause ?: error).javaClass.simpleName}", conn)
            null
        }
    }

    /** Closes the shell and stops all reconnection; the monitor keeps running on its own. */
    @Synchronized
    fun disconnect(keepPairingInProgress: Boolean = false) {
        if (keepPairingInProgress && pairingInProgress()) return
        automaticReconnectSuspended = true
        clearPairingAttempt()
        stopConnectAttempt()
        markShellDown("ADB session closed")
        _state.value = PairingState.IDLE
        if (!keepPairingInProgress || !isPairingRequired) notificationController.cancel()
    }

    @Synchronized
    fun resetPairing() {
        disconnect()
        prefs.edit().remove(KEY_PAIRED).remove(KEY_PAIRING_REQUIRED).remove(KEY_LAST_CONNECT_PORT).apply()
        forgetVerifiedPort()
        privateKeyFile().delete()
        publicKeyFile().delete()
        _pairingTrustState.value = PairingTrustState.UNPAIRED
        _failure.value = null
    }

    @Synchronized
    fun startRequiredPairing(input: PairingInput): Boolean {
        if (pairingInProgress()) return startDiscovery(input)
        if (isPairingRequired) disconnect()
        return startDiscovery(input)
    }
}
