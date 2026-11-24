// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.AppKey
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.ui.theme.StatusWarning
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class PermissionRow(
    val name: String, val label: String, val group: String, val icon: ImageVector,
    val allowed: Boolean?, val editable: Boolean, val role: Boolean
)

/** An app op from rules/app_ops.json; [permission] is what the app must request for it to be listed. */
private data class OpRule(val op: String, val permission: String?, val label: String, val icon: String)

/** What one access page manages: the app's permissions and app ops, or its network access. */
enum class AccessKind(val read: String) { PERMISSIONS("permissions"), NETWORK("network") }

@Composable
fun AppAccessScreen(controller: MonitorController, labels: AppLabels, key: AppKey, kind: AccessKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val monitor by controller.state.collectAsStateWithLifecycle()
    val single = kind != AccessKind.PERMISSIONS
    val readAction = kind.read
    var state by remember(key, kind) { mutableStateOf<JSONObject?>(null) }
    var pending by remember(key, kind) { mutableStateOf(true) }
    var lastAction by remember(key, kind) { mutableStateOf(readAction to "") }
    fun refresh(action: String, permission: String = "") {
        if (pending) return
        pending = true
        lastAction = action to permission
        scope.launch {
            state = controller.appAccess(key, action, permission)
            pending = false
        }
    }
    // App ops are not permissions, so they are read and changed on their own, once the permissions say
    // which of them this app can use.
    val opRules = remember(context) {
        val list = JSONObject(context.assets.open("rules/app_ops.json").bufferedReader().use { it.readText() }).getJSONArray("ops")
        List(list.length()) { i ->
            val o = list.getJSONObject(i)
            OpRule(o.getString("op"), o.optString("permission").ifEmpty { null }, o.getString("label"), o.getString("icon"))
        }
    }
    var ops by remember(key, kind) { mutableStateOf<JSONObject?>(null) }
    var opsPending by remember(key, kind) { mutableStateOf(kind == AccessKind.PERMISSIONS) }
    fun readOps(permissions: JSONObject?) {
        val requested = permissions?.optJSONArray("rows")?.let { a -> List(a.length()) { a.getJSONObject(it).getString("name") }.toSet() }
        val names = opRules.filter { it.permission == null || requested?.contains(it.permission) == true }.map { it.op }
        scope.launch {
            ops = if (requested == null || names.isEmpty()) null else controller.appAccess(key, "ops", names.joinToString(","))
            opsPending = false
        }
    }
    fun changeOp(op: String, allow: Boolean) {
        if (opsPending) return
        opsPending = true
        scope.launch {
            val result = controller.appAccess(key, if (allow) "opAllow" else "opDeny", op)
            val changed = result?.optJSONArray("ops")?.optJSONObject(0)
            val current = ops?.optJSONArray("ops")
            if (changed != null && current != null) {
                val merged = JSONArray(List(current.length()) { i ->
                    current.getJSONObject(i).let { if (it.getString("op") == op) changed else it }
                })
                ops = JSONObject(ops.toString()).put("ops", merged).put("lock", result.opt("lock"))
                    .put("failed", result.optJSONArray("failed") ?: JSONArray())
            } else {
                ops = ops?.let { JSONObject(it.toString()).put("failed", JSONArray().put(op)) }
            }
            opsPending = false
        }
    }
    LaunchedEffect(key, kind, monitor.running) {
        state = controller.appAccess(key, readAction)
        pending = false
        if (kind == AccessKind.PERMISSIONS) readOps(state)
    }
    val lock = state?.optString("lock").orEmpty()
    val groups = remember(context) {
        val rules = JSONObject(context.assets.open("rules/permission_groups.json").bufferedReader().use { it.readText() })
        buildMap {
            for (group in rules.keys()) {
                val names = rules.getJSONArray(group)
                repeat(names.length()) { put(names.getString(it), group) }
            }
        }
    }
    val rows = remember(state, context, groups) {
        val pm = context.packageManager
        val array = state?.optJSONArray("rows") ?: JSONArray()
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            val name = item.getString("name")
            val info = runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()
            val label = info?.loadLabel(pm)?.toString() ?: name
            val editable = item.optBoolean("editable")
            val category = groups[name]
            val fallback = context.getString(if (editable) R.string.access_group_other else R.string.access_group_locked)
            val labelId = category?.let {
                @Suppress("DiscouragedApi")
                context.resources.getIdentifier("access_group_$it", "string", context.packageName)
            } ?: 0
            val group = if (editable && labelId != 0) context.getString(labelId) else fallback
            val icon = when (category) {
                "location" -> JiumiIcons.MapPin
                "camera" -> JiumiIcons.Camera
                "contacts" -> JiumiIcons.Person
                "media" -> JiumiIcons.Image
                "notifications" -> JiumiIcons.Notifications
                "phone" -> JiumiIcons.Phone
                "messages" -> JiumiIcons.MessageCheck
                "calendar" -> JiumiIcons.Calendar
                "nearby" -> JiumiIcons.Link
                "health", "microphone" -> JiumiIcons.Activity
                else -> JiumiIcons.Lock
            }
            PermissionRow(name, label, group, icon, item.takeUnless { it.isNull("allowed") }?.optBoolean("allowed"), editable,
                item.optBoolean("role"))
        }
    }
    val enabled = rows.filter { it.editable && it.allowed == true }
    val editable = rows.filter { it.editable }
    val canRestore = state?.optBoolean("restore") == true
    val permissionFailures = state?.optJSONArray("failed")
    val failedNames = permissionFailures?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty()
    val switchFailed = single && state?.optBoolean("failed") == true
    val peers = state?.optJSONArray("peers")?.let { a -> List(a.length(), a::getString) }.orEmpty()
        .joinToString(stringResource(R.string.list_separator))
    val warn = state?.optJSONArray("warn")?.let { a -> List(a.length(), a::getString) }.orEmpty()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(JiumiIcons.ArrowBack, stringResource(R.string.action_back), tint = Ink.primary)
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(stringResource(if (single) R.string.access_network else R.string.access_permissions),
                style = MaterialTheme.typography.headlineSmall, color = Ink.primary)
            Text(labels.label(key), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Ink.primary)
            key.pkg?.let { pkg ->
                Text(pkg, Modifier.copyOnClick(pkg), style = MaterialTheme.typography.bodySmall, color = Ink.secondary)
            }
        }
        Spacer(Modifier.height(20.dp))
        val readable = state != null && (state?.has(if (single) "allowed" else "rows") == true)
        if (lock.isEmpty() && readable) {
            if (!single && "shared" in warn) {
                GroupFooter(stringResource(R.string.access_warn_shared, peers), warning = true)
            } else GroupTitle(stringResource(R.string.access_scope))
        }
        if (single && (readable || lock.isNotEmpty() && lock != "unknown" && lock != "monitor")) {
            val allowed = state?.optBoolean("allowed") == true
            Card {
                ListRow(
                    stringResource(R.string.access_network_allow),
                    subtitle = if (lock.isEmpty()) stringResource(if (allowed) R.string.access_not_blocked else R.string.access_blocked) else null,
                    leading = { IconTile(JiumiIcons.Network) },
                    onClick = if (!pending && lock.isEmpty()) ({ refresh(if (allowed) "networkDeny" else "networkAllow") }) else null
                ) {
                    if (lock.isEmpty()) JiumiSwitch(allowed, enabled = !pending) {
                        refresh(if (it) "networkAllow" else "networkDeny")
                    } else TrailingValue(stringResource(R.string.access_read_only))
                }
            }
            if (lock.isEmpty()) GroupFooter(stringResource(R.string.access_network_note))
        } else if (!single && readable) {
            if (editable.isNotEmpty()) {
                Card {
                    ListRow(
                        stringResource(R.string.access_master),
                        stringResource(when {
                            enabled.isEmpty() -> R.string.access_all_off
                            enabled.size == editable.size -> R.string.access_all_on
                            else -> R.string.access_partial
                        }),
                        leading = { IconTile(JiumiIcons.Lock) },
                        onClick = if (!pending && (enabled.isNotEmpty() || canRestore))
                            ({ refresh(if (enabled.isNotEmpty()) "denyAll" else "restoreAll") }) else null
                    ) {
                        JiumiSwitch(enabled.isNotEmpty(), enabled = !pending && (enabled.isNotEmpty() || canRestore)) {
                            refresh(if (it) "restoreAll" else "denyAll")
                        }
                    }
                }
                if (failedNames.isNotEmpty() && lastAction.first in setOf("denyAll", "restoreAll")) {
                    GroupFooter(stringResource(R.string.access_partial_result, state?.optInt("changed") ?: 0, failedNames.size), warning = true)
                }
            }
            OpsGroup(ops, opRules, opsPending, ::changeOp)
            if (rows.isEmpty()) GroupFooter(stringResource(R.string.access_no_permissions))
            rows.sortedWith(compareBy({ !it.editable }, { it.group }, { it.label })).groupBy { it.editable to it.group }
                .forEach { (group, items) ->
                    GroupTitle(group.second, Modifier.padding(top = 16.dp))
                    Card {
                        items.forEachIndexed { index, row ->
                            if (index > 0) InsetDivider()
                            val allowed = row.allowed
                            val action = if (allowed == true) "deny" else "allow"
                            val failedRow = row.name in failedNames
                            ListRow(row.label,
                                subtitle = when {
                                    failedRow -> stringResource(if (allowed == true) R.string.access_deny_failed else R.string.access_allow_failed)
                                    row.role -> stringResource(R.string.access_granted_by_role)
                                    else -> null
                                },
                                subtitleColor = if (failedRow) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                                leading = { IconTile(row.icon) },
                                onClick = if (row.editable && !pending) ({ refresh(action, row.name) }) else null
                            ) {
                                if (row.editable && allowed != null) {
                                    JiumiSwitch(allowed, enabled = !pending) { refresh(if (it) "allow" else "deny", row.name) }
                                } else TrailingValue(stringResource(if (allowed == null) R.string.access_read_only else
                                    if (allowed) R.string.access_fixed_allowed else R.string.access_fixed_denied))
                            }
                        }
                    }
                }
        }

        when {
            pending -> GroupFooter(stringResource(if (lastAction.first == readAction) R.string.access_reading else R.string.access_applying))
            !readable && (lock.isEmpty() || lock == "unknown") -> {
                Feedback(stringResource(R.string.access_unknown)) { refresh(readAction) }
            }
            lock.isNotEmpty() -> GroupFooter(stringResource(when (lock) {
                "shared" -> R.string.access_lock_shared
                "protected" -> R.string.access_lock_protected
                "users" -> R.string.access_lock_users
                "external" -> R.string.access_lock_external
                "chain" -> R.string.access_lock_chain
                "unsupported" -> R.string.access_lock_unsupported
                "monitor" -> if (single) R.string.access_lock_monitor_network else R.string.access_lock_monitor_permissions
                else -> R.string.access_unknown
            }))
            switchFailed -> Feedback(stringResource(R.string.access_rule_failed)) { refresh(lastAction.first) }
        }
        if (!pending && lock == "shared" && peers.isNotEmpty()) GroupFooter(peers)
        // What expert mode let through: said plainly, since the change reaches past this app.
        if (!pending && lock.isEmpty()) warn.forEach { w ->
            when (w) {
                "shared" -> if (single) GroupFooter(stringResource(R.string.access_warn_shared, peers), warning = true)
                "users" -> GroupFooter(stringResource(R.string.access_warn_users), warning = true)
                "external" -> GroupFooter(stringResource(R.string.access_warn_external), warning = true)
                "chain" -> GroupFooter(stringResource(R.string.access_warn_chain), warning = true)
            }
        }
    }
}

