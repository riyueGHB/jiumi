// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

/**
 * Shell commands that start and stop the monitor. The monitor runs from the installed APK as
 * the shell user and is detached from the ADB session, so it keeps running after ADB closes.
 */
object ProbeLaunch {
    private val packagePattern = Regex("[A-Za-z0-9_.]+")
    private const val LOG_ROTATE_BYTES = 262_144
    private const val RESTART_DELAY_SECONDS = 5

    /**
     * A small supervisor restarts the monitor after a crash. Exit code 0 means an intentional stop,
     * a duplicate, or a changed APK, and ends the supervisor. It also ends when the APK is gone or
     * developer options are turned off, so the user's switch always wins.
     */
    fun startCommand(apkPath: String, packageName: String, config: String): String {
        require(packagePattern.matches(packageName)) { "invalid package" }
        val dir = ProbeProtocol.dataDirectory(packageName)
        val log = "$dir/probe.log"
        val supervisor = "while :; do " +
            "app_process /system/bin --nice-name=${ProbeProtocol.PROCESS_NAME} ${ProbeProtocol.MAIN_CLASS} " +
            "$packageName ${ProbeProtocol.VERSION} \"\$CLASSPATH\"; " +
            "rc=\$?; echo \"\$(date +%s) supervisor: monitor exited rc=\$rc\"; " +
            "[ \$rc -eq 0 ] && break; " +
            "sleep $RESTART_DELAY_SECONDS; " +
            "[ -f \"\$CLASSPATH\" ] || break; " +
            "[ \"\$(settings get global development_settings_enabled 2>/dev/null)\" != 0 ] || break; " +
            "done"
        return buildString {
            append("export CLASSPATH=${quote(apkPath)}; ")
            append("umask 077; mkdir -p $dir && chmod 700 $dir || exit 1; ")
            // The probe restores this before starting any watchers, including on a supervisor restart.
            append("config_tmp=\$(mktemp $dir/.launch-config.XXXXXX) || exit 1; ")
            append("printf '%s' ${quote(config)} > \"\$config_tmp\" && mv -f \"\$config_tmp\" $dir/config.json || exit 1; ")
            append("[ -f $log ] && [ \"\$(wc -c <$log 2>/dev/null || echo 0)\" -gt $LOG_ROTATE_BYTES ] && mv -f $log $log.1; ")
            // setsid detaches from the one-shot ADB shell so closing ADB does not hang it up.
            append("(setsid sh -c ${quote(supervisor)} </dev/null >>$log 2>&1 &); ")
            // Return only once the monitor exists. If the shell returned at once, the ADB session
            // would close and could take the background job down before it detached (Android 16).
            append("i=0; until pidof ${ProbeProtocol.PROCESS_NAME} >/dev/null; do ")
            append("[ \$i -lt 50 ] || exit 2; sleep 0.1; i=\$((i+1)); done; exit 0")
        }
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
