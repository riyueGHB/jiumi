// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.ybblab.jiumi.adb.AirModePairingManager.PairingInput
import com.ybblab.jiumi.data.AppKey
import com.ybblab.jiumi.data.LogEntry
import com.ybblab.jiumi.ui.LogDetailScreen
import com.ybblab.jiumi.ui.OpenedLogs
import com.ybblab.jiumi.ui.AppDetailScreen
import com.ybblab.jiumi.ui.AppLabels
import com.ybblab.jiumi.ui.AccessKind
import com.ybblab.jiumi.ui.AttentionScreen
import com.ybblab.jiumi.ui.ExportScreen
import com.ybblab.jiumi.ui.GroupScreen
import com.ybblab.jiumi.ui.HomeScreen
import com.ybblab.jiumi.ui.TimelineSnapshot
import com.ybblab.jiumi.ui.LanguageScreen
import com.ybblab.jiumi.ui.SessionsScreen
import com.ybblab.jiumi.ui.SettingsScreen
import com.ybblab.jiumi.ui.SettingsSection
import com.ybblab.jiumi.ui.MonitoringExclusionsScreen
import com.ybblab.jiumi.ui.NetworkManagementScreen
import com.ybblab.jiumi.ui.SettingsSectionScreen
import com.ybblab.jiumi.ui.SpoolScreen
import com.ybblab.jiumi.ui.ThemeScreen
import com.ybblab.jiumi.ui.AppsScreen
import com.ybblab.jiumi.ui.SystemModuleScreen
import com.ybblab.jiumi.ui.AboutScreen
import com.ybblab.jiumi.ui.LicensesScreen
import com.ybblab.jiumi.ui.PairingScreen
import com.ybblab.jiumi.ui.PairingCodeScreen
import com.ybblab.jiumi.ui.PairingOptionsScreen
import com.ybblab.jiumi.ui.NetworkAdbScreen
import com.ybblab.jiumi.ui.SetupScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ybblab.jiumi.ui.NoticeScreen
import com.ybblab.jiumi.ui.Tab
import com.ybblab.jiumi.ui.TabScaffold
import com.ybblab.jiumi.ui.theme.JiumiTheme

