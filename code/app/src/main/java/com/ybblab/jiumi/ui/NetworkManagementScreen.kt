// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.AppKey
import com.ybblab.jiumi.ui.icons.JiumiIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NetworkManagementScreen(
    controller: MonitorController, labels: AppLabels, onOpenApp: (AppKey) -> Unit, onBack: () -> Unit
) {
    val management = controller.networkManagement
    val policy by management.state.collectAsStateWithLifecycle()
    val autoRestore = policy.restoreEnabledAtBoot != null
    val boot = management.boot
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val keys = remember(policy.apps) { policy.apps.values.map { it.key } }
    val names by produceState(emptyMap<AppKey, String>(), keys, labels) {
        value = withContext(Dispatchers.IO) { keys.associateWith(labels::label) }
    }
    val search = query.trim()
    val apps = remember(policy.apps, names, search) {
        policy.apps.values.filter { it.pkg.contains(search, ignoreCase = true) ||
            names[it.key]?.contains(search, ignoreCase = true) == true }
            .sortedBy { (names[it.key] ?: it.pkg).lowercase() }
    }
    SubScreen(stringResource(R.string.network_management_title), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item(key = "restore") {
                val enabled = !busy && (boot != null || autoRestore)
                val toggle: (Boolean) -> Unit = { on ->
                    if (!busy) {
                        busy = true
                        saveFailed = false
                        scope.launch {
                            try {
                                saveFailed = !management.setAutoRestore(on)
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
                Card {
                    ListRow(stringResource(R.string.network_management_restore),
                        leading = { IconTile(JiumiIcons.Network) },
                        onClick = if (enabled) ({ toggle(!autoRestore) }) else null) {
                        JiumiSwitch(autoRestore, enabled, toggle)
                    }
                }
                GroupFooter(stringResource(R.string.network_management_restore_desc))
                if (saveFailed) GroupFooter(stringResource(R.string.network_management_save_error), warning = true)
                Spacer(Modifier.height(12.dp))
            }
            if (policy.apps.isNotEmpty()) item(key = "search") {
                SearchField(query) { query = it }
                Spacer(Modifier.height(12.dp))
            }
            if (apps.isEmpty()) item(key = "empty") {
                EmptyCard(stringResource(if (policy.apps.isEmpty()) R.string.network_management_empty else R.string.apps_empty_search))
            }
            itemsIndexed(apps, key = { _, app -> app.pkg }) { index, app ->
                val name = names[app.key] ?: app.pkg
                CardSlice(first = index == 0, last = index == apps.lastIndex) {
                    if (index > 0) InsetDivider()
                    ListRow(name, subtitle = app.pkg.takeIf { it != name }, leading = { AppIcon(labels, app.pkg, LEADING_SIZE) },
                        onClick = { onOpenApp(app.key) }) {
                        Chevron()
                    }
                }
            }
        }
    }
}
