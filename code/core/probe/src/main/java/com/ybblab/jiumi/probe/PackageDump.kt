// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Locale

internal class PackageDump(
    val firstInstall: Map<String, Long>,
    val userStates: Map<String, UserState>,
    val packages: Map<String, Entry>
) {
    data class Entry(
        var appId: Int? = null,
        var flags: Set<String>? = null,
        var sharedUser: String? = null,
        var codePath: String? = null
    ) {
        val system get() = flags?.let { "SYSTEM" in it || "UPDATED_SYSTEM_APP" in it }
        val hasCode get() = flags?.contains("HAS_CODE")
        val persistent get() = flags?.contains("PERSISTENT")
    }

    data class UserState(val installed: Boolean, val enabled: Int?, val suspended: Boolean?, val suspendedByShell: Boolean = false) {
        val disabledByUser get() = installed && enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    }

    data class PermissionGrant(val allowed: Boolean, val flags: Set<String>)

    data class Permissions(
        val requested: List<String>,
        val installed: Map<String, PermissionGrant>,
        val runtime: Map<String, PermissionGrant>
    )

    companion object {
        private val userLine = Regex("User (\\d+): .*\\binstalled=(true|false)\\b.*")
        private val enabled = Regex("\\benabled=(\\d+)\\b")
        private val suspended = Regex("\\bsuspended=(true|false)\\b")
        private const val PROCESS_HEADER = "ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)"
        private const val PER_USER_RANGE = 100_000
        private val processEntry = Regex("^\\*[A-Z]+\\* UID (\\d+) ProcessRecord\\{\\S+ (\\d+):")
        private val permissionName = Regex("""[A-Za-z0-9_.]+""")
        private val grantLine = Regex("""([A-Za-z0-9_.]+): granted=(true|false)(?:, flags=\[(.*)])?""")

        /** Reads the selected package's declared permissions and the grants in effect for its Android user. */
        fun permissions(dump: String, pkg: String, appId: Int, user: Int): Permissions? {
            val lines = dump.lines()
            val packageBlock = lines.section { it == "Packages:" }
                ?.section { it.startsWith("Package [$pkg] (") } ?: return null
            val packageUser = packageBlock.section {
                val fields = userLine.matchEntire(it)?.groupValues ?: return@section false
                fields[1].toIntOrNull() == user && fields[2] == "true"
            } ?: return null

            // Android lists runtime permissions held by a shared UID under the shared app ID.
            val sharedUsers = lines.section { it == "Shared users:" }.orEmpty()
            val sharedUser = sharedUsers.map(String::trim).filter { it.startsWith("SharedUser [") }
                .mapNotNull { header -> sharedUsers.section { it == header } }
                .singleOrNull { section -> section.any { it.trim() == "appId=$appId" } }
            val runtimeUser = if (sharedUser == null) packageUser else sharedUser.section { it == "User $user:" }.orEmpty()
            val requested = packageBlock.section { it == "requested permissions:" }.orEmpty()
                .map(String::trim).filter(permissionName::matches).distinct()
            return Permissions(
                requested,
                readGrants(packageBlock.section { it == "install permissions:" }.orEmpty()),
                readGrants(runtimeUser.section { it == "runtime permissions:" }.orEmpty())
            )
        }

        private fun readGrants(lines: List<String>): Map<String, PermissionGrant> =
            lines.mapNotNull { line ->
                val (name, allowed, flags) = grantLine.matchEntire(line.trim())?.destructured ?: return@mapNotNull null
                name to PermissionGrant(allowed == "true", flags.split('|').map(String::trim).filter(String::isNotEmpty).toSet())
            }.toMap()

        private fun List<String>.section(heading: (String) -> Boolean): List<String>? {
            val start = indexOfFirst { heading(it.trim()) }
            if (start < 0) return null
            val indent = this[start].indexOfFirst { !it.isWhitespace() }
            return subList(start + 1, size).takeWhile { line ->
                line.isBlank() || line.indexOfFirst { !it.isWhitespace() } > indent
            }
        }

        /** Activity Manager filters this dump by package membership, including custom process names. */
        fun processRunning(dump: String, user: Int): Boolean? {
            // Some builds print a timestamp before the Activity Manager header.
            val lines = dump.lineSequence().dropWhile { it.trim() != PROCESS_HEADER }.iterator()
            if (!lines.hasNext()) return null
            lines.next()
            var unreadable = false
            for (line in lines) {
                val entry = line.trimStart()
                if (!entry.startsWith('*') || "ProcessRecord{" !in entry) continue
                val match = processEntry.find(entry)
                val uid = match?.groupValues?.get(1)?.toIntOrNull()
                val pid = match?.groupValues?.get(2)?.toIntOrNull()
                if (uid == null || pid == null) unreadable = true
                else if (uid / PER_USER_RANGE == user && pid > 0) return true
            }
            return if (unreadable) null else false
        }

        /** Reads the current package entries, excluding the separate copies of updated system apps. */
        fun parse(dump: String, user: Int): PackageDump {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val times = HashMap<String, Long>()
            val states = HashMap<String, UserState>()
            val entries = LinkedHashMap<String, Entry>()
            var packages = false
            var pkg: String? = null
            var targetUser = false
            for (line in dump.lineSequence()) {
                val t = line.trim()
                if (t.isEmpty()) continue
                if (!line.first().isWhitespace()) {
                    packages = t == "Packages:"
                    pkg = null
                    targetUser = false
                }
                if (!packages) continue
                if (t.startsWith("Package [")) {
                    pkg = t.substringAfter('[').substringBefore(']')
                    entries.getOrPut(pkg) { Entry() }
                    targetUser = false
                    continue
                }
                val name = pkg ?: continue
                val entry = entries.getValue(name)
                val userMatch = if (t.startsWith("User ")) userLine.matchEntire(t) else null
                when {
                    t.startsWith("appId=") || t.startsWith("userId=") ->
                        entry.appId = t.substringAfter('=').toIntOrNull()
                    t.startsWith("pkgFlags=[") && t.endsWith(']') ->
                        entry.flags = t.substringAfter('[').substringBeforeLast(']').split(' ').filter(String::isNotBlank).toSet()
                    t.startsWith("sharedUser=SharedUserSetting{") ->
                        entry.sharedUser = t.substringAfter(' ', "").substringBefore('/').takeIf(String::isNotBlank)
                    t.startsWith("codePath=") -> entry.codePath = t.substringAfter('=').takeIf(String::isNotBlank)
                    userMatch != null -> {
                        targetUser = userMatch.groupValues[1].toIntOrNull() == user
                        if (!targetUser) continue
                        val setting = enabled.find(t)?.groupValues?.get(1)?.toIntOrNull()
                        val suspension = suspended.find(t)?.groupValues?.get(1)?.toBooleanStrictOrNull()
                        states[name] = UserState(userMatch.groupValues[2] == "true", setting, suspension)
                    }
                    t.startsWith("firstInstallTime=") && name !in times ->
                        runCatching { format.parse(t.substringAfter('='))?.time }.getOrNull()?.let { times[name] = it }
                    targetUser && t.startsWith("suspendingPackage=") -> {
                        // Android prints either the package name or a user-qualified name like <0>com.android.shell.
                        val suspender = t.substringAfter('=').substringBefore(' ').substringAfter('>')
                        if (suspender == "com.android.shell") {
                            states[name]?.takeIf { it.suspended == true }?.let { states[name] = it.copy(suspendedByShell = true) }
                        }
                    }
                }
            }
            return PackageDump(times, states, entries)
        }
    }
}
