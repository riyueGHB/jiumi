// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.access.RootShell
import com.ybblab.jiumi.R
import com.ybblab.jiumi.adb.NetworkAdb
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.adb.messageRes
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.ui.theme.StatusWarning

/**
 * The first page on a new install: a choice of how to start the monitor (Wireless debugging, root or
 * Shizuku) as three cards, then what that way needs a step at a time, in the order the
 * phone needs it. Each step ticks itself off when the user comes back from the
 * system's pages; the button at the foot of the page always does the step at hand.
 */
@Composable
fun SetupScreen(
    controller: MonitorController,
    onPair: () -> Unit,
    onOpenPairingOptions: () -> Unit,
    onOpenWirelessDebugging: () -> Unit,
    onOpenNetwork: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val method = state.method
    val checkingRoot = method == com.ybblab.jiumi.access.AccessMethod.ROOT && state.root == RootShell.State.CHECKING
    val networkCheck by controller.networkCheck.collectAsStateWithLifecycle()
    val network = method == com.ybblab.jiumi.access.AccessMethod.NETWORK
    val checkingNetwork = network && networkCheck.result == null
    if (network) LaunchedEffect(Unit) { controller.ensureNetworkAdbChecked() }
    val paired = if (network) networkCheck.result is NetworkAdb.Result.Ready else state.ready
    val inFlow = method == com.ybblab.jiumi.access.AccessMethod.WIRELESS && state.pairingState in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE, PairingState.PAIRING,
        PairingState.CONNECTING)
    // Read again whenever the user comes back from the system's pages.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val notifications = remember(refresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(context, Manifest.permission.POST_NOTIFICATIONS)
    }
    // Wireless debugging lives in Developer options. USB debugging alone does not mean the Developer
    // options menu itself is unlocked on all phones, so check DEVELOPMENT_SETTINGS_ENABLED directly.
    val developer = remember(refresh) { isDeveloperOptionsEnabled(context) }
    // A permission the system no longer offers to ask for is granted in the app's settings instead.
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedNotifications = true; refresh++
        if (it) controller.reshowIndicator()
    }

    val shizuku = state.shizuku
    val steps = when (method) {
        com.ybblab.jiumi.access.AccessMethod.NETWORK -> {
            val result = networkCheck.result
            val auth = result as? NetworkAdb.Result.NeedsAuthorization
            val action = when {
                checkingNetwork -> R.string.network_adb_searching
                auth != null -> R.string.action_authorize
                else -> R.string.network_adb_connection_settings
            }
            listOf(Step(R.string.network_adb_setup_step, networkAdbProblem(result) ?: R.string.access_network_desc,
                action, done = paired) {
                if (auth != null) controller.checkNetworkAdb(auth.port, authorize = true)
                else onOpenNetwork()
            })
        }
        com.ybblab.jiumi.access.AccessMethod.ROOT -> listOf(
            Step(R.string.setup_step_root_title, R.string.setup_step_root_desc,
                rootAction(state.root), done = paired) { controller.authorizeRoot() }
        )
        com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> listOf(
            Step(R.string.setup_step_shizuku_run_title, R.string.setup_step_shizuku_run_desc,
                if (!shizuku.installed && !shizuku.running) R.string.action_get_shizuku else R.string.action_open_shizuku,
                done = paired || (shizuku.running && shizuku.supportedVersion && shizuku.usableIdentity)) {
                if (!shizuku.installed && !shizuku.running) controller.access.shizuku.openDownload()
                else controller.access.shizuku.openManager()
            },
            Step(R.string.setup_step_shizuku_auth_title, R.string.setup_step_shizuku_auth_desc,
                if (shizuku.permissionBlocked) R.string.action_open_shizuku else R.string.action_authorize,
                done = paired) {
                if (shizuku.permissionBlocked) controller.access.shizuku.openManager() else controller.authorizeShizuku()
            }
        )
        com.ybblab.jiumi.access.AccessMethod.WIRELESS -> listOf(
            Step(R.string.setup_step_notify_title, R.string.setup_step_notify_desc,
                if (askedNotifications) R.string.setup_step_notify_settings else R.string.setup_step_notify_action,
                done = notifications || paired) {
                if (askedNotifications || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) openAppSettings(context)
                else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            Step(R.string.setup_step_developer_title, R.string.setup_step_developer_desc,
                R.string.setup_step_developer_action, done = developer || paired) {
                openAboutPhone(context)
            },
            Step(R.string.setup_step_pair_title, R.string.setup_step_pair_desc,
                if (inFlow) R.string.action_open_wireless_debugging else R.string.action_start_pairing, done = paired) {
                if (inFlow) onOpenWirelessDebugging() else onPair()
            }
        )
    }
    val current = steps.indexOfFirst { !it.done }
    // A step tapped open stays open; otherwise the step at hand is.
    var opened by rememberSaveable { mutableStateOf<Int?>(null) }
    val expanded = opened?.takeIf { it < steps.size } ?: current

    var comparing by rememberSaveable { mutableStateOf(false) }
    if (comparing) MethodComparison(onClose = { comparing = false })

    SubScreen(
        "", onBack = null,
        bottomBar = {
            // The privacy line is the page's last line, under the action; the column takes the
            // navigation bar inset once for both.
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                if (current < 0) BottomAction(stringResource(R.string.setup_start), onClick = onDone)
                else BottomAction(stringResource(steps[current].action), enabled = !checkingRoot && !checkingNetwork) {
                    opened = null; steps[current].run()
                }
                PrivacyNote()
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
        ) {
            item { Brand() }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.setup_method_title), style = MaterialTheme.typography.titleLarge,
                        color = Ink.primary, modifier = Modifier.weight(1f))
                    Icon(JiumiIcons.Info, stringResource(R.string.setup_method_compare), tint = Ink.secondary,
                        modifier = Modifier.clip(CircleShape).clickable { comparing = true }.padding(6.dp).size(18.dp))
                }
                Text(stringResource(R.string.setup_method_note), style = MaterialTheme.typography.bodySmall, color = Ink.secondary,
                    modifier = Modifier.padding(top = 2.dp))
                Column(Modifier.padding(top = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.ybblab.jiumi.access.AccessMethod.entries.forEach { m ->
                        MethodCard(m, selected = m == method) {
                            opened = null
                            controller.selectAccess(m)
                        }
                    }
                }
            }
            item {
                Text(
                    if (current < 0) stringResource(R.string.setup_all_done)
                    else stringResource(R.string.setup_next, steps.count { it.done }, steps.size),
                    style = MaterialTheme.typography.labelMedium, color = Ink.tertiary,
                    modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)
                )
            }
            item {
                Column(Modifier.animateContentSize()) {
                    steps.forEachIndexed { i, step ->
                        StepItem(
                            index = i,
                            step = step,
                            status = when {
                                step.done -> StepStatus.DONE
                                i == current -> StepStatus.CURRENT
                                else -> StepStatus.LATER
                            },
                            expanded = i == expanded,
                            previousDone = i > 0 && steps[i - 1].done,
                            last = i == steps.lastIndex,
                            onToggle = { opened = if (expanded == i) -1 else i }
                        ) {
                            // The last step tells how pairing is going while it runs.
                            if (i == steps.lastIndex && !paired) PairingProgress(state.pairingState, inFlow,
                                onCancel = controller::cancelPairing)
                            if (method == com.ybblab.jiumi.access.AccessMethod.ROOT) {
                                rootError(state.root)?.let { error ->
                                    Text(stringResource(error), style = MaterialTheme.typography.bodySmall,
                                        color = StatusWarning, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            if (!paired && method == com.ybblab.jiumi.access.AccessMethod.SHIZUKU && i == current) {
                                statusMessage(state)?.let { message ->
                                    Text(message, style = MaterialTheme.typography.bodySmall,
                                        color = StatusWarning, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            if (i == steps.lastIndex && method == com.ybblab.jiumi.access.AccessMethod.WIRELESS) state.pairingFailure?.let {
                                Text(stringResource(it.messageRes()), style = MaterialTheme.typography.bodySmall,
                                    color = StatusWarning, modifier = Modifier.padding(top = 8.dp))
                            }
                            if (i == steps.lastIndex && method == com.ybblab.jiumi.access.AccessMethod.WIRELESS && !paired) {
                                OtherWayEntry(onOpenPairingOptions)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The way out when the notification can't take the code: a quiet outlined row that still reads as a button. */
@Composable
private fun OtherWayEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = AppShapes.Control,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(32.dp).background(Ink.fill, AppShapes.Small), contentAlignment = Alignment.Center) {
                Icon(JiumiIcons.SplitScreen, null, Modifier.size(18.dp), tint = Ink.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pairing_other_way), style = MaterialTheme.typography.labelLarge,
                    color = Ink.primary)
                Text(stringResource(R.string.pairing_other_way_desc), style = MaterialTheme.typography.bodySmall,
                    color = Ink.secondary)
            }
            Icon(JiumiIcons.ChevronRight, null, Modifier.size(18.dp), tint = Ink.tertiary)
        }
    }
}

private class Step(
    val title: Int,
    val desc: Int,
    val action: Int,
    val done: Boolean,
    val run: () -> Unit
)

private enum class StepStatus { DONE, CURRENT, LATER }

/** The app's icon, its name, and in a line what it is for: small, at the top left, so the choice below leads. */
@Composable
private fun Brand() {
    val icon = rememberAppIcon()
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Image(it, null, Modifier.size(56.dp).clip(AppShapes.Card)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Ink.primary)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyMedium, color = Ink.secondary,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** One way to start the monitor: what it is called and, in a line, what it needs. The chosen one is outlined. */
@Composable
private fun MethodCard(method: com.ybblab.jiumi.access.AccessMethod, selected: Boolean, onSelect: () -> Unit) {
    val icon = when (method) {
        com.ybblab.jiumi.access.AccessMethod.WIRELESS -> JiumiIcons.Wifi
        com.ybblab.jiumi.access.AccessMethod.NETWORK -> JiumiIcons.Network
        com.ybblab.jiumi.access.AccessMethod.ROOT -> JiumiIcons.Shield
        com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> JiumiIcons.Smartphone
    }
    val hint = when (method) {
        com.ybblab.jiumi.access.AccessMethod.WIRELESS -> R.string.access_wireless_hint
        com.ybblab.jiumi.access.AccessMethod.NETWORK -> R.string.access_network_hint
        com.ybblab.jiumi.access.AccessMethod.ROOT -> R.string.access_root_hint
        com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> R.string.access_shizuku_hint
    }
    val edge by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, label = "methodEdge")
    Surface(
        shape = AppShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, edge),
        modifier = Modifier.fillMaxWidth().clip(AppShapes.Card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Ink.fill, AppShapes.Control), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Ink.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        SelectionDot()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(accessName(method), style = MaterialTheme.typography.titleMedium, color = Ink.primary)
                    // Wireless debugging needs nothing else installed, so it is the one to suggest.
                    if (method == com.ybblab.jiumi.access.AccessMethod.WIRELESS) Text(
                        stringResource(R.string.setup_recommended), style = MaterialTheme.typography.labelMedium, color = Ink.secondary,
                        modifier = Modifier.padding(start = 8.dp).background(Ink.fill, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = Ink.secondary,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** What each way does in full, kept off the page until asked for. */
@Composable
private fun MethodComparison(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_done)) } },
        title = { Text(stringResource(R.string.setup_method_compare), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                com.ybblab.jiumi.access.AccessMethod.entries.forEach { m ->
                    Column {
                        Text(accessName(m), style = MaterialTheme.typography.titleMedium, color = Ink.primary)
                        Text(accessDescription(m), style = MaterialTheme.typography.bodySmall, color = Ink.secondary,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    )
}

/**
 * One step on a line: a numbered dot (a check once done), joined to the next by a rule. Only the
 * open step says what to do; the button for it is at the foot of the page.
 */
@Composable
private fun StepItem(
    index: Int,
    step: Step,
    status: StepStatus,
    expanded: Boolean,
    previousDone: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    extra: @Composable () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val doneLine = ink.copy(alpha = 0.25f)
    // Each stretch of the rule takes the colour of the step it leaves from.
    val lineAbove = if (previousDone) doneLine else Ink.track
    val lineBelow = if (status == StepStatus.DONE) doneLine else Ink.track
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(AppShapes.Control).clickable(onClick = onToggle)
            .padding(horizontal = 4.dp)
    ) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The rule runs unbroken from dot to dot, darker along the steps already done.
            Box(Modifier.width(1.5.dp).height(8.dp).background(if (index == 0) Color.Transparent else lineAbove))
            Box(
                Modifier.size(24.dp).background(if (status == StepStatus.CURRENT) ink else Ink.fill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (status) {
                    StepStatus.DONE -> Icon(JiumiIcons.Check, null, tint = ink, modifier = Modifier.size(13.dp))
                    StepStatus.CURRENT -> Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.surface)
                    StepStatus.LATER -> Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium,
                        color = Ink.tertiary)
                }
            }
            if (!last) Box(Modifier.width(1.5.dp).weight(1f).background(lineBelow))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 10.dp, bottom = if (last) 8.dp else 14.dp)) {
            Text(stringResource(step.title),
                style = if (status == StepStatus.CURRENT) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (status == StepStatus.CURRENT) Ink.primary else Ink.secondary)
            if (expanded) {
                Text(stringResource(step.desc), style = MaterialTheme.typography.bodySmall, color = Ink.secondary,
                    modifier = Modifier.padding(top = 4.dp))
                extra()
            }
        }
    }
}

/** While pairing runs: what to do now on the system's page, and a way to stop. */
@Composable
private fun PairingProgress(pairing: PairingState, inFlow: Boolean, onCancel: () -> Unit) {
    if (!inFlow) return
    val text = when (pairing) {
        PairingState.PAIRING -> R.string.pairing_in_progress
        PairingState.CONNECTING -> R.string.pairing_connecting
        else -> R.string.pairing_hint
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).background(Ink.fill, AppShapes.Control)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (pairing == PairingState.AWAITING_CODE) Icon(JiumiIcons.Notifications, null, tint = Ink.primary,
            modifier = Modifier.size(16.dp))
        else CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Ink.secondary)
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = Ink.primary,
            modifier = Modifier.weight(1f))
        if (pairing == PairingState.DISCOVERING || pairing == PairingState.AWAITING_CODE) Text(stringResource(R.string.action_cancel),
            style = MaterialTheme.typography.labelLarge, color = Ink.secondary,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onCancel).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }
}
