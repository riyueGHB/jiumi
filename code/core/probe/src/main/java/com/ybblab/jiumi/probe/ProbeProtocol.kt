// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

/** Contract shared by the app and the shell-identity monitor started from the same APK. */
object ProbeProtocol {
    const val VERSION = 24
    const val STOP_PARTIAL_TRACE = "partial_trace"
    const val SHELL_UID = 2000
    const val MAIN_CLASS = "com.ybblab.jiumi.probe.ProbeMain"
    const val PROCESS_NAME = "jiumi_probe"
    const val ACTION_READY = "com.ybblab.jiumi.action.PROBE_READY"
    /** Only holders of DUMP (the shell) may deliver the monitor binder. */
    const val SENDER_PERMISSION = "android.permission.DUMP"
    const val EXTRA_BINDER = "probe_binder"
    const val EXTRA_UID = "probe_uid"
    const val EXTRA_PROTOCOL = "probe_protocol"
    const val EXTRA_SOURCE = "probe_source"
    const val EXTRA_UPDATED_AT = "probe_updated_at"

    fun dataDirectory(packageName: String): String = "/data/local/tmp/$packageName"
}

/** Machine-readable reasons returned by package management commands. */
enum class PackageChangeFailure {
    PERMISSION_DENIED, NOT_APPLIED, UNVERIFIED;

    companion object {
        private val permissionDenial = Regex(
            """^(?:Error:\s*)?(?:(?:java\.lang\.)?Security\s*Exception|Permission Denial|Permission denied)(?::|\.|$)""",
            RegexOption.IGNORE_CASE
        )
        private const val ROOT_ONLY_REMOVAL = "Failure [only root can delete system app for a particular user]"

        fun fromCommandOutput(output: String): PackageChangeFailure? = PERMISSION_DENIED.takeIf {
            output.lineSequence().any { line ->
                val message = line.trim()
                message.equals(ROOT_ONLY_REMOVAL, ignoreCase = true) || permissionDenial.containsMatchIn(message)
            }
        }
    }
}
