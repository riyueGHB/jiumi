// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import com.ybblab.jiumi.adb.AirModePairingManager
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingInput
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.adb.AirPairingFailure
import com.ybblab.jiumi.adb.NetworkAdb
import com.ybblab.jiumi.access.AccessMethod
import kotlinx.coroutines.CancellationException
import androidx.core.graphics.drawable.toBitmap
import com.ybblab.jiumi.access.RootShell
import com.ybblab.jiumi.data.AppBackups
import com.ybblab.jiumi.data.AppSource
import com.ybblab.jiumi.data.ManageAction
import com.ybblab.jiumi.data.ManageRules
import com.ybblab.jiumi.data.Management
import com.ybblab.jiumi.data.MonitoringPreferences
import com.ybblab.jiumi.data.NetworkManagement
import com.ybblab.jiumi.data.PackageFacts
import com.ybblab.jiumi.data.PackageFactsSource
import com.ybblab.jiumi.data.Refusal
import com.ybblab.jiumi.data.Risk
import com.ybblab.jiumi.data.ShellPackage
import com.ybblab.jiumi.data.ShellCatalog
import com.ybblab.jiumi.data.AccessRules
import com.ybblab.jiumi.data.AppKey
import com.ybblab.jiumi.data.JiumiStore
import com.ybblab.jiumi.data.JiumiSync
import com.ybblab.jiumi.data.PackageOwners
import com.ybblab.jiumi.data.Report
import com.ybblab.jiumi.data.ReportBuilder
import com.ybblab.jiumi.data.AppDetail
import com.ybblab.jiumi.data.Coverage
import com.ybblab.jiumi.data.Activity
import com.ybblab.jiumi.data.LogExport
import com.ybblab.jiumi.data.NetworkUsage
import com.ybblab.jiumi.data.AppKinds
import com.ybblab.jiumi.data.AttentionRules
import com.ybblab.jiumi.data.AttentionAlerts
import com.ybblab.jiumi.data.NoticeSummary
import com.ybblab.jiumi.data.Freezes
import com.ybblab.jiumi.probe.PackageChangeFailure
import com.ybblab.jiumi.probe.ProbeClient
import com.ybblab.jiumi.dns.DomainVpnService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Starts the monitor through a short-lived ADB session and talks to it afterwards.
 * ADB is opened only for starting the monitor and closed as soon as it is running.
 */
class MonitorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pairing: AirModePairingManager,
    private val networkAdb: NetworkAdb,
    private val probe: ProbeClient,
    private val rules: AccessRules,
    private val store: JiumiStore,
    val access: com.ybblab.jiumi.access.Access,
    private val monitoring: MonitoringPreferences
) {
    enum class Busy { STARTING, STOPPING }
    /**
     * SESSION: the chosen monitoring session (the latest unless one was picked); the others reach back
     * from now, TODAY to local midnight. Half a year is the longest the report looks back.
     */
    /** A session, or a run of whole local days ending today: one, seven or thirty, as Screen Time counts them. */
    enum class Range { SESSION, TODAY, WEEK, MONTH }

    /** A monitoring session with how much of it the monitor covered. */
    data class SessionView(
        val session: JiumiStore.Session,
        val coverage: Coverage.Result,
        val measuredAt: Long,
        val latest: Boolean
    ) {
        val running: Boolean get() = session.end == null
    }
    enum class Problem { CONNECT, START, STOP, STOP_PARTIAL, SHIZUKU, NETWORK_UNAVAILABLE, NETWORK_AUTHORIZATION, NETWORK_IDENTITY }

    data class UiState(
        val pairingState: PairingState = PairingState.IDLE,
        val trust: PairingTrustState = PairingTrustState.UNPAIRED,
        val pairingFailure: AirPairingFailure? = null,
        val running: Boolean = false,
        val busy: Busy? = null,
        /** An open session's monitor is being looked for: it may still run and offer itself again. */
        val awaitingMonitor: Boolean = false,
        val problem: Problem? = null,
        val footprint: Footprint? = null,
        val range: Range = Range.SESSION,
        val ledger: Report? = null,
        val callTracing: Boolean = true,
        /** The session the report covers, or the latest one when the report covers all of them. */
        val session: SessionView? = null,
        val spoolLimitMb: Int = DEFAULT_SPOOL_MB,
        /** The time the report covers. */
        val window: Pair<Long, Long> = 0L to 0L,
        /** The stretches of [window] the monitor ran, over every session in it. */
        val spans: List<LongRange> = emptyList(),
        /** The open session slot by slot, whatever range is picked: the running monitor's card draws it. */
        val live: Activity? = null,
        /** How many apps the open session's records came from. */
        val liveApps: Int = 0,
        /** [ledger] slot by slot, for the overview's curve. */
        val activity: Activity? = null,
        /** The range [ledger] and [activity] were built for; [range] runs ahead of it while a rebuild is under way. */
        val shownRange: Range = Range.SESSION,
        /** Preinstalled apps may be removed for this user, not only disabled. */
        val allowRemovePreinstalled: Boolean = false,
        /** Expert mode: most core system components may be changed too, each after a warning. */
        val expertMode: Boolean = false,
        /** An app frozen from here that is found turned on again is frozen again. */
        val autoRefreeze: Boolean = false,
        /** The notification settings: a persistent notification while monitoring, its summary, attention alerts. */
        val notices: Notices = Notices(),
        /** How the monitor is started, and whether that way is ready (paired, root granted, Shizuku allowed). */
        val method: com.ybblab.jiumi.access.AccessMethod = com.ybblab.jiumi.access.AccessMethod.WIRELESS,
        val ready: Boolean = false,
        val shizuku: com.ybblab.jiumi.access.ShizukuState = com.ybblab.jiumi.access.ShizukuState(),
        val root: RootShell.State = RootShell.State.UNCHECKED
    ) {
        /** Freezing, removing and restoring apps run as root: the Root way, or Shizuku started with root, once ready. */
        val manageAsRoot: Boolean get() = ready && (method == com.ybblab.jiumi.access.AccessMethod.ROOT ||
            (method == com.ybblab.jiumi.access.AccessMethod.SHIZUKU && shizuku.root))

        /** The user started monitoring and has not stopped it, whether or not the monitor runs now. */
        val sessionOpen: Boolean get() = session?.latest == true && session.running
    }

    /**
     * The notification settings. [summary] and [alerts] keep the record in step in the background while
     * the screen is on, so they need the persistent notification, which keeps the app running.
     */
    data class Notices(val persistent: Boolean = true, val summary: Boolean = false, val alerts: Boolean = false) {
        /** Whether the record is pulled in the background for the notifications. */
        val live: Boolean get() = persistent && (summary || alerts)
    }

    private data class Local(
        val busy: Busy? = null,
        /** Resumes waiting for the monitor to offer itself; several triggers may arrive together. */
        val awaiting: Int = 0,
        val problem: Problem? = null,
        val footprint: Footprint? = null,
        val range: Range = Range.SESSION,
        val ledger: Report? = null,
        val callTracing: Boolean = true,
        val selected: Long? = null,
        val session: SessionView? = null,
        val spoolLimitMb: Int = DEFAULT_SPOOL_MB,
        val window: Pair<Long, Long> = 0L to 0L,
        val spans: List<LongRange> = emptyList(),
        val live: Activity? = null,
        val liveApps: Int = 0,
        val activity: Activity? = null,
        /** The range [ledger] and [activity] were built for; [range] runs ahead while a rebuild is under way. */
        val shownRange: Range = Range.SESSION,
        val allowRemovePreinstalled: Boolean = false,
        val expertMode: Boolean = false,
        val autoRefreeze: Boolean = false,
        val notices: Notices = Notices()
    )

    private val settings = context.getSharedPreferences("jiumi_settings", Context.MODE_PRIVATE)
    @Volatile private var inventoryByUid: Map<Int, List<String>> = emptyMap()
    private val owners = PackageOwners(context.packageManager, catalogPackages = { inventoryByUid[it].orEmpty() })
    private val sync = JiumiSync(store, rules)
    val monitoringExclusions = monitoring.exclusions
    internal val networkManagement = NetworkManagement(context) { probe.service.value }
    private val usage = NetworkUsage(context)
    private val appKinds = AppKinds(context.packageManager)

    /** The apps the system has bound as its location providers, as the monitor last read them; kept across restarts. */
    @Volatile private var locationProviders: Set<String> = readLocationProviders(
        runCatching { java.io.File(context.filesDir, LOCATION_PROVIDERS_FILE).readText() }.getOrNull()
    )
    @Volatile private var providersReadAt = 0L

    /** Reads the location providers again when the last read is old; a monitor without the method leaves them as they were. */
    private fun refreshLocationProviders(service: com.ybblab.jiumi.probe.IProbeService) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (providersReadAt != 0L && now - providersReadAt < PROVIDERS_MAX_AGE_MS) return
        providersReadAt = now
        val json = runCatching { service.locationProviders() }.getOrNull() ?: return
        locationProviders = readLocationProviders(json)
        runCatching { java.io.File(context.filesDir, LOCATION_PROVIDERS_FILE).writeText(json) }
    }
    private val attentionRules = AttentionRules.load(context)
    private val builder = ReportBuilder(
        store, rules, Process.myUid(), owners::ownerOf,
        traffic = { from, to ->
            val exclusions = monitoringExclusions.value
            usage.byUid(from, to) { uid -> !exclusions.hides(uid) && !exclusions.blocks(uid, null, from, to) }
        },
        kindOf = appKinds::kindOf,
        attentionRules = attentionRules,
        packagesOfUid = { uid -> owners.packagesOf(uid).filterNot { monitoringExclusions.value.hides(uid, it) } },
        persistentOf = appKinds::persistent,
        locationProviderOf = { pkg -> pkg != null && pkg in locationProviders }
    )
    private val syncLock = Mutex()
    private var configuredService: com.ybblab.jiumi.probe.IProbeService? = null
    private var sentConfig: String? = null

    private val local = MutableStateFlow(Local(
        // Call tracing is always on: most kinds of data (device IDs, clipboard, the app list) are seen only by it.
        callTracing = true,
        spoolLimitMb = settings.getInt(KEY_SPOOL_MB, DEFAULT_SPOOL_MB),
        allowRemovePreinstalled = settings.getBoolean(KEY_ALLOW_REMOVE, false),
        expertMode = settings.getBoolean(KEY_EXPERT, false),
        autoRefreeze = settings.getBoolean(KEY_AUTO_REFREEZE, false),
        notices = Notices(settings.getBoolean(KEY_NOTICE_PERSISTENT, true), settings.getBoolean(KEY_NOTICE_SUMMARY, false),
            settings.getBoolean(KEY_NOTICE_ALERTS, false))
    ))

    private val packageFacts = PackageFactsSource(context)
    private val manageRules = ManageRules.load(context)
    private val backups = AppBackups(context)

    /**
     * One app as management sees it: its facts (null once deleted), why it is left alone if it is,
     * what may be done, and its backup when it was deleted from here.
     */
    data class Managed(val pkg: String, val facts: PackageFacts?, val refusal: Refusal?, val actions: List<ManageAction>,
                       val backup: AppBackups.Backup? = null, val risk: Risk? = null,
                       /** A preinstalled app that could be removed once the setting allows it. */
                       val removable: Boolean = false,
                       /** A core component left alone now that expert mode would let through. */
                       val expertUnlocks: Boolean = false,
                       /** Frozen from here, then found turned on again at this time, and not dealt with since. */
                       val lapsedAt: Long? = null)

    /** Where an app stands in the Apps tab. */
    enum class AppState { ACTIVE, DISABLED, SUSPENDED, DELETED, HISTORICAL }

    /** An app as the Apps tab lists it; [backup] for an app deleted from here, which is listed by it. */
    data class ListedApp(val pkg: String, val uid: Int, val state: AppState?,
                         val backup: AppBackups.Backup? = null,
                         /** Where it came from; an app deleted from here was one the user added. */
                         val source: AppSource = AppSource.SIDELOADED,
                         /** Part of the phone rather than an app one opens: no launcher icon, or the platform itself. */
                         val component: Boolean = false,
                         /** Frozen from here, then found turned on again (see [Managed.lapsedAt]). */
                         val lapsed: Boolean = false,
                         val module: Boolean = false)

    suspend fun managed(pkg: String): Managed? = withContext(Dispatchers.IO) { managedNow(pkg) }

    private fun managedNow(pkg: String): Managed? {
        val backup = backups.get(pkg)
        val facts = packageFacts.facts(pkg, keptShellPackages())
        if (facts == null) return backup?.let { Managed(pkg, null, null, listOf(ManageAction.REINSTALL), it) }
        val expert = local.value.expertMode
        val refusal = Management.refusal(facts, manageRules, context.packageName, expert)
        val actions = Management.actions(facts, local.value.allowRemovePreinstalled)
            .takeUnless { refusal?.reason == Refusal.Reason.SELF }.orEmpty()
            .filter { refusal == null || Management.isUndo(it) }
        val risk = if (refusal == null) Management.risk(facts, manageRules) else null
        val removable = refusal == null && facts.hasCode == true && facts.uid >= 0 && Management.isPackageName(facts.pkg) &&
            facts.preinstalled && facts.installed && !local.value.allowRemovePreinstalled
        val unlocks = facts.hasCode == true && !expert && Management.expertUnlocks(facts, manageRules, context.packageName)
        val lapsedAt = if (facts.installed && !facts.disabled && !facts.suspendedByShell)
            Freezes.lapsedSince(store.userActions(0, Long.MAX_VALUE, pkg))[pkg] else null
        return Managed(pkg, facts, refusal, actions, backup, risk, removable, unlocks, lapsedAt)
    }

    /** The current user's packages and backups, along with the device's system modules. */
    suspend fun listedApps(fresh: Boolean = false): List<ListedApp> = withContext(Dispatchers.IO) {
        val saved = backups.list().associateBy { it.pkg }
        val catalog = if (fresh) shellCatalog() else keptShellCatalog()
        if (fresh) scope.launch(Dispatchers.IO) { checkFreezes() }
        val lapsed = Freezes.lapsedSince(store.userActions(0, Long.MAX_VALUE)).keys
        val present = packageFacts.all(catalog.packages).mapNotNull { listedApp(it, lapsed) }
        val known = present.map { it.pkg }.toSet()
        present + saved.values.filter { it.pkg !in known }.map { ListedApp(it.pkg, it.uid, AppState.DELETED, it) } +
            catalog.modules.orEmpty().map { ListedApp(it.pkg, -1, null, module = true) }
    }

    private fun listedApp(f: PackageFacts, lapsed: Set<String> = emptySet()): ListedApp? {
        val state = when {
            !f.installed -> if (f.preinstalled) AppState.DELETED else return null
            f.suspendedByShell -> AppState.SUSPENDED
            f.disabled -> AppState.DISABLED
            else -> AppState.ACTIVE
        }
        val component = f.hasCode != true || Management.isCore(f, manageRules) || (f.preinstalled && !f.launchable)
        return ListedApp(f.pkg, f.uid, state, source = Management.source(f), component = component,
            lapsed = state == AppState.ACTIVE && f.pkg in lapsed)
    }

    suspend fun appsForMonitoring(fresh: Boolean = false): List<ListedApp> = withContext(Dispatchers.IO) {
        val packages = if (fresh) shellPackages() else keptShellPackages()
        packageFacts.all(packages).asSequence()
            .filter { it.installed && it.hasCode == true && it.uid >= 0 && it.pkg != context.packageName }
            .mapNotNull { listedApp(it) }.toList()
    }

    suspend fun setMonitoringExcluded(key: AppKey, excluded: Boolean): Boolean = withContext(Dispatchers.IO) {
        val pkg = key.pkg ?: return@withContext false
        if (!Management.isCatalogPackage(pkg) || key.uid < 0 || pkg == context.packageName) return@withContext false
        syncLock.withLock {
            val uid = installedUid(pkg) ?: key.uid
            saveMonitoringExclusions(monitoringExclusions.value.set(pkg, uid, excluded, System.currentTimeMillis()))
        }
    }

    fun refreshMonitoringPackages() {
        if (monitoringExclusions.value.active.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            syncLock.withLock {
                val current = monitoringExclusions.value
                val now = System.currentTimeMillis()
                val updated = current.active.values.fold(current) { rules, app ->
                    installedUid(app.pkg)?.let { rules.set(app.pkg, it, excluded = true, now) } ?: rules
                }
                saveMonitoringExclusions(updated)
            }
        }
    }

    private fun installedUid(pkg: String): Int? = runCatching { context.packageManager.getPackageUid(pkg, 0) }.getOrNull()

    /** Called under syncLock, so an import cannot commit against an older policy. */
    private fun saveMonitoringExclusions(next: com.ybblab.jiumi.probe.MonitoringExclusions): Boolean {
        if (next === monitoringExclusions.value) return true
        if (!monitoring.save(next)) return false
        builder.clearGroupCache()
        local.update { it.copy(ledger = null) }
        MonitorNotice.cancelAlerts(context)
        probe.service.value?.let(::configure)
        DomainVpnService.refresh(context)
        rebuild()
        shownSince?.let { since ->
            val summary = noticeSummary(since)
            MonitorNotificationService.update(context, since) { it.copy(summary = summary) }
        }
        return true
    }

    suspend fun historicalApps(query: String): List<ListedApp> = withContext(Dispatchers.IO) {
        store.recordedPackages(query, Process.myUid() / PER_USER_RANGE).map {
            ListedApp(requireNotNull(it.pkg), it.uid, AppState.HISTORICAL)
        }
    }

    suspend fun systemModule(pkg: String) = withContext(Dispatchers.IO) {
        val catalog = keptShellCatalog()
        catalog.modules?.firstOrNull { it.pkg == pkg }?.let { module ->
            val apps = catalog.packages.filter {
                val path = it.codePath.orEmpty()
                it.installed && path.startsWith("/apex/") && path.removePrefix("/apex/").substringBefore('/').substringBefore('@') == pkg
            }.map { AppKey(it.uid, it.pkg) }.sortedBy { it.pkg }
            module to apps
        }
    }

    /**
     * The package list as the monitor sees it: read fresh while it runs and kept, so the Apps tab
     * stays complete on phones that hide apps from one another after the monitor stops.
     */
    private val shellCatalogFile = AtomicFile(java.io.File(context.filesDir, SHELL_PACKAGES_FILE))
    private val shellCatalogRefresh = Any()

    private fun shellPackages(): List<ShellPackage> = shellCatalog().packages

    private fun shellCatalog(): ShellCatalog = synchronized(shellCatalogRefresh) {
        val cached = shellCatalogJson()
        probe.service.value?.let { service ->
            runCatching {
                val json = ShellCatalog.retainCompleteSections(JSONObject(service.listPackages()), cached)
                val parsed = ShellCatalog.parse(json)
                if (parsed.packages.isNotEmpty() || parsed.modules.orEmpty().isNotEmpty()) {
                    synchronized(shellCatalogFile) {
                        val out = shellCatalogFile.startWrite()
                        try {
                            out.write(json.toString().encodeToByteArray())
                            shellCatalogFile.finishWrite(out)
                        } catch (error: Exception) {
                            shellCatalogFile.failWrite(out)
                            throw error
                        }
                    }
                    if (json.optBoolean("packagesComplete")) indexPackages(parsed)
                    shellReadAt = android.os.SystemClock.elapsedRealtime()
                    return@synchronized parsed
                }
            }
        }
        cached?.let { runCatching { ShellCatalog.parse(it) }.getOrNull() } ?: ShellCatalog()
    }

    private fun indexPackages(catalog: ShellCatalog) {
        inventoryByUid = catalog.packages.filter { it.installed && it.uid >= 0 }.groupBy({ it.uid }, { it.pkg })
    }

    /** When the monitor last listed the packages; cleared by each change, so the next list asks again. */
    @Volatile private var shellReadAt = 0L

    /**
     * Whether the Apps tab should ask the running monitor again. Its list costs a few shell commands,
     * so it is read once a minute at most, and after every change made here.
     */
    fun shellListStale(): Boolean = probe.service.value != null &&
        (shellReadAt == 0L || android.os.SystemClock.elapsedRealtime() - shellReadAt > SHELL_LIST_MAX_AGE_MS)

    private fun shellCatalogJson(): JSONObject? = synchronized(shellCatalogFile) {
        runCatching { JSONObject(shellCatalogFile.readFully().decodeToString()) }.getOrNull()
    }

    private fun keptShellCatalog(): ShellCatalog =
        runCatching { shellCatalogJson()?.let(ShellCatalog::parse) }.getOrNull() ?: ShellCatalog()

    private fun keptShellPackages(): List<ShellPackage> = keptShellCatalog().packages

    fun backupIcon(pkg: String): android.graphics.Bitmap? = backups.icon(pkg)

    /** How often [pkg]'s alarms woke the phone since it started; null while the monitor is not running. */
    suspend fun wakeups(pkg: String): com.ybblab.jiumi.data.Wakeups? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val ownUser = Process.myUid() / PER_USER_RANGE * PER_USER_RANGE
        if (monitoringExclusions.value.blocks(ownUser, pkg, now - SystemClock.elapsedRealtime(), now)) return@withContext null
        val service = probe.service.value ?: return@withContext null
        runCatching { service.alarmWakeups(pkg) }.getOrNull()
            ?.let { com.ybblab.jiumi.data.Wakeups.parse(it, pkg, System.currentTimeMillis()) }
    }

    /** How a management action ended. */
    enum class ManageStatus { DONE, DISABLED, REMOVED, NO_MONITOR, FAILED, PERMISSION_DENIED, NOT_APPLIED, UNVERIFIED }

    /** [asRoot]: the change was tried as root, not by the monitor as the shell. */
    data class ManageResult(val status: ManageStatus, val running: Boolean? = null, val asRoot: Boolean = false)

    private val manageLock = Mutex()

    /**
     * Carries out [action] on [pkg] through the monitor, which alone may change packages, and
     * records it. A monitor that is not running is started first, continuing the open session as
     * resuming would. An app deleted from here is first backed up, so it can be installed again.
     */
    suspend fun manage(pkg: String, action: ManageAction): ManageResult = withContext(Dispatchers.IO) {
        manageLock.withLock {
            val failed = ManageResult(ManageStatus.FAILED)
            val m = managedNow(pkg) ?: return@withLock failed
            if (action !in m.actions) return@withLock failed
            val service = ensureMonitor() ?: return@withLock ManageResult(ManageStatus.NO_MONITOR)
            if (action == ManageAction.UNINSTALL) {
                val pm = context.packageManager
                val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@withLock failed
                val icon = runCatching { pm.getApplicationIcon(app).toBitmap(192, 192) }.getOrNull()
                backups.create(app, app.loadLabel(pm).toString(), icon) ?: return@withLock failed
            }
            val response = changePackage(service, pkg, action)
            val ok = response?.optBoolean("ok") == true
            val stillInstalled = if (action == ManageAction.UNINSTALL && !ok) runCatching {
                JSONObject(service.packageState(pkg, false)).opt("installed") as? Boolean
            }.getOrNull() else null
            if (ok && action == ManageAction.REINSTALL) backups.delete(pkg)
            shellReadAt = 0L
            val status = when (response?.optString("failure")) {
                PackageChangeFailure.PERMISSION_DENIED.name -> ManageStatus.PERMISSION_DENIED
                PackageChangeFailure.NOT_APPLIED.name -> ManageStatus.NOT_APPLIED
                PackageChangeFailure.UNVERIFIED.name -> ManageStatus.UNVERIFIED
                else -> if (action == ManageAction.UNINSTALL && !ok && stillInstalled != true) ManageStatus.UNVERIFIED else ManageStatus.FAILED
            }
            if (ok || status == ManageStatus.UNVERIFIED) runCatching { shellPackages() }
            if (ok) store.insertUserAction(JiumiStore.UserAction(System.currentTimeMillis(), m.facts?.uid ?: m.backup?.uid ?: -1, pkg, action.id))
            refresh()
            if (!ok) return@withLock ManageResult(status, asRoot = response?.optBoolean("root") == true)
            val running = response?.opt("running") as? Boolean
            when (action) {
                ManageAction.DISABLE -> ManageResult(ManageStatus.DISABLED, running)
                ManageAction.REMOVE -> ManageResult(ManageStatus.REMOVED, running)
                else -> ManageResult(ManageStatus.DONE)
            }
        }
    }

    /**
     * Freezes, unfreezes, removes for this user or restores [pkg] as root when the chosen way holds
     * root, which the shell may not do for every app; otherwise, and for uninstalling and reinstalling
     * apps the user added (which the shell may always do), the monitor makes the change as the shell.
     * The answer has the monitor's shape: {"ok","output","failure"?,"running"?}.
     */
    private fun changePackage(service: com.ybblab.jiumi.probe.IProbeService, pkg: String, action: ManageAction): JSONObject? {
        val result = changeAsRoot(service, pkg, action)
            ?: runCatching { JSONObject(service.managePackage(action.id, pkg)) }.getOrNull()
        if (result != null) return result
        // A disconnected probe may have already sent the change. Do not start another attempt.
        return if (action == ManageAction.DISABLE || action == ManageAction.ENABLE) {
            JSONObject().put("ok", false).put("failure", PackageChangeFailure.UNVERIFIED.name)
        } else null
    }

    /**
     * pm disable as root sets the system's own DISABLED state, which the shell cannot set and some
     * phones refuse the shell's DISABLED_USER for; some phones refuse the shell removing their
     * preinstalled apps too. The same commands as the shell's run as root, for this user only, and the
     * monitor then reads the result back. Freeze and enable keep verifying after an uncertain Root response.
     */
    private fun changeAsRoot(service: com.ybblab.jiumi.probe.IProbeService, pkg: String, action: ManageAction): org.json.JSONObject? {
        if (!Management.isPackageName(pkg)) return null
        val user = Process.myUid() / PER_USER_RANGE
        val command = when (action) {
            ManageAction.DISABLE -> "pm disable --user $user $pkg"
            ManageAction.ENABLE -> "pm enable --user $user $pkg"
            ManageAction.REMOVE -> "pm uninstall -k --user $user $pkg"
            ManageAction.RESTORE -> "cmd package install-existing --user $user $pkg"
            else -> return null
        }
        val checkRunning = action == ManageAction.DISABLE || action == ManageAction.REMOVE
        val method = access.method.value
        val rootSelected = method == AccessMethod.ROOT || (method == AccessMethod.SHIZUKU && access.shizuku.refresh().root)
        if (!rootSelected) return null
        val output = access.asRoot(command, ROOT_COMMAND_MS)
        if (output == null && action != ManageAction.DISABLE && action != ManageAction.ENABLE) return null
        val result = JSONObject().put("output", output.orEmpty().take(MAX_COMMAND_OUTPUT)).put("root", true)
        output?.let(PackageChangeFailure::fromCommandOutput)?.let { return result.put("ok", false).put("failure", it.name) }
        val state = runCatching { org.json.JSONObject(service.packageState(pkg, checkRunning)) }.getOrNull()
            ?.takeIf { it.has("enabled") }
            ?: return result.put("ok", false).put("failure", PackageChangeFailure.UNVERIFIED.name)
        val installed = state.optBoolean("installed")
        val enabled = state.optInt("enabled", -1)
        val applied = when (action) {
            ManageAction.DISABLE -> installed && Management.isDisabled(enabled)
            ManageAction.ENABLE -> installed && enabled == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ManageAction.REMOVE -> !installed
            else -> installed
        }
        if (!applied) return result.put("ok", false).put("failure",
            if (output == null) PackageChangeFailure.UNVERIFIED.name else PackageChangeFailure.NOT_APPLIED.name)
        return result.put("ok", true).apply { (state.opt("running") as? Boolean)?.let { put("running", it) } }
    }

    @Volatile private var freezesCheckedAt = 0L

    /**
     * Looks for apps frozen from here that the monitor now reads turned on again, records each once,
     * and freezes it again when [UiState.autoRefreeze] is on. Runs at most every few minutes and only
     * while the monitor runs, since only it reads every app's state.
     */
    private suspend fun checkFreezes() {
        val service = probe.service.value ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (freezesCheckedAt != 0L && now - freezesCheckedAt < FREEZE_CHECK_MS) return
        freezesCheckedAt = now
        val found = manageLock.withLock {
            val records = store.userActions(0, Long.MAX_VALUE)
            if (Freezes.standings(records).values.none { it.first == Freezes.Standing.FROZEN }) return@withLock 0
            // Read after the record, under the lock, so a change made here meanwhile is never taken for a lapse.
            val lapsed = Freezes.lapsed(records, shellPackages())
            lapsed.forEach { p ->
                store.insertPackageEvent(System.currentTimeMillis(), p.uid, p.pkg, JiumiStore.PackageEvent.LAPSED)
                if (!local.value.autoRefreeze || managedNow(p.pkg)?.actions?.contains(ManageAction.DISABLE) != true) return@forEach
                if (changePackage(service, p.pkg, ManageAction.DISABLE)?.optBoolean("ok") == true) {
                    store.insertPackageEvent(System.currentTimeMillis(), p.uid, p.pkg, JiumiStore.PackageEvent.REFROZEN)
                }
            }
            if (lapsed.isNotEmpty()) shellReadAt = 0L
            lapsed.size
        }
        if (found > 0) {
            android.util.Log.i(TAG, "freezes lapsed: $found")
            refresh()
        }
    }

    suspend fun appAccess(key: com.ybblab.jiumi.data.AppKey, action: String, permission: String = ""):
        org.json.JSONObject? = withContext(Dispatchers.IO) {
        val pkg = key.pkg ?: return@withContext null
        if (action == "networkDeny" || action == "networkAllow") {
            return@withContext networkManagement.change(key, blocked = action == "networkDeny", expert = local.value.expertMode)
        }
        val service = probe.service.value
        if (service == null) {
            val offline = JSONObject().put("lock", "monitor")
            if (action != "permissions") return@withContext offline
            val info = runCatching { context.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS) }.getOrNull()
                ?.takeIf { it.applicationInfo?.uid == key.uid } ?: return@withContext offline
            val flags = info.requestedPermissionsFlags ?: IntArray(0)
            val rows = info.requestedPermissions.orEmpty().mapIndexed { index, name ->
                JSONObject().put("name", name).put("editable", false)
                    .put("allowed", flags.getOrNull(index)?.let { it and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 })
            }
            return@withContext offline.put("rows", org.json.JSONArray(rows))
        }
        runCatching { org.json.JSONObject(service.appAccess(pkg, key.uid, action, permission, local.value.expertMode)) }.getOrNull()
    }

    /** The running monitor, started (or resumed) first when it is not; null when it would not start. */
    private suspend fun ensureMonitor(): com.ybblab.jiumi.probe.IProbeService? {
        probe.service.value?.let { return it }
        startMonitor()
        withTimeoutOrNull(CONNECT_TIMEOUT_MS + START_TIMEOUT_MS + 5_000) {
            combine(probe.service, local) { service, l -> service != null || (l.busy == null && l.problem != null) }.first { it }
        }
        return probe.service.value
    }

    /** Throws away the backup of a deleted app; it can no longer be installed again from here. */
    suspend fun discardBackup(pkg: String) = withContext(Dispatchers.IO) {
        backups.delete(pkg)
        refresh()
    }

    /** ADB persistence: the monitor can be started without Wi-Fi, through a local port, until a restart. */
    val adbPersistence: StateFlow<Boolean> get() = pairing.persistenceEnabled

    /** A local port is open for this boot: the monitor can be started without Wi-Fi. */
    val hasLocalAdbPort: Boolean get() = pairing.hasLocalAdbPort

    /** A classic listener opened by an older build still has a saved port for this boot. */
    val localPortClosePending: Boolean get() = pairing.localListenerClosePending

    /** The local port needs USB debugging on as well; null when the phone will not say. */
    fun usbDebuggingEnabled(): Boolean? = pairing.isUsbDebuggingEnabled()

    /** Allows reusing verified loopback ADB. Closing a legacy listener waits for the monitor to stop. */
    fun setAdbPersistence(on: Boolean) {
        if (on) pairing.setPersistenceEnabled(true)
        else scope.launch(Dispatchers.IO) { pairing.disablePersistence() }
    }

    fun setExpertMode(on: Boolean) {
        settings.edit().putBoolean(KEY_EXPERT, on).apply()
        local.update { it.copy(expertMode = on) }
    }

    fun setAutoRefreeze(on: Boolean) {
        settings.edit().putBoolean(KEY_AUTO_REFREEZE, on).apply()
        local.update { it.copy(autoRefreeze = on) }
        if (on) {
            freezesCheckedAt = 0L
            scope.launch(Dispatchers.IO) { checkFreezes() }
        }
    }

    fun setAllowRemovePreinstalled(on: Boolean) {
        settings.edit().putBoolean(KEY_ALLOW_REMOVE, on).apply()
        local.update { it.copy(allowRemovePreinstalled = on) }
    }

    private data class Env(val method: com.ybblab.jiumi.access.AccessMethod, val trust: PairingTrustState,
                           val root: RootShell.State, val shizuku: com.ybblab.jiumi.access.ShizukuState)

    private val env = combine(access.method, pairing.pairingTrustState, access.root.state, access.shizuku.state, ::Env)
    private var networkRetry: Job? = null
    data class NetworkCheck(val pending: Boolean = false, val result: NetworkAdb.Result? = null)

    private var networkCheckJob: Job? = null
    private val _networkCheck = MutableStateFlow(NetworkCheck())
    val networkCheck = _networkCheck.asStateFlow()
    val networkAdbPort: StateFlow<Int?> get() = networkAdb.savedPort
    private val connectionEnv = combine(env, networkAdb.savedPort, networkCheck, ::Triple)

    fun ensureNetworkAdbChecked() {
        if (networkCheck.value.result == null) checkNetworkAdb()
    }

    /** Selecting the method checks the local endpoint without opening an Android authorization prompt. */
    fun checkNetworkAdb(port: Int? = null, authorize: Boolean = false) {
        if (access.method.value != AccessMethod.NETWORK || networkCheck.value.pending) return
        _networkCheck.value = NetworkCheck(pending = true)
        networkCheckJob = scope.launch(Dispatchers.Main.immediate) {
            val result = try {
                networkAdb.use(port, allowAuthorization = authorize)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(TAG, "Network ADB check failed", error)
                NetworkAdb.Result.Unavailable
            }
            if (result is NetworkAdb.Result.Ready) {
                networkAdb.rememberPort(result.port)
                local.update { if (it.problem.isNetworkProblem()) it.copy(problem = null) else it }
                resumeInterrupted("network ADB configured")
            }
            _networkCheck.value = NetworkCheck(result = result)
        }
    }

    /** Retry a briefly unavailable local endpoint while this process remains alive. */
    @Synchronized
    private fun scheduleNetworkRetry() {
        if (networkRetry?.isActive == true || store.sessions().none { it.end == null }) return
        networkRetry = scope.launch(Dispatchers.IO) {
            for (pause in listOf(5_000L, 15_000L, 30_000L)) {
                delay(pause)
                if (probe.service.value != null || access.method.value != AccessMethod.NETWORK ||
                    local.value.problem != Problem.NETWORK_UNAVAILABLE) break
                resumeInterrupted("network ADB retry").join()
            }
        }
    }

    val state: StateFlow<UiState> = combine(
        pairing.state, pairing.failure, probe.service, local, connectionEnv
    ) { pairingState, failure, service, l, (e, networkPort, check) ->
        val ready = when (e.method) {
            com.ybblab.jiumi.access.AccessMethod.WIRELESS -> e.trust == PairingTrustState.PAIRED
            AccessMethod.NETWORK -> !check.pending &&
                (check.result is NetworkAdb.Result.Ready || (check.result == null && networkPort != null))
            // Until this run of the app has asked again, a grant from before counts, as it does for
            // starting unattended: otherwise every cold start shows "authorize" for a moment.
            com.ybblab.jiumi.access.AccessMethod.ROOT -> e.root == RootShell.State.READY ||
                (e.root == RootShell.State.UNCHECKED && access.root.wasReady)
            com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> e.shizuku.ready
        }
        val problem = l.problem?.takeUnless { ready && it == Problem.SHIZUKU }
        UiState(pairingState, e.trust, failure, service != null, l.busy, l.awaiting > 0 && service == null, problem, l.footprint, l.range,
            l.ledger, l.callTracing, l.session, l.spoolLimitMb, l.window, l.spans, l.live, l.liveApps, l.activity, l.shownRange, l.allowRemovePreinstalled, l.expertMode,
            l.autoRefreeze, l.notices, e.method, ready, e.shizuku, e.root)
    }.stateIn(scope, SharingStarted.Eagerly, UiState())

    init {
        // A fresh pairing connects automatically; continue straight into starting the monitor.
        scope.launch {
            pairing.state.collect { s ->
                if (s != PairingState.CONNECTED || local.value.busy != null) return@collect
                if (probe.service.value == null && access.method.value == AccessMethod.WIRELESS) {
                    startMonitor()
                } else {
                    pairing.disconnect(keepPairingInProgress = true)
                }
            }
        }
        scope.launch {
            // Startup recovery already covers the current transport state.
            pairing.wirelessTransportReady.drop(1).collectLatest { ready ->
                if (!ready || access.method.value != AccessMethod.WIRELESS || !isPaired ||
                    probe.service.value != null) return@collectLatest
                // A previous attempt may still be settling after the network was lost.
                local.first { it.busy == null }
                withContext(Dispatchers.IO) {
                    resumeInterruptedNow("wireless debugging available", requireWirelessTransport = true)
                }
            }
        }
        // Restarting adbd ends every process it started, the monitor among them; ADB persistence waits.
        pairing.adbdRestartAllowed = { probe.service.value == null }
        scope.launch {
            var hadMonitor = false
            probe.service.collect { service ->
                if (service == null) {
                    local.update { l -> l.copy(footprint = null) }
                    if (access.method.value == AccessMethod.NETWORK) networkInterruptionNotified = false
                    showIndicator()
                    // Lost without being stopped from here: try to pick the session up again.
                    if (hadMonitor && local.value.busy != Busy.STOPPING) resumeInterrupted("monitor lost")
                } else {
                    // A start that gave up waiting is answered all the same; it did not fail.
                    local.update { l -> if (l.problem == Problem.START || l.problem.isNetworkProblem()) l.copy(problem = null) else l }
                    configure(service)
                    scope.launch { networkManagement.onConnected(service) }
                    scope.launch(Dispatchers.IO) {
                        shellCatalog()
                        refresh()
                    }
                    refreshFootprint()
                    refresh()
                }
                hadMonitor = service != null
            }
        }
        scope.launch(Dispatchers.IO) {
            synchronized(shellCatalogRefresh) {
                val cached = shellCatalogJson()
                if (cached?.optBoolean("packagesComplete", true) == true) {
                    runCatching { ShellCatalog.parse(cached) }.getOrNull()?.let(::indexPackages)
                }
            }
            refresh()
        }
        // A session left open by a restart, an update or a monitor that was ended is picked up again
        // whenever the app starts; makers that withhold the update or boot broadcast are covered too.
        resumeInterrupted("app start")
    }

    /** Paired now, read straight from pairing rather than from [state], which may not have caught up. */
    val isPaired: Boolean get() = pairing.pairingTrustState.value == PairingTrustState.PAIRED

    fun startPairing(input: PairingInput): Boolean {
        local.update { it.copy(problem = null) }
        return pairing.startRequiredPairing(input)
    }

    fun cancelPairing() = pairing.stopDiscovery()

    fun preparePairingSettings() = pairing.preparePairingSettings()

    fun cancelInAppPairing() = pairing.stopInAppPairing()

    fun submitInAppPairingCode(code: String): Boolean = pairing.submitInAppPairingCode(code)

    /**
     * When a session is still open but no monitor is connected (after an app update, a restart of the
     * phone, or a monitor that was ended), starts it again the way it was started, so an interruption
     * does not silently end monitoring. A monitor that only lost touch with the app offers itself
     * again within seconds, so this waits a moment first. Only a channel that needs nobody is used:
     * Wireless debugging is not turned on unasked, and a root or Shizuku prompt is not opened.
     * [launched] is given by a receiver holding its broadcast open, and runs once that may be let go.
     */
    fun resumeInterrupted(trigger: String, launched: (() -> Unit)? = null): Job = scope.launch(Dispatchers.IO) {
        resumeInterruptedNow(trigger, launched)
    }

    private suspend fun resumeInterruptedNow(
        trigger: String,
        launched: (() -> Unit)? = null,
        requireWirelessTransport: Boolean = false
    ) {
        // Shown as under way from the first moment, not only once a start goes out.
        var waiting = false
        val stopWaiting = {
            if (waiting) {
                waiting = false
                local.update { it.copy(awaiting = it.awaiting - 1) }
            }
        }
        try {
            if (probe.service.value != null || !store.hasOpenSession()) return
            waiting = local.value.busy == null
            if (waiting) local.update { it.copy(awaiting = it.awaiting + 1) }
            // A monitor restarted by its supervisor, or still offering itself, gets there first. Not while
            // a broadcast is held ([launched]): until it is let go no offer reaches this process, so the
            // start goes out at once and lets it go.
            if (launched == null) withTimeoutOrNull(RESUME_DELAY_MS) { probe.service.first { it != null } }
            val skip = when {
                probe.service.value != null -> "monitor running"
                local.value.busy != null -> "busy ${local.value.busy}"
                !store.hasOpenSession() -> "no open session"
                requireWirelessTransport && (!pairing.wirelessTransportReady.value ||
                    access.method.value != AccessMethod.WIRELESS) -> "wireless debugging unavailable"
                !canStartUnattended() -> "${access.method.value} not ready"
                else -> null
            }
            android.util.Log.i(TAG, "resume ($trigger): ${skip ?: "starting"}")
            if (skip != null) return
            beginMonitorStart(
                restoreWirelessDebugging = false,
                launched = launched ?: {},
                resumeOpenSession = true,
                requireWirelessTransport = requireWirelessTransport
            )
            // The start shows itself from here on.
            stopWaiting()
            // Held until the start is settled, so the process is not let go halfway.
            withTimeoutOrNull(CONNECT_TIMEOUT_MS + START_TIMEOUT_MS) { local.first { it.busy == null } }
        } finally {
            stopWaiting()
            launched?.invoke()
        }
    }

    /**
     * Starts the monitor the chosen way. [launched] runs once the start command has gone out, before
     * waiting for the monitor to answer (or when it never goes out): a broadcast receiver holding its
     * broadcast open must let it go then, since the monitor answers with a broadcast of its own, which
     * the system delivers to this process only after the one held.
     */
    fun startMonitor(restoreWirelessDebugging: Boolean = true, launched: () -> Unit = {}) =
        beginMonitorStart(restoreWirelessDebugging, launched)

    private fun beginMonitorStart(
        restoreWirelessDebugging: Boolean,
        launched: () -> Unit,
        resumeOpenSession: Boolean = false,
        requireWirelessTransport: Boolean = false
    ) {
        // Claimed in one step: a resume and a tap arriving together must not both start one.
        var claimed = false
        local.update { l -> (l.busy == null).also { claimed = it }.let { if (it) l.copy(busy = Busy.STARTING, problem = null) else l } }
        if (!claimed) return launched()
        scope.launch(Dispatchers.IO) {
            var problem: Problem? = null
            try {
                val method = access.method.value
                // Recheck after claiming Busy.STARTING so a finished session cannot be reopened.
                if (resumeOpenSession && (!store.hasOpenSession() || !canStartUnattended(method) ||
                    (requireWirelessTransport && (method != AccessMethod.WIRELESS ||
                        !pairing.wirelessTransportReady.value)))) return@launch
                if (probe.service.value != null) {
                    openSessionIfNone()
                    return@launch
                }
                problem = when (method) {
                    com.ybblab.jiumi.access.AccessMethod.WIRELESS -> startOverWireless(restoreWirelessDebugging, launched)
                    AccessMethod.NETWORK -> startOverNetwork(launched)
                    com.ybblab.jiumi.access.AccessMethod.ROOT -> startOverRoot(launched)
                    com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> startOverShizuku(launched)
                }
                if (problem == null) openSessionIfNone()
            } finally {
                launched()
                // A monitor that answered after the wait ran out still counts as started.
                val settled = problem.takeUnless { probe.service.value != null }
                local.update { it.copy(busy = null, problem = settled) }
                if (access.method.value == AccessMethod.NETWORK) {
                    showIndicator()
                    if (settled == Problem.NETWORK_UNAVAILABLE) scheduleNetworkRetry()
                }
            }
        }
    }

    /** Over Wireless debugging (or its local channel): connect, start, and let ADB go again. */
    private suspend fun startOverWireless(restoreWirelessDebugging: Boolean, launched: () -> Unit): Problem? {
        try {
            if (!pairing.isShellReady) {
                // Not set off (Wi-Fi gone, pairing under way): nothing will connect, so do not wait for it.
                if (!pairing.reconnect(restoreWirelessDebugging = restoreWirelessDebugging)) return Problem.CONNECT
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    combine(pairing.state, pairing.failure) { state, failure ->
                        state == PairingState.CONNECTED || failure != null
                    }.first { it }
                }
                if (!pairing.isShellReady) return Problem.CONNECT
            }
            if (pairing.shell(ownPermissionsCommand()).startsWith("[ADB_") ||
                pairing.shell(probe.startCommand(probeConfiguration())).startsWith("[ADB_")) return Problem.CONNECT
            launched()
            return waitForProbe()
        } finally {
            // The monitor does not depend on ADB; never keep the session open.
            pairing.disconnect(keepPairingInProgress = true)
        }
    }

    private suspend fun startOverNetwork(launched: () -> Unit): Problem? {
        val previousCheck = networkCheck.value
        val result = try {
            networkAdb.use { shell ->
                shell.shell(ownPermissionsCommand())
                shell.shell(probe.startCommand(probeConfiguration()))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w(TAG, "Network ADB could not start the monitor", error)
            return Problem.START
        }
        if (!previousCheck.pending && access.method.value == AccessMethod.NETWORK) {
            _networkCheck.compareAndSet(previousCheck, NetworkCheck(result = result))
        }
        return when (result) {
            is NetworkAdb.Result.Ready -> {
                networkAdb.rememberPort(result.port)
                launched()
                waitForProbe()
            }
            is NetworkAdb.Result.NeedsAuthorization -> Problem.NETWORK_AUTHORIZATION
            NetworkAdb.Result.WrongIdentity -> Problem.NETWORK_IDENTITY
            NetworkAdb.Result.Unavailable -> Problem.NETWORK_UNAVAILABLE
        }
    }

    private suspend fun startOverRoot(launched: () -> Unit): Problem? {
        val command = ownPermissionsCommand() + "\n" + probe.startCommand(probeConfiguration())
        if (!access.root.shell(command, PROBE_SHELL_MS)) return Problem.START
        launched()
        return waitForProbe()
    }

    private suspend fun startOverShizuku(launched: () -> Unit): Problem? {
        if (access.shizuku.shell("id -u", PROBE_SHELL_MS).trim() != "2000") return Problem.SHIZUKU
        access.shizuku.shell(ownPermissionsCommand(), PROBE_SHELL_MS)
        if (access.shizuku.shell(probe.startCommand(probeConfiguration()), START_TIMEOUT_MS).startsWith("[SHIZUKU_")) return Problem.SHIZUKU
        launched()
        val problem = waitForProbe()
        access.shizuku.release()
        return problem
    }

    private suspend fun waitForProbe(): Problem? =
        if (withTimeoutOrNull(START_TIMEOUT_MS) { probe.service.first { it != null } } == null) Problem.START else null

    /** Whether the chosen way can start the monitor without asking the user anything. */
    private fun canStartUnattended(method: AccessMethod = access.method.value): Boolean = when (method) {
        com.ybblab.jiumi.access.AccessMethod.WIRELESS -> isPaired
        AccessMethod.NETWORK -> networkAdb.savedPort.value != null
        com.ybblab.jiumi.access.AccessMethod.ROOT -> access.root.wasReady
        com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> access.shizuku.refresh().ready
    }

    private val checkGate = com.ybblab.jiumi.access.AccessCheckGate()

    /** Asks the root manager for root (its own dialog, where supported), or checks whether root was granted. */
    fun authorizeRoot() {
        if (local.value.busy != null || !checkGate.beginRoot()) return
        local.update { it.copy(problem = null) }
        scope.launch(Dispatchers.IO) {
            try {
                access.root.authorize()
            } finally {
                checkGate.endRoot()
            }
        }
    }

    /** Asks Shizuku to let this app use it (Shizuku's own dialog). */
    fun authorizeShizuku() {
        if (!checkGate.beginShizuku()) return
        scope.launch {
            try {
                val ok = access.shizuku.requestAuthorization()
                local.update {
                    it.copy(problem = if (ok || access.method.value != com.ybblab.jiumi.access.AccessMethod.SHIZUKU) null else Problem.SHIZUKU)
                }
            } finally {
                checkGate.endShizuku()
            }
        }
    }

    fun selectAccess(method: com.ybblab.jiumi.access.AccessMethod) {
        val changed = access.method.value != method
        access.select(method)
        if (changed && method != AccessMethod.NETWORK) {
            networkCheckJob?.cancel()
            _networkCheck.value = NetworkCheck()
        }
        local.update { it.copy(problem = null) }
        when (method) {
            com.ybblab.jiumi.access.AccessMethod.WIRELESS -> Unit
            AccessMethod.NETWORK -> checkNetworkAdb()
            com.ybblab.jiumi.access.AccessMethod.ROOT -> {
                if (checkGate.onSelectRoot(changed, access.root.ready)) authorizeRoot()
            }
            com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> {
                if (checkGate.onSelectShizuku(access.shizuku.refresh())) authorizeShizuku()
            }
        }
    }

    /** Records that the user left Jiumi (including via a floating window) while no prompt of ours was running. */
    fun onAppPaused() {
        appVisible = false
        checkGate.onPaused()
    }

    /** Updates Root or Shizuku readiness when returning from a root manager or Shizuku without restarting. */
    fun onAppResumed() {
        appVisible = true
        refreshMonitoringPackages()
        when (access.method.value) {
            com.ybblab.jiumi.access.AccessMethod.WIRELESS, AccessMethod.NETWORK -> checkGate.onResumeWireless()
            com.ybblab.jiumi.access.AccessMethod.ROOT -> {
                if (checkGate.onResumeRoot(access.root.ready)) authorizeRoot()
            }
            com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> {
                val s = access.shizuku.refresh()
                val shouldAsk = checkGate.onResumeShizuku(s)
                if (s.ready) {
                    local.update { if (it.problem == Problem.SHIZUKU) it.copy(problem = null) else it }
                } else if (shouldAsk) {
                    authorizeShizuku()
                }
            }
        }
        // An open session whose monitor was lost while the app was away is picked up on return.
        resumeInterrupted("app returned")
    }

    /** Ends the session; the monitor stops if it runs (after an interruption it may not). */
    fun stopMonitor() {
        if (local.getAndUpdate { if (it.busy == null) it.copy(busy = Busy.STOPPING, problem = null) else it }.busy != null) return
        scope.launch(Dispatchers.IO) {
            val stopped = runCatching {
                manageLock.withLock {
                    val service = probe.service.value ?: return@withLock false
                    val partial = syncLock.withLock {
                        val traceIncomplete = service.finishRecording()
                        sync.pullEvents(service)
                        traceIncomplete
                    }
                    service.shutdown()
                    check(withTimeoutOrNull(5_000) { probe.service.first { it == null }; true } == true)
                    partial
                }
            }
            if (stopped.isFailure) {
                android.util.Log.w(TAG, "Could not finish monitoring", stopped.exceptionOrNull())
                local.update { it.copy(busy = null, problem = Problem.STOP) }
                return@launch
            }
            val now = System.currentTimeMillis()
            store.sessions().filter { it.end == null }.forEach { store.endSession(it.id, now) }
            showIndicator()
            local.update { it.copy(busy = null, problem = Problem.STOP_PARTIAL.takeIf { stopped.getOrThrow() }) }
            refresh()
            // Closing a legacy listener can restart adbd, so wait until the session is finished.
            if (probe.service.value == null) runCatching { pairing.closePendingLocalListener() }
        }
    }

    /**
     * The persistent notification follows the monitor: shown while it runs within an open session and
     * the user wants it, removed otherwise. Refreshes correct the timer from newly imported monitor
     * events. When the monitor stops under the open session it was shown for, a notice says so instead.
     */
    private fun Problem?.isNetworkProblem(): Boolean =
        this == Problem.NETWORK_UNAVAILABLE || this == Problem.NETWORK_AUTHORIZATION || this == Problem.NETWORK_IDENTITY

    @Synchronized
    private fun showIndicator() {
        val open = store.sessions().firstOrNull { it.end == null }
        val running = probe.service.value != null
        val status = local.value
        val on = status.notices.persistent
        val network = access.method.value == AccessMethod.NETWORK
        if (running || open == null || !on) networkInterruptionNotified = false
        val session = open?.takeIf { running && on }
        if (session == null) {
            if (shownSince != null) {
                MonitorNotificationService.hide(context)
                if (!network && on && open != null && !running && shownSince == open.start && status.busy != Busy.STOPPING) {
                    MonitorNotice.postInterrupted(context)
                }
                shownSince = null
            }
            // At boot there may be no previous running notification. Only notify once the
            // unattended recovery has failed, and deduplicate later retries of this interruption.
            if (network && on && open != null && !running && status.busy == null && !networkInterruptionNotified &&
                (status.problem.isNetworkProblem() || status.problem == Problem.START)) {
                val message = when (status.problem) {
                    Problem.NETWORK_UNAVAILABLE -> R.string.network_adb_unavailable
                    Problem.NETWORK_AUTHORIZATION -> R.string.network_adb_authorization_needed
                    Problem.NETWORK_IDENTITY -> R.string.network_adb_wrong_identity
                    else -> R.string.notification_interrupted_text
                }
                networkInterruptionNotified = MonitorNotice.postInterrupted(context, message)
            }
        } else {
            MonitorNotice.cancelInterrupted(context)
            val since = session.start
            val base = noticeChronometerBase(session)
            if (since == shownSince) {
                MonitorNotificationService.update(context, since) { it.copy(chronometerBase = base) }
            } else {
                // A start refused while the app is in the background is tried again on the next refresh.
                val notice = MonitorNotice.Running(since, base, noticeSummary(since))
                if (!MonitorNotificationService.show(context, notice)) return
                shownSince = since
            }
        }
        if (open == null || !on) MonitorNotice.cancelInterrupted(context)
        if (open == null) MonitorNotice.cancelAlerts(context)
        followNotices()
    }

    @Volatile private var shownSince: Long? = null
    @Volatile private var networkInterruptionNotified = false

    /** Posts the notification again, for when it was posted before notifications were allowed. */
    fun reshowIndicator() {
        shownSince = null
        networkInterruptionNotified = false
        scope.launch(Dispatchers.IO) { showIndicator() }
    }

    fun setNoticePersistent(on: Boolean) = setNotices(local.value.notices.copy(persistent = on))

    fun setNoticeSummary(on: Boolean) = setNotices(local.value.notices.copy(summary = on))

    fun setNoticeAlerts(on: Boolean) = setNotices(local.value.notices.copy(alerts = on))

    private fun setNotices(notices: Notices) {
        val before = local.value.notices
        settings.edit().putBoolean(KEY_NOTICE_PERSISTENT, notices.persistent).putBoolean(KEY_NOTICE_SUMMARY, notices.summary)
            .putBoolean(KEY_NOTICE_ALERTS, notices.alerts).apply()
        local.update { it.copy(notices = notices) }
        scope.launch(Dispatchers.IO) {
            // Alerts turned on are for what comes next: everything raised so far counts as seen.
            if (notices.alerts && !before.alerts) store.sessions().firstOrNull { it.end == null }?.let { open ->
                markAlerted(open.start, AttentionAlerts.keys(sessionReport(open.start).attention))
            }
            if (!notices.alerts) MonitorNotice.cancelAlerts(context)
            showIndicator()
            // The summary appears or goes at once, not at the next pull.
            if (notices.summary != before.summary) shownSince?.let { since ->
                val summary = noticeSummary(since)
                MonitorNotificationService.update(context, since) { it.copy(summary = summary) }
            }
        }
    }

    /** The summary for the notification of the session that began at [since], when the user wants one. */
    private fun noticeSummary(since: Long): NoticeSummary? =
        if (local.value.notices.summary) NoticeSummary.of(sessionReport(since), System.currentTimeMillis()) else null

    /** The chronometer uses elapsed realtime, seeded with the same covered duration as the session UI. */
    private fun noticeChronometerBase(session: JiumiStore.Session): Long {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        return elapsed - coverage(session, now).coveredMs
    }

    /**
     * The report of the open session that began at [since]: the one on screen when it covers that
     * session and is fresh, else built for the purpose.
     */
    private fun sessionReport(since: Long): Report {
        val l = local.value
        val now = System.currentTimeMillis()
        val ledger = l.ledger
        if (ledger != null && l.range == Range.SESSION && l.session?.session?.start == since && l.session?.latest == true &&
            now - rebuiltAt < NOTICE_INTERVAL_MS) return ledger
        return builder.report(since, now)
    }

    @Volatile private var appVisible = false
    private var noticeJob: kotlinx.coroutines.Job? = null

    /**
     * While the persistent notification is shown and its summary or alerts are wanted, the record is
     * pulled about once a minute when the screen is on (the app's own pages pull it while open), and
     * the notifications follow it. Nothing is pulled while the screen is off.
     */
    @Synchronized
    private fun followNotices() {
        val wanted = shownSince != null && local.value.notices.live
        if (!wanted) {
            noticeJob?.cancel()
            noticeJob = null
        } else if (noticeJob?.isActive != true) {
            noticeJob = scope.launch(Dispatchers.IO) {
                while (true) {
                    kotlinx.coroutines.delay(noticeIntervalMs)
                    runCatching { updateNotices() }.onFailure { android.util.Log.w(TAG, "notice update failed", it) }
                }
            }
        }
    }

    private val power = context.getSystemService(android.os.PowerManager::class.java)
    @Volatile private var noticeIntervalMs = NOTICE_INTERVAL_MS

    private suspend fun updateNotices() {
        val since = shownSince ?: return
        if (power?.isInteractive == false) return
        val service = probe.service.value ?: return
        val started = System.currentTimeMillis()
        syncLock.withLock {
            if (!appVisible) {
                configure(service)
                pendingLive = pullLiveChanges(service) || pendingLive
            }
            val report = sessionReport(since)
            val notices = local.value.notices
            if (notices.summary) {
                val summary = NoticeSummary.of(report, System.currentTimeMillis())
                MonitorNotificationService.update(context, since) { it.copy(summary = summary) }
            }
            if (notices.alerts) alert(since, report)
        }
        // A long session costs more to summarize; give the phone a breather in proportion.
        noticeIntervalMs = maxOf(NOTICE_INTERVAL_MS, (System.currentTimeMillis() - started) * 20)
    }

    /** Alerts the apps newly raised in the session that began at [since], once each per item. */
    private fun alert(since: Long, report: Report) {
        val notify = attentionRules.items.filter { it.notify }.map { it.id }.toSet()
        val fresh = AttentionAlerts.fresh(report.attention, notify, alerted(since))
        if (fresh.isEmpty()) return
        fresh.forEach { item ->
            report.attention.firstOrNull { it.id == item.id }?.let { MonitorNotice.postAlert(context, it, item.apps) }
        }
        markAlerted(since, alerted(since) + AttentionAlerts.keys(fresh))
    }

    /** What was alerted in the session that began at [since]; kept for the latest session only. */
    private fun alerted(since: Long): Set<String> =
        if (settings.getLong(KEY_ALERTED_SESSION, 0L) == since) settings.getStringSet(KEY_ALERTED, null).orEmpty() else emptySet()

    private fun markAlerted(since: Long, keys: Set<String>) {
        settings.edit().putLong(KEY_ALERTED_SESSION, since).putStringSet(KEY_ALERTED, keys).apply()
    }

    /** Starting begins a session; resuming after an interruption continues the open one. */
    private fun openSessionIfNone() {
        if (store.sessions().any { it.end == null }) return
        store.startSession(System.currentTimeMillis())
        local.update { it.copy(range = Range.SESSION, selected = null) }
        // Shown as running at once: the first sync imports the system's history and can take a while.
        scope.launch(Dispatchers.IO) {
            syncLock.withLock { rebuild() }
            refresh()
        }
    }

    /** Every session, newest first, for the monitoring record. */
    suspend fun sessions(): List<SessionView> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        store.sessions().mapIndexed { i, s -> SessionView(s, coverage(s, now), now, latest = i == 0) }
    }

    fun selectSession(id: Long) {
        local.update { it.copy(range = Range.SESSION, selected = id) }
        refresh()
    }

    /** Pulls what the monitor recorded (when it runs) and rebuilds the report from the store. */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            syncLock.withLock {
                probe.service.value?.let { service ->
                    configure(service)
                    runCatching { sync.sync(service) }.onFailure { android.util.Log.w(TAG, "sync failed", it) }
                    refreshLocationProviders(service)
                }
                rebuild()
            }
            checkFreezes()
        }
    }

    /**
     * Called every few seconds while the app is on screen: pulls what the monitor recorded since and,
     * only when something new came in, rebuilds the report, so every view follows a running session.
     * Skipped while another refresh is under way.
     */
    suspend fun refreshLive() = withContext(Dispatchers.IO) {
        val service = probe.service.value ?: return@withContext
        if (!syncLock.tryLock()) return@withContext
        try {
            configure(service)
            pendingLive = pullLiveChanges(service) || pendingLive
            refreshLocationProviders(service)
            // A long range costs more to rebuild; give the phone a breather in proportion.
            if (pendingLive && scrollingTimelines.isEmpty() && System.currentTimeMillis() - rebuiltAt >= rebuildCostMs * 4) rebuild()
        } finally {
            syncLock.unlock()
        }
        checkFreezes()
    }

    private fun pullLiveChanges(service: com.ybblab.jiumi.probe.IProbeService): Boolean =
        runCatching { sync.sync(service) }.onFailure { android.util.Log.w(TAG, "sync failed", it) }.getOrDefault(false)

    private val scrollingTimelines = java.util.concurrent.ConcurrentHashMap.newKeySet<Any>()

    /** Collecting and storing keep running while the UI is busy moving through older records. */
    internal fun timelineScrolling(owner: Any, scrolling: Boolean) {
        if (scrolling) scrollingTimelines.add(owner) else scrollingTimelines.remove(owner)
    }

    private class Built(val report: Report, val window: Pair<Long, Long>, val spans: List<LongRange>, val activity: Activity)

    private fun build(range: Range, session: SessionView?, now: Long): Built {
        val window = window(session, now, range)
        val report = builder.report(window.first, window.second, reuseGroups = true)
        val spans = store.sessions().filter { it.start < window.second && (it.end ?: now) > window.first }
            .flatMap { coverage(it, now).spans }.sortedBy { it.first }
        val activity = Activity.of(report.timeline, report.attention, report.apps, window.first, window.second, spans,
            report.systemSlots, routineKinds = report.routineKinds)
        return Built(report, window, spans, activity)
    }

    private fun rebuild() {
        val now = System.currentTimeMillis()
        val requested = local.value
        val range = requested.range
        val selected = requested.selected
        val session = chosenSession(now, selected)
        val current = build(range, session, now)
        val window = current.window
        // The open session, for the running monitor's card: the same curve as the session range shows.
        val open = store.sessions().firstOrNull()?.takeIf { it.end == null }
        val live = when {
            open == null -> null
            range == Range.SESSION && selected == null -> current
            else -> build(Range.SESSION, SessionView(open, coverage(open, now), now, latest = true), now)
        }
        local.update {
            if (it.range != range || it.selected != selected) it else it.copy(ledger = current.report, session = session, window = window, spans = current.spans,
                live = live?.activity, liveApps = live?.report?.apps?.count { a -> a.accesses + a.calls > 0 } ?: 0,
                activity = current.activity, shownRange = range)
        }
        rebuiltAt = System.currentTimeMillis()
        rebuildCostMs = rebuiltAt - now
        pendingLive = false
        showIndicator()
    }

    // Kept under syncLock.
    private var rebuiltAt = 0L
    private var rebuildCostMs = 0L
    private var pendingLive = false

    /** Most space the monitor's spool may use while the app is not opened; 0 means no limit. */
    fun setSpoolLimit(mb: Int) {
        settings.edit().putInt(KEY_SPOOL_MB, mb).apply()
        local.update { it.copy(spoolLimitMb = mb) }
        val service = probe.service.value ?: return
        scope.launch(Dispatchers.IO) { configure(service) }
    }

    private fun probeConfiguration(): String = rules.monitorConfig(local.value.callTracing, local.value.spoolLimitMb,
        monitoringExclusions.value.toJson())

    @Synchronized
    private fun configure(service: com.ybblab.jiumi.probe.IProbeService) {
        val config = probeConfiguration()
        if (configuredService === service && sentConfig == config) return
        runCatching { service.configure(config) }.onSuccess {
            configuredService = service
            sentConfig = config
        }.onFailure { android.util.Log.w(TAG, "probe configuration failed", it) }
    }

    fun setRange(range: Range) {
        local.update { it.copy(range = range, selected = if (range == Range.SESSION) it.selected else null) }
        refresh()
    }

    suspend fun detail(key: AppKey): AppDetail = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val (from, to) = window(chosenSession(now), now)
        builder.detail(key, from, to)
    }

    /** The picked session, or else the latest; null before the first session. */
    private fun chosenSession(now: Long, selected: Long? = local.value.selected): SessionView? {
        val all = store.sessions()
        val picked = selected?.let { id -> all.firstOrNull { it.id == id } } ?: all.firstOrNull() ?: return null
        return SessionView(picked, coverage(picked, now), now, latest = picked.id == all.first().id)
    }

    private fun coverage(s: JiumiStore.Session, now: Long) =
        Coverage.compute(s.start, s.end, store.monitorEvents(s.start - Coverage.MAX_GAP_MS, s.end ?: now), now)

    /** The time the report covers: one session, or a span reaching back from now. */
    private fun window(session: SessionView?, now: Long, range: Range = local.value.range): Pair<Long, Long> = when (range) {
        Range.SESSION -> session?.let { it.session.start to (it.session.end ?: now) } ?: (now to now)
        Range.TODAY -> daysEndingToday(now, 1) to now
        Range.WEEK -> daysEndingToday(now, 7) to now
        Range.MONTH -> daysEndingToday(now, 30) to now
    }

    /** Local midnight [days] - 1 days before today's. */
    private fun daysEndingToday(now: Long, days: Int): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        add(java.util.Calendar.DAY_OF_YEAR, 1 - days)
    }.timeInMillis

    /**
     * Writes the record of [range] (null: everything kept) to [uri] as a zip of CSV files; see
     * [LogExport]. [label] and [name] give app names and texts in the language the user sees.
     */
    suspend fun export(
        uri: android.net.Uri,
        range: Range?,
        readme: String,
        label: (AppKey) -> String,
        name: (prefix: String, id: String) -> String?
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val (from, to) = if (range == null) 0L to now else window(chosenSession(now), now, range)
            val sessions = store.sessions().filter { s -> s.start <= to && (s.end ?: now) >= from }
                .map { LogExport.SessionRow(it, coverage(it, now)) }
            val pairingLog = java.io.File(context.filesDir, "logs/adb.log").takeIf { it.isFile }?.readBytes()
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                LogExport(store, label, name).write(out, from, to, builder.report(from, to), sessions, readme,
                    listOfNotNull(pairingLog?.let { "pairing.log" to it }).toMap())
            }
        }.onFailure { android.util.Log.w(TAG, "export failed", it) }.isSuccess
    }

    fun unpair() {
        pairing.resetPairing()
        local.update { it.copy(problem = null) }
    }

    private fun refreshFootprint() {
        val service = probe.service.value ?: return
        scope.launch(Dispatchers.IO) {
            val footprint = runCatching { ProbeJson.footprint(service.footprint()) }.getOrNull()
            local.update { it.copy(footprint = footprint) }
        }
    }

    /** These grants change only this app: restoring Wireless debugging later and reading per-app traffic. */
    private fun ownPermissionsCommand(): String {
        val pkg = context.packageName
        return "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS; " +
            "appops set $pkg GET_USAGE_STATS allow" +
            (if (definesPermission(GET_INSTALLED_APPS)) "; pm grant $pkg $GET_INSTALLED_APPS" else "")
    }

    private fun definesPermission(name: String) = runCatching { context.packageManager.getPermissionInfo(name, 0) }.isSuccess

    private companion object {
        const val TAG = "JiumiSync"
        const val KEY_SPOOL_MB = "spool_limit_mb"
        const val KEY_ALLOW_REMOVE = "allow_remove_preinstalled"
        const val KEY_EXPERT = "expert_mode"
        const val DEFAULT_SPOOL_MB = 256
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val RESUME_DELAY_MS = 3_000L
        const val START_TIMEOUT_MS = 15_000L
        const val PROBE_SHELL_MS = 30_000L
        const val SHELL_PACKAGES_FILE = "shell_packages.json"
        const val LOCATION_PROVIDERS_FILE = "location_providers.json"
        const val PROVIDERS_MAX_AGE_MS = 10 * 60_000L

        /** The packages of a monitor's location-provider list; empty for none or anything unreadable. */
        fun readLocationProviders(json: String?): Set<String> = runCatching {
            val a = org.json.JSONArray(json ?: return emptySet())
            (0 until a.length()).map { a.getJSONObject(it).getString("pkg") }.toSet()
        }.getOrDefault(emptySet())
        const val SHELL_LIST_MAX_AGE_MS = 60_000L
        /** How often apps frozen from here are checked while the app is open and the monitor runs. */
        const val FREEZE_CHECK_MS = 5 * 60_000L
        const val ROOT_COMMAND_MS = 60_000L
        const val MAX_COMMAND_OUTPUT = 4_000
        const val PER_USER_RANGE = 100_000
        const val KEY_AUTO_REFREEZE = "auto_refreeze"
        const val KEY_NOTICE_PERSISTENT = "notice_persistent"
        const val KEY_NOTICE_SUMMARY = "notice_summary"
        const val KEY_NOTICE_ALERTS = "notice_alerts"
        const val KEY_ALERTED_SESSION = "alerted_session"
        const val KEY_ALERTED = "alerted"
        /** How often the notifications follow the record in the background, at the least. */
        const val NOTICE_INTERVAL_MS = 60_000L
        /** The app-list permission some phone makers add; granted where the phone defines it. */
        const val GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"
    }
}
