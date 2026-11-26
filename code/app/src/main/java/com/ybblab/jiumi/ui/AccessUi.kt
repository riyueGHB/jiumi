// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.access.AccessMethod
import com.ybblab.jiumi.access.RootShell
import com.ybblab.jiumi.access.ShizukuState

@Composable
fun accessName(method: AccessMethod): String = stringResource(when (method) {
    AccessMethod.WIRELESS -> R.string.access_wireless
    AccessMethod.NETWORK -> R.string.access_network_debugging
    AccessMethod.ROOT -> R.string.access_root
    AccessMethod.SHIZUKU -> R.string.access_shizuku
})

@Composable
fun accessDescription(method: AccessMethod): String = stringResource(when (method) {
    AccessMethod.WIRELESS -> R.string.access_wireless_desc
    AccessMethod.NETWORK -> R.string.access_network_desc
    AccessMethod.ROOT -> R.string.access_root_desc
    AccessMethod.SHIZUKU -> R.string.access_shizuku_desc
})

fun rootStatus(state: RootShell.State): Int = when (state) {
    RootShell.State.UNCHECKED -> R.string.root_unchecked
    RootShell.State.CHECKING -> R.string.root_checking
    RootShell.State.UNAVAILABLE -> R.string.root_not_granted
    RootShell.State.SHELL_FAILED -> R.string.root_shell_failed
    RootShell.State.READY -> R.string.root_granted
}

fun rootError(state: RootShell.State): Int? = when (state) {
    RootShell.State.UNAVAILABLE -> R.string.error_root
    RootShell.State.SHELL_FAILED -> R.string.error_root_shell
    else -> null
}

fun rootAction(state: RootShell.State): Int = when (state) {
    RootShell.State.CHECKING -> R.string.root_checking
    RootShell.State.SHELL_FAILED -> R.string.root_retry
    else -> R.string.action_authorize
}

/** Where Shizuku stands, in the order the user has to fix it. */
@Composable
fun shizukuStatus(state: ShizukuState): String = stringResource(when {
    state.ready -> R.string.shizuku_ready
    !state.installed && !state.running -> R.string.shizuku_not_installed
    !state.running -> R.string.shizuku_not_connected
    !state.supportedVersion -> R.string.shizuku_old
    !state.usableIdentity -> R.string.shizuku_identity
    state.permissionBlocked -> R.string.shizuku_blocked
    else -> R.string.shizuku_needs_auth
})

/** The one thing to do next for Shizuku: get it, open it to start it, or allow this app. */
fun shizukuNextAction(state: ShizukuState): Int = when {
    !state.installed && !state.running -> R.string.action_get_shizuku
    !state.running || !state.supportedVersion || !state.usableIdentity || state.permissionBlocked -> R.string.action_open_shizuku
    else -> R.string.action_authorize
}

fun runShizukuNextAction(controller: MonitorController, state: ShizukuState) {
    val shizuku = controller.access.shizuku
    when (shizukuNextAction(state)) {
        R.string.action_get_shizuku -> shizuku.openDownload()
        R.string.action_open_shizuku -> shizuku.openManager()
        else -> controller.authorizeShizuku()
    }
}

const val KEY_WIRELESS_DEBUGGING = "toggle_adb_wireless"
const val KEY_USB_DEBUGGING = "enable_adb"
private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
private const val ACTION_LEGACY_DEVELOPMENT_SETTINGS = "com.android.settings.APPLICATION_DEVELOPMENT_SETTINGS"

fun isDeveloperOptionsEnabled(context: Context): Boolean =
    runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }
        .getOrDefault(false)

fun openAboutPhone(context: Context): Boolean =
    startSettings(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) ||
        startSettings(context, Intent(Settings.ACTION_SETTINGS))

/**
 * Opens Developer options (highlighting [highlightKey] where the ROM supports it) without touching
 * SubSettings/WirelessDebuggingFragment directly, which renders blank on several OEM skins.
 */
fun openDeveloperOptions(
    context: Context,
    highlightKey: String = KEY_WIRELESS_DEBUGGING,
    adjacent: Boolean = false
) {
    // In split screen, Settings takes the other half so Jiumi stays beside it.
    val extra = if (adjacent) Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT else 0
    val localized = ContextCompat.getContextForLanguage(context)
    if (!isDeveloperOptionsEnabled(context)) {
        openAboutPhone(context)
        Toast.makeText(localized, localized.getString(R.string.toast_enable_developer_first), Toast.LENGTH_LONG).show()
        return
    }
    if (startDeveloperIntent(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, highlightKey, extra) ||
        startDeveloperIntent(context, ACTION_LEGACY_DEVELOPMENT_SETTINGS, highlightKey, extra) ||
        startSettings(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), extra)) {
        return
    }
    startSettings(context, Intent(Settings.ACTION_SETTINGS))
    Toast.makeText(localized, localized.getString(R.string.toast_open_developer_manually), Toast.LENGTH_LONG).show()
}

private fun startDeveloperIntent(context: Context, action: String, highlightKey: String, flags: Int = 0): Boolean {
    val args = Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, highlightKey) }
    val intent = Intent(action)
        .putExtra(EXTRA_FRAGMENT_ARG_KEY, highlightKey)
        .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, args)
    return startSettings(context, intent, flags)
}

private fun startSettings(context: Context, intent: Intent, flags: Int = 0): Boolean = runCatching {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or flags))
    true
}.getOrDefault(false)
