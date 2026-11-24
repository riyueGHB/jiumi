// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.AppLanguage
import com.ybblab.jiumi.AppPreferences
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.MonitorController.Busy
import com.ybblab.jiumi.R
import com.ybblab.jiumi.ThemeMode
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.adb.NetworkAdb
import com.ybblab.jiumi.adb.messageRes
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.StatusWarning
import com.ybblab.jiumi.ui.theme.StatusSuccess

internal enum class SettingsSection(
    val title: Int,
    val summary: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    CONNECTION(R.string.settings_connection, R.string.settings_connection_summary, JiumiIcons.Wifi),
    MONITORING(R.string.section_monitoring, R.string.settings_monitor_summary, JiumiIcons.Activity),
    NOTIFICATIONS(R.string.section_notifications, R.string.settings_notifications_summary, JiumiIcons.Notifications),
    RECORDS(R.string.settings_records, R.string.settings_records_summary, JiumiIcons.Storage),
    MANAGEMENT(R.string.settings_management, R.string.settings_management_summary, JiumiIcons.Shield),
    ADVANCED(R.string.section_advanced, R.string.settings_advanced_summary, JiumiIcons.Cpu)
}

@Composable
internal fun SettingsScreen(
    preferences: AppPreferences,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrolled by remember { androidx.compose.runtime.derivedStateOf { list.canScrollBackward } }
    val update by com.ybblab.jiumi.update.UpdateNotice.available.collectAsStateWithLifecycle()
    val theme by preferences.themeMode.collectAsStateWithLifecycle()
    HidingTopBar(stringResource(R.string.settings_title), scrolled) { top ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + PageTopGap,
                bottom = 32.dp + LocalTabBarInset.current)
        ) {
            item {
                Card {
                    listOf(SettingsSection.CONNECTION, SettingsSection.MONITORING, SettingsSection.NOTIFICATIONS,
                        SettingsSection.RECORDS).forEachIndexed { index, section ->
                        if (index > 0) InsetDivider()
                        SettingsSectionRow(section, onOpenSection)
                    }
                }
            }
            item {
                Card {
                    SettingsSectionRow(SettingsSection.MANAGEMENT, onOpenSection)
                    InsetDivider()
                    SettingsSectionRow(SettingsSection.ADVANCED, onOpenSection)
                }
            }
            item {
                Card {
                    ListRow(stringResource(R.string.section_appearance), subtitle = themeLabel(theme),
                        leading = { IconTile(JiumiIcons.Palette) }, onClick = onOpenTheme) {
                        Chevron()
                    }
                    InsetDivider()
                    ListRow(stringResource(R.string.settings_language), subtitle = languageLabel(preferences.language),
                        leading = { IconTile(JiumiIcons.Languages) }, onClick = onOpenLanguage) {
                        Chevron()
                    }
                }
            }
            item {
                Card {
                    ListRow(stringResource(R.string.settings_about), subtitle = stringResource(R.string.settings_about_summary),
                        leading = { IconTile(JiumiIcons.Info) }, onClick = onOpenAbout) {
                        if (update != null) { NewDot(); TrailingValue(stringResource(R.string.update_new_badge)) }
                        Chevron()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionRow(section: SettingsSection, onOpenSection: (SettingsSection) -> Unit) {
    ListRow(stringResource(section.title), subtitle = stringResource(section.summary),
        leading = { IconTile(section.icon) }, onClick = { onOpenSection(section) }) {
        Chevron()
    }
}

@Composable
internal fun SettingsSectionScreen(
    section: SettingsSection,
    controller: MonitorController,
    preferences: AppPreferences,
    onBack: () -> Unit,
    onOpenSpool: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenMonitoringExclusions: () -> Unit,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onOpenMonitor: () -> Unit
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val selectedMethod = state.method
    SubScreen(stringResource(section.title), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        ) {
            when (section) {
                SettingsSection.CONNECTION -> {
                    val pairingFlow = state.pairingState in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE, PairingState.PAIRING)
                    val paired = state.trust == PairingTrustState.PAIRED
                    item {
                        // The requirements of the selected way follow beneath.
                        Segmented(
                            com.ybblab.jiumi.access.AccessMethod.entries.map { method ->
                                when (method) {
                                    com.ybblab.jiumi.access.AccessMethod.WIRELESS -> stringResource(R.string.connection_wireless_short)
                                    com.ybblab.jiumi.access.AccessMethod.NETWORK -> stringResource(R.string.connection_network_short)
                                    else -> accessName(method)
                                }
                            },
                            selected = selectedMethod.ordinal,
                            onSelect = { controller.selectAccess(com.ybblab.jiumi.access.AccessMethod.entries[it]) },
                            modifier = Modifier.fillMaxWidth(1f),
                            activeOption = state.method.ordinal
                        )
                        GroupFooter(stringResource(R.string.access_pick_one))
                        Spacer(Modifier.height(12.dp))
                    }
                    if (selectedMethod == com.ybblab.jiumi.access.AccessMethod.NETWORK) item {
                        val check by controller.networkCheck.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { controller.ensureNetworkAdbChecked() }
                        val result = check.result
                        val problem = networkAdbProblem(result)
                        val status = when (result) {
                            is NetworkAdb.Result.Ready -> stringResource(R.string.network_adb_saved_port, result.port)
                            is NetworkAdb.Result.NeedsAuthorization -> stringResource(R.string.network_adb_needs_authorization)
                            NetworkAdb.Result.Unavailable -> stringResource(R.string.network_adb_not_found)
                            NetworkAdb.Result.WrongIdentity -> stringResource(R.string.network_adb_unusable)
                            null -> stringResource(R.string.network_adb_searching)
                        }
                        Card {
                            ListRow(stringResource(R.string.access_network_debugging),
                                leading = { IconTile(JiumiIcons.Network) }, onClick = {
                                    if (result is NetworkAdb.Result.NeedsAuthorization) {
                                        controller.checkNetworkAdb(result.port, authorize = true)
                                    } else onOpenNetwork()
                                }) {
                                TrailingValue(status,
                                    color = if (problem == null) MaterialTheme.colorScheme.onSurfaceVariant else StatusWarning)
                                Chevron()
                            }
                        }
                        problem?.let { GroupFooter(stringResource(it), warning = true) }
                        GroupFooter(stringResource(R.string.access_network_desc))
                    }
                    if (selectedMethod == com.ybblab.jiumi.access.AccessMethod.ROOT) item {
                        Card {
                            ListRow(stringResource(R.string.access_root), leading = { IconTile(JiumiIcons.Shield) },
                                onClick = controller::authorizeRoot) {
                                TrailingValue(stringResource(rootStatus(state.root)),
                                    color = if (rootError(state.root) == null) MaterialTheme.colorScheme.onSurfaceVariant else StatusWarning)
                                Chevron()
                            }
                        }
                        // Why the last try failed comes first; what this way is, after.
                        rootError(state.root)?.let { GroupFooter(stringResource(it), warning = true) }
                        GroupFooter(accessDescription(state.method))
                    }
                    if (selectedMethod == com.ybblab.jiumi.access.AccessMethod.SHIZUKU) item {
                        // Shizuku may have been started or stopped while away; read it again when shown.
                        androidx.compose.runtime.LaunchedEffect(Unit) { controller.access.shizuku.refresh() }
                        Card {
                            ListRow(stringResource(R.string.access_shizuku), leading = { IconTile(JiumiIcons.Shield) },
                                onClick = { runShizukuNextAction(controller, state.shizuku) }) {
                                TrailingValue(shizukuStatus(state.shizuku),
                                    color = if (state.ready) MaterialTheme.colorScheme.onSurfaceVariant else StatusWarning)
                                Chevron()
                            }
                        }
                        if (state.problem == MonitorController.Problem.SHIZUKU) GroupFooter(stringResource(R.string.error_shizuku), warning = true)
                        GroupFooter(accessDescription(state.method))
                    }
                    if (selectedMethod == com.ybblab.jiumi.access.AccessMethod.WIRELESS) item {
                        val persistence by controller.adbPersistence.collectAsStateWithLifecycle()
                        val usbOn = remember(persistence, state.pairingState) { controller.usbDebuggingEnabled() }
                        val needsUsb = persistence && usbOn == false
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Card {
                            ListRow(
                                stringResource(R.string.row_pairing),
                                leading = { IconTile(JiumiIcons.Wifi) },
                                onClick = onOpenPairing
                            ) {
                                TrailingValue(stringResource(when {
                                    pairingFlow -> R.string.pairing_in_progress
                                    state.pairingState == PairingState.CONNECTING -> R.string.pairing_connecting
                                    paired -> R.string.pairing_paired
                                    state.trust == PairingTrustState.PAIRING_REQUIRED -> R.string.pairing_required
                                    else -> R.string.pairing_unpaired
                                }), color = if (paired || pairingFlow) MaterialTheme.colorScheme.onSurfaceVariant else StatusWarning)
                                Chevron()
                            }
                        }
                        // Without USB debugging, adbd stops when the system turns Wireless debugging off (as it does
                        // on leaving Wi-Fi), and the monitor it started ends with it.
                        GroupFooter(stringResource(R.string.access_wireless_desc))
                        if (usbOn == false) GroupFooter(stringResource(R.string.settings_usb_debugging_off), warning = true)
                        Spacer(Modifier.height(16.dp))
                        GroupTitle(stringResource(R.string.settings_adb_options))
                        Card {
                            ListRow(
                                stringResource(R.string.settings_adb_persistence),
                                leading = { IconTile(JiumiIcons.Link) },
                                onClick = {
                                    if (needsUsb) openDeveloperOptions(context, KEY_USB_DEBUGGING)
                                    else controller.setAdbPersistence(!persistence)
                                }
                            ) {
                                JiumiSwitch(persistence, onCheckedChange = controller::setAdbPersistence)
                            }
                        }
                        GroupFooter(stringResource(when {
                            needsUsb -> R.string.settings_adb_persistence_usb_required
                            state.running && controller.localPortClosePending -> R.string.settings_adb_persistence_close_after_stop
                            persistence && !controller.hasLocalAdbPort -> R.string.settings_adb_persistence_next_start
                            persistence -> R.string.settings_adb_persistence_verified
                            else -> R.string.settings_adb_persistence_desc
                        }), warning = needsUsb)
                        if (controller.localPortClosePending && !state.running) Card {
                            ListRow(stringResource(R.string.settings_close_legacy_adb), onClick = { controller.setAdbPersistence(false) })
                        }
                    }
                }
                SettingsSection.MONITORING -> {
                    item {
                        Card {
                            // Where the monitor stands, as the Monitor tab shows it at the same moment; tapped,
                            // that tab, where it is started and stopped.
                            val failed = state.problem != null && !state.running && state.busy == null
                            ListRow(stringResource(R.string.row_service), leading = { IconTile(JiumiIcons.Shield) }, onClick = onOpenMonitor) {
                                TrailingValue(when {
                                    state.busy == MonitorController.Busy.STOPPING -> stringResource(R.string.service_stopping)
                                    state.busy == MonitorController.Busy.STARTING && state.pairingState == PairingState.CONNECTING ->
                                        stringResource(R.string.pairing_connecting)
                                    state.busy == MonitorController.Busy.STARTING -> stringResource(R.string.service_starting)
                                    state.running -> listOfNotNull(stringResource(R.string.service_running),
                                        state.footprint?.let { "${(it.pssKb / 1024).toInt()} MB" }).joinToString(" · ")
                                    failed -> stringResource(R.string.service_failed)
                                    else -> stringResource(R.string.hero_idle)
                                }, color = if (failed) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant)
                                Chevron()
                            }
                        }
                        if (state.problem != null && !state.running && state.busy == null) statusMessage(state)?.let { GroupFooter(it, warning = true) }
                    }
                    item {
                        val exclusions by controller.monitoringExclusions.collectAsStateWithLifecycle()
                        Spacer(Modifier.height(16.dp))
                        Card {
                            ListRow(stringResource(R.string.monitoring_exclusions), leading = { IconTile(JiumiIcons.Shield) },
                                onClick = onOpenMonitoringExclusions) {
                                TrailingValue(exclusions.active.size.toString())
                                Chevron()
                            }
                        }
                        GroupFooter(stringResource(R.string.monitoring_exclusions_desc))
                    }
                    item {
                        GroupTitle(stringResource(R.string.settings_domain_recording), Modifier.padding(top = 24.dp))
                        DomainRecordingRow()
                    }
                }
                SettingsSection.NOTIFICATIONS -> item { NoticeRows(state.notices, controller) }
                SettingsSection.RECORDS -> {
                    item {
                        Card {
                            ListRow(stringResource(R.string.settings_sessions), leading = { IconTile(JiumiIcons.History) }, onClick = onOpenSessions) {
                                Chevron()
                            }
                            InsetDivider()
                            ListRow(stringResource(R.string.settings_export), leading = { IconTile(JiumiIcons.Download) }, onClick = onOpenExport) {
                                Chevron()
                            }
                            InsetDivider()
                            ListRow(stringResource(R.string.settings_spool), leading = { IconTile(JiumiIcons.Storage) }, onClick = onOpenSpool) {
                                TrailingValue(spoolLabel(state.spoolLimitMb))
                                Chevron()
                            }
                        }
                    }
                }
                SettingsSection.MANAGEMENT -> {
                    item {
                        // Turning it on is a deliberate step: it asks, with what it means, before it takes effect.
                        var askAllowRemove by remember { mutableStateOf(false) }
                        Card {
                            ListRow(stringResource(R.string.settings_manage_identity), leading = { IconTile(JiumiIcons.Shield) }) {
                                TrailingValue(stringResource(if (state.manageAsRoot) R.string.manage_identity_root else R.string.manage_identity_shell))
                            }
                            InsetDivider()
                            ListRow(
                                stringResource(R.string.settings_auto_refreeze),
                                subtitle = stringResource(R.string.settings_auto_refreeze_desc),
                                leading = { IconTile(JiumiIcons.Snowflake) },
                                onClick = { controller.setAutoRefreeze(!state.autoRefreeze) }
                            ) {
                                JiumiSwitch(state.autoRefreeze) { controller.setAutoRefreeze(it) }
                            }
                            InsetDivider()
                            ListRow(
                                stringResource(R.string.settings_allow_remove),
                                subtitle = stringResource(R.string.settings_allow_remove_desc),
                                leading = { IconTile(JiumiIcons.Trash) },
                                // The whole row turns it, as on iOS; the switch alone is a small target.
                                onClick = {
                                    if (!state.allowRemovePreinstalled) askAllowRemove = true else controller.setAllowRemovePreinstalled(false)
                                }
                            ) {
                                JiumiSwitch(state.allowRemovePreinstalled) { on ->
                                    if (on) askAllowRemove = true else controller.setAllowRemovePreinstalled(false)
                                }
                            }
                        }
                        GroupFooter(stringResource(if (state.manageAsRoot) R.string.settings_manage_root_desc else R.string.settings_manage_shell_desc))
                        if (askAllowRemove) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { askAllowRemove = false },
                                containerColor = MaterialTheme.colorScheme.surface,
                                icon = { Icon(JiumiIcons.Warning, null, tint = com.ybblab.jiumi.ui.theme.StatusWarning) },
                                title = { Text(stringResource(R.string.allow_remove_title), style = MaterialTheme.typography.titleMedium) },
                                text = {
                                    Text(emphasizeAppName(stringResource(R.string.allow_remove_body)), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = {
                                        askAllowRemove = false
                                        controller.setAllowRemovePreinstalled(true)
                                    }) {
                                        Text(stringResource(R.string.allow_remove_confirm), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { askAllowRemove = false }) {
                                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            )
                        }
                    }
                }
                SettingsSection.ADVANCED -> item {
                    Card { ExpertModeRow(state.expertMode, controller::setExpertMode) }
                    GroupFooter(stringResource(R.string.settings_expert_mode_desc))
                    Spacer(Modifier.height(16.dp))
                    val hideFromRecents by preferences.hideFromRecents.collectAsStateWithLifecycle()
                    Card {
                        ListRow(
                            stringResource(R.string.settings_hide_from_recents),
                            subtitle = stringResource(R.string.settings_hide_from_recents_desc),
                            leading = { IconTile(JiumiIcons.Layers) },
                            onClick = { onHideFromRecentsChange(!hideFromRecents) }
                        ) {
                            JiumiSwitch(hideFromRecents, onCheckedChange = onHideFromRecentsChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeScreen(preferences: AppPreferences, onBack: () -> Unit) {
    val mode by preferences.themeMode.collectAsStateWithLifecycle()
    SubScreen(stringResource(R.string.section_appearance), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).selectableGroup(), contentPadding = PaddingValues(16.dp)) {
            item {
                Card {
                    ThemeMode.entries.forEachIndexed { index, value ->
                        if (index > 0) InsetDivider(start = 16.dp)
                        SelectionRow(themeLabel(value), selected = mode == value) { preferences.setThemeMode(value) }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageScreen(preferences: AppPreferences, onBack: () -> Unit) {
    // The activity is recreated after a change, so the current value is read on each composition.
    val current = preferences.language
    SubScreen(stringResource(R.string.settings_language), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).selectableGroup(), contentPadding = PaddingValues(16.dp)) {
            item {
                Card {
                    AppLanguage.entries.forEachIndexed { index, value ->
                        if (index > 0) InsetDivider(start = 16.dp)
                        SelectionRow(languageLabel(value), selected = current == value) {
                            if (current != value) preferences.setLanguage(value)
                        }
                    }
                }
            }
        }
    }
}

/** Spool limits offered in Settings, in MB; 0 keeps everything until storage runs low. */
val SPOOL_CHOICES = listOf(256, 1024, 2048, 0)

@Composable
fun spoolLabel(mb: Int): String = when {
    mb == 0 -> stringResource(R.string.spool_unlimited)
    mb >= 1024 -> "${mb / 1024} GB"
    else -> "$mb MB"
}

@Composable
fun SpoolScreen(controller: MonitorController, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    SubScreen(stringResource(R.string.settings_spool), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).selectableGroup(), contentPadding = PaddingValues(16.dp)) {
            item {
                Card {
                    SPOOL_CHOICES.forEachIndexed { index, mb ->
                        if (index > 0) InsetDivider(start = 16.dp)
                        SelectionRow(spoolLabel(mb), selected = state.spoolLimitMb == mb) { controller.setSpoolLimit(mb) }
                    }
                }
            }
            item {
                Text(stringResource(R.string.spool_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

/** Every monitoring session; choosing one shows its report. */
@Composable
fun SessionsScreen(controller: MonitorController, onBack: () -> Unit, onChosen: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val sessions by androidx.compose.runtime.produceState<List<MonitorController.SessionView>?>(null, state.ledger) {
        value = controller.sessions()
    }
    SubScreen(stringResource(R.string.settings_sessions), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            val list = sessions
            if (list != null && list.isEmpty()) item { EmptyCard(stringResource(R.string.sessions_empty)) }
            if (!list.isNullOrEmpty()) item {
                Card {
                    list.forEachIndexed { index, s ->
                        if (index > 0) InsetDivider()
                        ListRow(
                            title = sessionSpan(s.session.start, s.session.end),
                            subtitle = listOfNotNull(
                                stringResource(R.string.session_coverage, formatDuration(s.coverage.coveredMs)),
                                s.coverage.interruptions.takeIf { it > 0 }?.let {
                                    pluralStringResource(R.plurals.interruptions, it, formatCount(it.toLong()))
                                },
                                s.coverage.lostEvents.takeIf { it > 0 }?.let {
                                    pluralStringResource(R.plurals.lost_records, it.toInt(), formatCount(it))
                                },
                                if (s.coverage.partialTrace) stringResource(R.string.session_partial_trace) else null
                            ).joinToString(" · "),
                            leading = { IconTile(JiumiIcons.History) },
                            onClick = { controller.selectSession(s.session.id); onChosen() }
                        ) { Chevron() }
                    }
                }
            }
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_zh
        AppLanguage.ENGLISH -> R.string.language_en
    }
)

/**
 * Expert mode: most core system components may be changed too. Like removing preinstalled apps,
 * turning it on asks first and says what is at stake; turning it off takes effect at once.
 */
@Composable
private fun ExpertModeRow(on: Boolean, onChange: (Boolean) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    ListRow(
        stringResource(R.string.settings_expert_mode),
        leading = { IconTile(JiumiIcons.Cpu) },
        onClick = { if (on) onChange(false) else asking = true }
    ) {
        JiumiSwitch(on) { if (it) asking = true else onChange(false) }
    }
    if (asking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(JiumiIcons.Warning, null, tint = com.ybblab.jiumi.ui.theme.StatusWarning) },
            title = { Text(stringResource(R.string.expert_title), style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(emphasizeAppName(stringResource(R.string.expert_body)), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { asking = false; onChange(true) }) {
                    Text(stringResource(R.string.expert_confirm), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { asking = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

/**
 * The notifications while monitoring: the persistent one, and under it its summary and attention
 * alerts, which need it to keep running. With Jiumi's notifications turned off, the first row opens
 * the system settings for them instead.
 */
@Composable
private fun NoticeRows(notices: MonitorController.Notices, controller: MonitorController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var allowed by remember { mutableStateOf(com.ybblab.jiumi.MonitorNotice.allowed(context)) }
    // Read again on return from the system settings; allowed again, the notification is posted now.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        val now = com.ybblab.jiumi.MonitorNotice.allowed(context)
        if (now && !allowed) controller.reshowIndicator()
        allowed = now
        onPauseOrDispose {}
    }
    val openSettings = {
        runCatching {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        Unit
    }
    Card {
        ListRow(stringResource(R.string.settings_notice_persistent), leading = { IconTile(JiumiIcons.Notifications) },
            onClick = { if (!allowed) openSettings() else controller.setNoticePersistent(!notices.persistent) }) {
            JiumiSwitch(notices.persistent) { controller.setNoticePersistent(it) }
        }
        if (notices.persistent) {
            InsetDivider()
            ListRow(stringResource(R.string.settings_notice_summary), leading = { IconTile(JiumiIcons.Activity) },
                onClick = { controller.setNoticeSummary(!notices.summary) }) {
                JiumiSwitch(notices.summary) { controller.setNoticeSummary(it) }
            }
            InsetDivider()
            ListRow(stringResource(R.string.settings_notice_alerts), leading = { IconTile(JiumiIcons.Warning) },
                onClick = { controller.setNoticeAlerts(!notices.alerts) }) {
                JiumiSwitch(notices.alerts) { controller.setNoticeAlerts(it) }
            }
        }
    }
    when {
        !allowed -> GroupFooter(stringResource(R.string.settings_notice_blocked), warning = true)
        notices.persistent -> GroupFooter(stringResource(R.string.settings_notice_desc))
        else -> GroupFooter(stringResource(R.string.settings_notice_off_desc))
    }
}

/** What the setting in the card above means, in small grey type beneath it, as iOS groups explain themselves. */
@Composable
internal fun GroupFooter(text: String, warning: Boolean = false) {
    Text(if (warning) androidx.compose.ui.text.AnnotatedString(text) else emphasizeAppName(text), style = MaterialTheme.typography.bodySmall,
        color = if (warning) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
}

/**
 * Domain recording: on, a VPN that carries only DNS records which app looks up which domain. The
 * system asks the user once before the first VPN is set up.
 */
@Composable
private fun DomainRecordingRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val domains = (context.applicationContext as com.ybblab.jiumi.JiumiApp).domains
    val enabled by domains.enabled.collectAsStateWithLifecycle()
    val running by domains.running.collectAsStateWithLifecycle()
    val strict by domains.strictPrivateDns.collectAsStateWithLifecycle()
    val vpn by domains.vpnPresent.collectAsStateWithLifecycle()
    var refused by remember { mutableStateOf(false) }
    val consent = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        refused = result.resultCode != android.app.Activity.RESULT_OK
        if (!refused) domains.setEnabled(true)
    }
    val turn = { on: Boolean ->
        if (!on) domains.setEnabled(false)
        else {
            refused = false
            val ask = domains.consentIntent()
            if (ask == null) domains.setEnabled(true) else runCatching { consent.launch(ask) }
        }
    }
    Card {
        ListRow(stringResource(R.string.settings_domains), leading = { IconTile(JiumiIcons.Network) }, onClick = { turn(!enabled) }) {
            JiumiSwitch(enabled) { turn(it) }
        }
    }
    val (note, warning) = when {
        refused -> stringResource(R.string.settings_domains_refused) to true
        enabled && running && strict -> stringResource(R.string.settings_domains_private_dns) to true
        // Not this app's own VPN: another app's holds the phone's one VPN slot.
        !running && vpn -> stringResource(R.string.settings_domains_other_vpn) to true
        enabled && !running -> stringResource(R.string.settings_domains_stopped) to true
        else -> stringResource(R.string.settings_domains_desc) to false
    }
    GroupFooter(note, warning = warning)
}