/**
 * The app ops this app can use. Each row shows the mode Android reports and why it is read-only, if applicable.
 */
@Composable
private fun OpsGroup(ops: JSONObject?, rules: List<OpRule>, pending: Boolean, onChange: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val rows = ops?.optJSONArray("ops") ?: return
    if (rows.length() == 0) return
    val targetEditable = ops.optString("lock").isEmpty()
    val failed = ops.optJSONArray("failed")?.let { a -> List(a.length(), a::getString) }.orEmpty()
    val byOp = rules.associateBy { it.op }
    GroupTitle(stringResource(R.string.access_group_ops), Modifier.padding(top = 16.dp))
    Card {
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val op = row.getString("op")
            val rule = byOp[op] ?: continue
            val allowed = row.optBoolean("allowed")
            val mode = row.optString("mode")
            val restriction = when (row.optString("lock")) {
                "uid" -> R.string.appop_lock_uid
                "external" -> R.string.appop_lock_external
                else -> null
            }
            val editable = targetEditable && restriction == null
            @Suppress("DiscouragedApi")
            val labelId = context.resources.getIdentifier("appop_${rule.label}", "string", context.packageName)
            if (i > 0) InsetDivider()
            val failedRow = op in failed && !pending
            ListRow(
                if (labelId != 0) stringResource(labelId) else op,
                subtitle = when {
                    restriction != null -> stringResource(restriction)
                    failedRow -> stringResource(if (allowed) R.string.access_deny_failed else R.string.access_allow_failed)
                    !allowed -> stringResource(R.string.appop_mode_denied)
                    mode == "allow" -> stringResource(R.string.appop_mode_allow)
                    mode == "foreground" -> stringResource(R.string.appop_mode_foreground)
                    else -> stringResource(R.string.appop_mode_default)
                },
                subtitleColor = if (restriction != null || failedRow) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                leading = { IconTile(groupIcon(rule.icon)) },
                onClick = if (editable && !pending) ({ onChange(op, !allowed) }) else null
            ) {
                if (editable) JiumiSwitch(allowed, enabled = !pending) { onChange(op, it) }
                else TrailingValue(stringResource(if (allowed) R.string.access_fixed_allowed else R.string.access_fixed_denied))
            }
        }
    }
    if (targetEditable) GroupFooter(stringResource(R.string.access_ops_note))
}

@Composable
private fun Feedback(message: String, retry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = StatusWarning)
        ActionText(stringResource(R.string.update_retry), onClick = retry)
    }
}
