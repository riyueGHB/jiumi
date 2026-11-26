// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.adb.NetworkAdb
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.Ink

@Composable
internal fun NetworkAdbScreen(
    controller: MonitorController,
    onBack: () -> Unit
) {
    val savedPort by controller.networkAdbPort.collectAsStateWithLifecycle()
    val check by controller.networkCheck.collectAsStateWithLifecycle()
    var manual by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var checkedPort by rememberSaveable { mutableStateOf<Int?>(null) }
    val port = input.toIntOrNull()?.takeIf { it in 1..65535 }
    val busy = check.pending || (!manual && check.result == null)
    val result = check.result.takeIf { !manual || (port != null && port == checkedPort) }
    val ready = result as? NetworkAdb.Result.Ready
    val needsAuth = result as? NetworkAdb.Result.NeedsAuthorization
    val foundPort = ready?.port ?: needsAuth?.port
    LaunchedEffect(Unit) {
        controller.ensureNetworkAdbChecked()
    }

    fun continueConnection() {
        when {
            ready != null -> onBack()
            needsAuth != null -> controller.checkNetworkAdb(needsAuth.port, authorize = true)
            else -> {
                checkedPort = port.takeIf { manual }
                controller.checkNetworkAdb(checkedPort)
            }
        }
    }

    val action = when {
        busy -> R.string.pairing_connecting
        ready != null -> R.string.action_done
        needsAuth != null -> R.string.action_authorize
        manual -> R.string.network_adb_connect
        else -> R.string.network_adb_retry_find
    }

    SubScreen(stringResource(R.string.access_network_debugging), onBack, bottomBar = {
        BottomAction(stringResource(action), enabled = !busy && (!manual || port != null), onClick = ::continueConnection)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.access_network_desc), style = MaterialTheme.typography.bodyMedium, color = Ink.secondary)
            Card {
                ListRow(stringResource(R.string.network_adb_device), leading = { IconTile(JiumiIcons.Smartphone) }) {
                    TrailingValue(stringResource(R.string.network_adb_this_phone))
                }
                InsetDivider()
                ListRow(stringResource(R.string.network_adb_port), leading = { IconTile(JiumiIcons.Network) }) {
                    TrailingValue(when {
                        foundPort != null -> foundPort.toString()
                        manual -> stringResource(R.string.network_adb_manual)
                        result == NetworkAdb.Result.Unavailable -> stringResource(R.string.network_adb_not_found)
                        savedPort != null -> stringResource(R.string.network_adb_last_port, savedPort ?: 0)
                        else -> stringResource(R.string.network_adb_automatic)
                    })
                }
                if (manual) OutlinedTextField(
                    value = input,
                    onValueChange = {
                        if (it.length <= 5 && it.all { digit -> digit in '0'..'9' }) {
                            input = it
                            checkedPort = null
                        }
                    },
                    enabled = !busy,
                    isError = input.isNotEmpty() && port == null,
                    singleLine = true,
                    label = { Text(stringResource(R.string.network_adb_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            if (manual || result == NetworkAdb.Result.Unavailable || result == NetworkAdb.Result.WrongIdentity) {
                TextButton(onClick = {
                    manual = !manual
                    checkedPort = null
                    if (!manual) controller.checkNetworkAdb()
                }, enabled = !busy) {
                    Text(stringResource(if (manual) R.string.network_adb_find_instead else R.string.network_adb_enter_port))
                }
            }
            val problem = networkAdbProblem(result)
            val feedback = problem ?: when {
                ready != null -> R.string.network_adb_verified
                manual && !busy -> R.string.network_adb_manual_hint
                else -> R.string.network_adb_checking
            }
            GroupFooter(stringResource(feedback), warning = problem != null)
            GroupFooter(stringResource(R.string.network_adb_after_reboot))
        }
    }
}

internal fun networkAdbProblem(result: NetworkAdb.Result?): Int? = when (result) {
    is NetworkAdb.Result.NeedsAuthorization -> R.string.network_adb_authorization_needed
    NetworkAdb.Result.Unavailable -> R.string.network_adb_unavailable
    NetworkAdb.Result.WrongIdentity -> R.string.network_adb_wrong_identity
    NetworkAdb.Result.KeyUnavailable -> R.string.network_adb_key_unavailable
    is NetworkAdb.Result.Ready, null -> null
}
