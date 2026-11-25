// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Uses Android's own permission, app-op and UID firewall rules. All writes share one lock.
 *
 * With expert mode the app may also change what affects more than the one app or overrides someone
 * else's rule: a shared UID, other users and profiles, a firewall rule set elsewhere, others' rules in
 * the firewall chain, permissions a role granted. Each such case is named in "warn". Jiumi itself,
 * platform UIDs and persistent apps stay locked even then.
 */
internal class AppAccess(context: Context, directory: File, private val self: String) {
    private val pm = context.packageManager
    private val ledger = AtomicFile(File(directory, "app-access.json"))

    private data class Target(
        val pkg: String, val uid: Int, val user: Int, val lock: String?, val identity: String, val peers: List<String>,
        val expert: Boolean, val warn: Set<String>, val installedAt: Long
    )
    /** An app op's mode for the package and for its whole UID, null where none is set. */
    data class OpState(val pkg: String?, val uid: String?) {
        val packageMode get() = pkg ?: "default"
        val denied get() = pkg in deniedModes || uid in deniedModes
        /** What decides it: a denial wherever set, else the package's own mode, else the UID's, else the default. */
        val mode get() = listOf(uid, pkg).firstOrNull { it in deniedModes }
            ?: pkg?.takeIf { it != "default" } ?: uid ?: "default"

        fun editLock(savedMode: String): String? = when {
            uid in deniedModes -> "uid"
            denied && (pkg != "ignore" || savedMode.isEmpty() || savedMode in deniedModes) -> "external"
            else -> null
        }
    }
    private data class Grant(val name: String, val allowed: Boolean?, val editable: Boolean, val role: Boolean = false)
    private data class Firewall(val enabled: Boolean, val denied: Boolean, val boot: String)

    @Synchronized
    fun run(pkg: String, uid: Int, user: Int, action: String, permission: String, expert: Boolean): JSONObject = runCatching {
        require(action in actions)
        val ops = action.startsWith("op")
        val network = action.startsWith("network")
        if (network && permission.isNotEmpty()) {
            val installedAt = permission.toLong().also { require(it > 0 && action == "networkDeny") }
            if (user != 0) return JSONObject().put("lock", "unknown")
            val installed = try {
                pm.getPackageInfo(pkg, 0)
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                return JSONObject().put("stale", true)
            }
            if (installed.firstInstallTime != installedAt || installed.applicationInfo?.uid != uid) {
                return JSONObject().put("stale", true)
            }
        } else {
            require(permission.isEmpty() || (if (ops) opList else permissionName).matches(permission))
        }
        val target = target(pkg, uid, user, expert) ?: return JSONObject().put("lock", "unknown")
        if (network && permission.isNotEmpty() && target.installedAt.toString() != permission) {
            return JSONObject().put("stale", true)
        }
        val saved = if (ledger.baseFile.exists()) JSONObject(ledger.readFully().decodeToString()) else JSONObject()
        val warn = target.warn.toMutableSet()
        val result = when {
            network -> network(target, saved, action, warn)
            ops -> ops(target, saved, action, permission.split(','))
            else -> permissions(target, saved, action, permission)
        }
        result.put("peers", JSONArray(target.peers)).put("warn", JSONArray(warn.sorted()))
    }.getOrElse { JSONObject().put("lock", "unknown") }

    @Synchronized
    fun ownedNetworkRules(user: Int): JSONArray {
        if (user != 0 || !ledger.baseFile.exists()) return JSONArray()
        val saved = JSONObject(ledger.readFully().decodeToString())
        return JSONArray().apply {
            for (key in saved.keys()) {
                if (!key.startsWith("network:")) continue
                val parts = key.removePrefix("network:").split(':', limit = 3)
                if (parts.size != 3) continue
                val uid = parts[0].toIntOrNull() ?: continue
                val installedAt = parts[1].toLongOrNull() ?: continue
                val pkg = parts[2]
                val recordedBoot = saved.optInt(key, -1)
                if (recordedBoot < 0 || installedAt <= 0 || target(pkg, uid, user, false)?.identity != key.removePrefix("network:")) continue
                val current = checkNotNull(firewall(pkg))
                if (recordedBoot > current.boot.toInt() || recordedBoot.toString() == current.boot && (!current.denied || !current.enabled)) continue
                put(JSONObject().put("pkg", pkg).put("uid", uid).put("installedAt", installedAt)
                    .put("blocked", true).put("verifiedBoot", recordedBoot))
            }
        }
    }