class MainActivity : AppCompatActivity() {
    private val controller get() = (application as JiumiApp).controller
    private val preferences get() = (application as JiumiApp).preferences
    private val labels by lazy { AppLabels(this) }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        beginPairing()
    }

    private val monitorNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) controller.reshowIndicator()
    }

    /** A page a notification asked to open, until it is shown. */
    private val requestedRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** The monitor and attention pages can be opened by Jiumi notifications. */
    private fun requestRoute(intent: Intent?) {
        intent?.getStringExtra(MonitorNotice.EXTRA_ROUTE)?.takeIf { it == MonitorNotice.ROUTE_MONITOR || it.startsWith(MonitorNotice.ROUTE_ATTENTION) }
            ?.let { requestedRoute.value = it }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestRoute(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestRoute(intent)
        enableEdgeToEdge()
        // An install paired before setup existed has nothing left to set up.
        if (controller.isPaired) preferences.finishSetup()
        // While the app is on screen, every page follows a running session as it records.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(LIVE_REFRESH_MS)
                    controller.refreshLive()
                }
            }
        }
        setContent {
            JiumiTheme {
                // Pages above the tabs: "app:uid|package", "group:id", "attention:id", "log:id", "theme",
                // "language", "spool", "sessions", "export", "about", "licenses", "notice:index", "pairing".
                var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
                var tab by rememberSaveable { mutableStateOf(Tab.MONITOR) }
                // Each page keeps its own state (scroll, chosen view, filters) while pages above it are open.
                val pages = rememberSaveableStateHolder()
                val homeTimeline = remember { TimelineSnapshot<LogEntry>() }
                val groupTimelines = remember { mutableMapOf<String, TimelineSnapshot<LogEntry>>() }
                val push = { route: String -> stack = stack + route }
                // A page's state is keyed by its depth too: the same page can be open twice in the stack.
                fun pageKey(depth: Int) = "$depth|${stack.getOrNull(depth - 1) ?: "home"}"
                fun removePage(depth: Int) {
                    val key = pageKey(depth)
                    pages.removeState(key)
                    groupTimelines.remove(key)
                }
                val pop = {
                    if (stack.lastOrNull() == "pairing-code") controller.cancelInAppPairing()
                    removePage(stack.size)
                    stack = stack.dropLast(1)
                }
                val openPairingOptions = { push("pairing-options") }
                val openApp = { key: AppKey -> push("app:${key.uid}|${key.pkg.orEmpty()}") }
                val openAccess = { key: AppKey, kind: AccessKind ->
                    push("access:${kind.name}|${key.uid}|${key.pkg.orEmpty()}")
                }
                val openLog = { entry: LogEntry -> push("log:${OpenedLogs.put(entry)}") }
                val top = stack.lastOrNull()
                val setupDone by preferences.setupDone.collectAsStateWithLifecycle()
                val route by requestedRoute.collectAsStateWithLifecycle()
                LaunchedEffect(route, setupDone) {
                    val r = route ?: return@LaunchedEffect
                    if (!setupDone) return@LaunchedEffect
                    if (top == "pairing-code") controller.cancelInAppPairing()
                    (1..stack.size).forEach(::removePage)
                    tab = Tab.MONITOR
                    stack = if (r == MonitorNotice.ROUTE_MONITOR) emptyList() else listOf(r)
                    requestedRoute.value = null
                }
                // On opening, a check for a newer release when one is due; a release found is offered once
                // in a sheet, and stays marked on the Settings tab until installed.
                val updates = androidx.compose.runtime.remember { com.ybblab.jiumi.update.Updates(applicationContext) }
                var offered by androidx.compose.runtime.remember { mutableStateOf<com.ybblab.jiumi.update.ReleaseInfo?>(null) }
                LaunchedEffect(Unit) {
                    com.ybblab.jiumi.update.UpdateNotice.available.value = updates.cached()
                    val release = updates.checkIfDue() ?: updates.cached()
                    com.ybblab.jiumi.update.UpdateNotice.available.value = release
                    if (release != null && !updates.announced(release.versionName)) {
                        updates.markAnnounced(release.versionName)
                        offered = release
                    }
                }
                offered?.let { if (setupDone) com.ybblab.jiumi.ui.UpdateDialog(updates, it, onClose = { offered = null }) }
                if (top != null) BackHandler(onBack = pop)
                pages.SaveableStateProvider(pageKey(stack.size)) { when {
                    top == null && !setupDone -> SetupScreen(controller, onPair = ::pair,
                        onOpenPairingOptions = openPairingOptions,
                        onOpenWirelessDebugging = ::openWirelessDebugging, onOpenNetwork = { push("network-adb") },
                        onDone = {
                            preferences.finishSetup()
                            if (Build.VERSION.SDK_INT >= 33 && !MonitorNotice.allowed(this@MainActivity) &&
                                controller.access.method.value == com.ybblab.jiumi.access.AccessMethod.NETWORK) {
                                monitorNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        })
                    top == null -> TabScaffold(tab, onSelect = { tab = it }) { selected ->
                        // Each tab keeps its own scroll and choices while another is shown.
                        pages.SaveableStateProvider("tab|${selected.name}") { when (selected) {
                            Tab.MONITOR -> HomeScreen(
                                controller = controller,
                                labels = labels,
                                timelineSnapshot = homeTimeline,
                                onOpenGroup = { push("group:$it") },
                                onOpenAttention = { push("attention:$it") },
                                onOpenLog = openLog,
                                onOpenApp = openApp,
                                onOpenPairing = { push("pairing") },
                                onOpenNetwork = { push("network-adb") }
                            )
                            Tab.APPS -> AppsScreen(controller, labels, onOpenApp = openApp,
                                onOpenModule = { push("apex:$it") })
                            Tab.SETTINGS -> SettingsScreen(
                                preferences,
                                onOpenSection = { push("settings:${it.name}") },
                                onOpenTheme = { push("theme") },
                                onOpenLanguage = { push("language") },
                                onOpenAbout = { push("about") }
                            )
                        } }
                    }
                    top.startsWith("app:") -> {
                        val (uid, pkg) = top.removePrefix("app:").split('|', limit = 2)
                        val key = AppKey(uid.toInt(), pkg.ifEmpty { null })
                        AppDetailScreen(controller, labels, key, onBack = pop, onOpenLog = openLog,
                            onOpenAppManagement = { push("settings:${SettingsSection.MANAGEMENT.name}") },
                            onOpenAccess = { kind -> openAccess(key, kind) })
                    }
                    top.startsWith("apex:") ->
                        SystemModuleScreen(controller, labels, top.removePrefix("apex:"), onBack = pop, onOpenApp = openApp)
                    top.startsWith("access:") -> {
                        val (mode, uid, pkg) = top.removePrefix("access:").split('|', limit = 3)
                        com.ybblab.jiumi.ui.AppAccessScreen(
                            controller, labels, AppKey(uid.toInt(), pkg),
                            AccessKind.entries.firstOrNull { it.name == mode } ?: AccessKind.PERMISSIONS,
                            onBack = pop
                        )
                    }
                    top.startsWith("log:") -> {
                        // A record kept only in memory is gone after the process was recreated.
                        val entry = OpenedLogs.get(top.removePrefix("log:").toInt())
                        if (entry == null) LaunchedEffect(top) { pop() }
                        else LogDetailScreen(controller, labels, entry, onBack = pop, onOpenApp = openApp, onOpenLog = openLog)
                    }
                    top.startsWith("group:") -> GroupScreen(controller, labels, top.removePrefix("group:"),
                        groupTimelines.getOrPut(pageKey(stack.size)) { TimelineSnapshot() }, onBack = pop,
                        onOpenLog = openLog, onOpenApp = openApp)
                    top.startsWith("attention:") -> AttentionScreen(controller, labels, top.removePrefix("attention:"), onBack = pop,
                        onOpenApp = { push("app:${it.uid}|${it.pkg.orEmpty()}") })
                    top.startsWith("settings:") -> {
                        val section = SettingsSection.entries.firstOrNull { "settings:${it.name}" == top }
                        if (section == null) LaunchedEffect(top) { pop() }
                        else SettingsSectionScreen(
                            section, controller, preferences, onBack = pop,
                            onOpenSpool = { push("spool") }, onOpenSessions = { push("sessions") }, onOpenExport = { push("export") },
                            onOpenPairing = { push("pairing") },
                            onOpenNetwork = { push("network-adb") },
                            onOpenMonitoringExclusions = { push("monitoring-exclusions") },
                            onOpenNetworkManagement = { push("network-management") },
                            onHideFromRecentsChange = { hide ->
                                preferences.setHideFromRecents(hide)
                                applyRecentsVisibility()
                            },
                            onOpenMonitor = {
                                (1..stack.size).forEach(::removePage)
                                stack = listOf()
                                tab = Tab.MONITOR
                            }
                        )
                    }
                    top == "network-adb" -> NetworkAdbScreen(controller, onBack = pop)
                    top == "pairing" -> PairingScreen(controller, onBack = pop, onPair = ::pair,
                        onOpenWirelessDebugging = ::openWirelessDebugging, onOpenPairingOptions = openPairingOptions)
                    top == "pairing-options" -> PairingOptionsScreen(onBack = pop, onEnterPairingCode = { push("pairing-code") })
                    top == "pairing-code" -> PairingCodeScreen(controller, onBack = pop,
                        onOpenWirelessDebugging = ::openPairingSettingsForInAppCode)
                    top == "spool" -> SpoolScreen(controller, onBack = pop)
                    top == "monitoring-exclusions" -> MonitoringExclusionsScreen(controller, labels, onBack = pop)
                    top == "network-management" -> NetworkManagementScreen(controller, labels,
                        onOpenApp = { openAccess(it, AccessKind.NETWORK) }, onBack = pop)
                    top == "about" -> AboutScreen(labels, onBack = pop, onOpenLicenses = { push("licenses") })
                    top == "licenses" -> LicensesScreen(onBack = pop, onOpenNotice = { push("notice:$it") })
                    top.startsWith("notice:") -> NoticeScreen(top.removePrefix("notice:").toInt(), onBack = pop)
                    top == "export" -> ExportScreen(controller, labels, onBack = pop)
                    top == "sessions" -> SessionsScreen(controller, onBack = pop, onChosen = {
                        (1..stack.size).forEach(::removePage); stack = listOf()
                    })
                    top == "theme" -> ThemeScreen(preferences, onBack = pop)
                    top == "language" -> LanguageScreen(preferences, onBack = pop)
                } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyRecentsVisibility()
        controller.onAppResumed()
        controller.refresh()
    }

    private fun applyRecentsVisibility() {
        val activityManager = getSystemService(ActivityManager::class.java)
        activityManager.appTasks.firstOrNull { it.taskInfo?.taskId == taskId }
            ?.setExcludeFromRecents(preferences.hideFromRecents.value)
    }

    override fun onPause() {
        controller.onAppPaused()
        super.onPause()
    }

    private fun pair() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            beginPairing()
        }
    }

    private fun beginPairing() {
        if (controller.startPairing(PairingInput.NOTIFICATION)) openWirelessDebugging()
    }

    private fun openWirelessDebugging() {
        controller.preparePairingSettings()
        com.ybblab.jiumi.ui.openDeveloperOptions(this)
    }

    private fun openPairingSettingsForInAppCode() {
        if (controller.startPairing(PairingInput.APP)) {
            controller.preparePairingSettings()
            com.ybblab.jiumi.ui.openDeveloperOptions(this, adjacent = isInMultiWindowMode)
        }
    }
}

/** How often the pages catch up with a running monitor while the app is on screen. */
private const val LIVE_REFRESH_MS = 3_000L