    private fun command(vararg args: String): String? =
        runCatching { Shell.run(args.toList()) }.getOrNull()?.takeIf { it.exitCode == 0 }?.output?.trimEnd()

    private fun target(pkg: String, uid: Int, user: Int, expert: Boolean): Target? {
        if (uid < 0 || uid / USER_RANGE != user) return null
        val listed = command("cmd", "package", "list", "packages", "--user", "$user", "-U", "--uid", "$uid") ?: return null
        val rows = listed.lines().filter(String::isNotBlank).map { packageRow.matchEntire(it.trim()) ?: return null }
        if (rows.none { it.groupValues[1] == pkg && it.groupValues[2] == "$uid" }) return null
        val info = if (user == 0) runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull() else null
        val shared = rows.size != 1
        val lock = when {
            pkg == self || uid % USER_RANGE < 10_000 || (info?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_PERSISTENT != 0 -> "protected"
            shared && !expert -> "shared"
            info == null || info.applicationInfo?.uid != uid -> "unknown"
            else -> null
        }
        val peers = rows.map { it.groupValues[1] }.filter { it != pkg }.distinct().sorted()
        val installedAt = info?.firstInstallTime ?: 0
        return Target(pkg, uid, user, lock, "$uid:$installedAt:$pkg", peers, expert,
            if (shared && lock == null) setOf("shared") else emptySet(), installedAt)
    }

    private fun grants(t: Target): List<Grant>? {
        val dump = command("dumpsys", "package", t.pkg) ?: return null
        val permissions = PackageDump.permissions(dump, t.pkg, t.uid % USER_RANGE, t.user) ?: return null
        return permissions.requested.map { name ->
            val state = permissions.runtime[name] ?: permissions.installed[name]
            val flags = state?.flags.orEmpty()
            val info = runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()
            // The system refuses fixed grants; a role's grant may be taken back, in expert mode only.
            val editable = t.lock == null && name in permissions.runtime &&
                info?.protection == PermissionInfo.PROTECTION_DANGEROUS &&
                flags.none { it == "SYSTEM_FIXED" || it == "POLICY_FIXED" || it == "GRANTED_BY_ROLE" && !t.expert }
            Grant(name, state?.allowed, editable, "GRANTED_BY_ROLE" in flags)
        }
    }

    private fun permissions(t: Target, saved: JSONObject, action: String, name: String): JSONObject {
        var rows = grants(t) ?: return JSONObject().put("lock", "unknown")
        val key = "permissions:${t.identity}"
        val owned = saved.optJSONObject(key) ?: JSONObject().also { saved.put(key, it) }
        val changes = when (action) {
            "deny", "allow" -> rows.filter { it.name == name }
            "denyAll" -> rows.filter { it.editable && it.allowed == true }
            "restoreAll" -> rows.filter { it.editable && owned.optBoolean(it.name) && it.allowed == false }
            else -> emptyList()
        }
        val failed = JSONArray()
        var changed = 0
        val allow = action == "allow" || action == "restoreAll"
        for (row in changes) {
            if (!row.editable || target(t.pkg, t.uid, t.user, t.expert) != t) {
                failed.put(row.name)
                continue
            }
            if (row.allowed == allow) continue
            if (!allow) {
                owned.put(row.name, true)
                write(saved)
            }
            val verb = if (allow) "grant" else "revoke"
            command("pm", verb, "--user", "${t.user}", t.pkg, row.name)
            val current = grants(t)
            if (current == null) {
                failed.put(row.name)
                return JSONObject().put("lock", "unknown").put("failed", failed).put("changed", changed)
            }
            rows = current
            if (current.firstOrNull { it.name == row.name }?.allowed == allow) {
                changed++
                if (allow) owned.remove(row.name)
            } else {
                if (!allow && current.any { it.name == row.name && it.allowed == true }) owned.remove(row.name)
                failed.put(row.name)
            }
            write(saved)
        }
        if (action in setOf("deny", "allow") && changes.isEmpty()) failed.put(name)
        return JSONObject().put("lock", t.lock)
            .put("rows", JSONArray(rows.map {
                JSONObject().put("name", it.name).put("allowed", it.allowed).put("editable", it.editable).put("role", it.role)
            }))
            .put("restore", rows.any { it.editable && it.allowed == false && owned.optBoolean(it.name) })
            .put("changed", changed).put("failed", failed)
    }

    private fun connectivity(vararg args: String) = command("cmd", "connectivity", *args)

    private fun firewall(pkg: String): Firewall? {
        val enabled = when (connectivity("get-chain3-enabled")?.trim()) {
            "chain:enabled" -> true
            "chain:disabled" -> false
            else -> return null
        }
        val denied = when (connectivity("get-package-networking-enabled", pkg)?.trim()) {
            "$pkg:deny" -> true
            "$pkg:allow" -> false
            else -> return null
        }
        val boot = command("settings", "get", "global", "boot_count")?.trim()?.toIntOrNull()?.toString() ?: return null
        return Firewall(enabled, denied, boot)
    }

    private fun chainRules(): Set<Int>? {
        val dump = command("dumpsys", "connectivity", "trafficcontroller") ?: return null
        if ("current ownerMatch configuration:" !in dump) return null
        val lines = dump.lines().map(String::trim)
        val start = lines.indexOf("sUidOwnerMap:")
        val end = lines.indexOf("sUidPermissionMap:")
        if (start < 0 || end <= start) return null
        val rows = lines.subList(start + 1, end).filter(String::isNotEmpty)
        return rows.mapNotNull { line ->
            val match = firewallRow.matchEntire(line) ?: return null
            match.groupValues[1].toInt().takeIf { "OEM_DENY_3_MATCH" in match.groupValues[2].split(' ') }
        }.toSet()
    }

    /** Why the firewall rule may not be changed, or null; [warn] collects what expert mode let through. */
    private fun networkLock(t: Target, warn: MutableSet<String>): String? {
        if (t.lock != null) return t.lock
        val users = command("cmd", "package", "list", "users") ?: return "unknown"
        val lines = users.lines().filter(String::isNotBlank)
        if (lines.firstOrNull()?.trim() != "Users:") return "unknown"
        val ids = lines.drop(1).map { userList.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return "unknown" }
        return when {
            ids == listOf(0) && t.user == 0 -> null
            t.expert -> null.also { warn += "users" }
            else -> "users"
        }
    }

    private fun network(t: Target, saved: JSONObject, action: String, warn: MutableSet<String>): JSONObject {
        val before = firewall(t.pkg) ?: return JSONObject().put("lock", t.lock ?: "unsupported")
        val owned = "network:${t.identity}"
        val ownsRule = saved.optString(owned) == before.boot
        val others = !before.enabled && chainRules()?.all { it == t.uid && ownsRule } != true
        val lock = networkLock(t, warn) ?: when {
            before.denied && !ownsRule -> if (t.expert) null.also { warn += "external" } else "external"
            others -> if (t.expert) null.also { warn += "chain" } else "chain"
            else -> null
        }
        var error = false
        if (action != "network" && lock == null) {
            if (target(t.pkg, t.uid, t.user, t.expert)?.copy(warn = t.warn) != t || networkLock(t, warn) != null ||
                firewall(t.pkg) != before) {
                return JSONObject().put("lock", "unknown")
            }
            val allow = action == "networkAllow"
            if (!allow) {
                saved.put(owned, before.boot)
                if (!before.enabled) saved.put("chain", before.boot)
                write(saved)
            }
            connectivity("set-package-networking-enabled", "$allow", t.pkg)
            if (!allow && !before.enabled && firewall(t.pkg)?.denied == true) {
                if (t.expert || chainRules() == setOf(t.uid)) connectivity("set-chain3-enabled", "true")
                if (firewall(t.pkg)?.enabled == false) connectivity("set-package-networking-enabled", "true", t.pkg)
            }
            val after = firewall(t.pkg) ?: return JSONObject().put("lock", "unknown")
            error = if (allow) after.denied else !after.denied || !after.enabled
            if (!after.denied) {
                saved.remove(owned)
                if (saved.optString("chain") == before.boot && chainRules()?.isEmpty() == true) {
                    if (after.enabled) connectivity("set-chain3-enabled", "false")
                    if (firewall(t.pkg)?.enabled == false) saved.remove("chain")
                }
                write(saved)
            }
        }
        val current = firewall(t.pkg) ?: return JSONObject().put("lock", "unknown")
        return JSONObject().put("lock", lock).put("allowed", !(current.enabled && current.denied))
            .put("boot", current.boot.toInt()).put("installedAt", t.installedAt)
            .put("owned", current.denied && saved.optString(owned) == current.boot)
            .put("failed", error || action != "network" && lock != null)
    }

    /** [op]'s modes for [t], read one op at a time so package and UID modes are told apart. */
    private fun opState(t: Target, op: String): OpState? =
        command("appops", "get", "--user", "${t.user}", t.pkg, op)?.let { opState(it, op) }

    /** Sets and restores only the selected package's mode. A UID denial or unowned package denial is read-only. */
    private fun ops(t: Target, saved: JSONObject, action: String, names: List<String>): JSONObject {
        val lock = t.lock?.takeIf { it != "shared" }
        val key = "ops:${t.identity}"
        val owned = saved.optJSONObject(key) ?: JSONObject().also { saved.put(key, it) }
        val failed = JSONArray()
        if (action != "ops") {
            val op = names.single()
            val before = opState(t, op)
            val savedMode = owned.optString(op)
            if (lock != null || before == null || before.editLock(savedMode) != null ||
                target(t.pkg, t.uid, t.user, t.expert) != t
            ) {
                failed.put(op)
            } else {
                val allow = action == "opAllow"
                if (before.denied == allow) {
                    val desiredMode = if (allow) savedMode else "ignore"
                    if (!allow) {
                        owned.put(op, before.packageMode)
                        write(saved)
                    }
                    command("appops", "set", "--user", "${t.user}", t.pkg, op, desiredMode)
                    val after = opState(t, op)
                    if (after == null || after.packageMode != desiredMode || after.denied == allow) failed.put(op)
                    if (after != null && after.pkg != "ignore") {
                        owned.remove(op)
                        write(saved)
                    }
                }
            }
        }
        val rows = JSONArray()
        for (op in names) {
            val state = opState(t, op) ?: continue
            rows.put(JSONObject().put("op", op).put("mode", state.mode).put("allowed", !state.denied)
                .put("lock", state.editLock(owned.optString(op))))
        }
        if (rows.length() == 0 && names.isNotEmpty()) return JSONObject().put("lock", lock ?: "unknown").put("failed", failed)
        return JSONObject().put("lock", lock).put("ops", rows).put("failed", failed)
    }

    private fun write(saved: JSONObject) {
        val output = ledger.startWrite()
        try {
            output.write(saved.toString().encodeToByteArray())
            ledger.finishWrite(output)
        } catch (error: Exception) {
            ledger.failWrite(output)
            throw error
        }
    }

    internal companion object {
        /**
         * Reads `appops get <pkg> <op>`: "Uid mode: OP: mode" is the UID's, "OP: mode" the package's;
         * "No operations." means neither is set. Null when the answer is neither.
         */
        fun opState(output: String, op: String): OpState? {
            val lines = output.lines().map(String::trim)
            var pkg: String? = null
            var uid: String? = null
            for (line in lines) {
                val match = opRow.matchEntire(line) ?: continue
                if (match.groupValues[2] != op) continue
                if (match.groupValues[1].isNotEmpty()) uid = match.groupValues[3] else pkg = match.groupValues[3]
            }
            if (pkg == null && uid == null && "No operations." !in lines) return null
            return OpState(pkg, uid)
        }

        private const val USER_RANGE = 100_000
        private val actions = setOf("permissions", "allow", "deny", "denyAll", "restoreAll", "network", "networkAllow", "networkDeny",
            "ops", "opAllow", "opDeny")
        private val opRow = Regex("""(Uid mode: )?([A-Z0-9_]+): ([a-z]+)\b.*""")
        private val opList = Regex("""[A-Z0-9_]+(?:,[A-Z0-9_]+)*""")
        private val deniedModes = setOf("ignore", "deny", "errored")
        private val packageRow = Regex("""package:([A-Za-z0-9_.]+)\s+uid:(\d+)""")
        private val userList = Regex("""UserInfo\{(\d+):[^}]*\}(?:\s+running)?""")
        private val permissionName = Regex("""[A-Za-z0-9_.]+""")
        private val firewallRow = Regex("""(\d+) ((?:[A-Z0-9_]+_MATCH)(?: [A-Z0-9_]+_MATCH)*)""")
    }
}
