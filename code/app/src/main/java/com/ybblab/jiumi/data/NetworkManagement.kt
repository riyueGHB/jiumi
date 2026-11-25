// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.ybblab.jiumi.probe.IProbeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ManagedNetworkApp(
    val pkg: String,
    val uid: Int,
    val installedAt: Long,
    val verifiedBoot: Int
) {
    val key: AppKey get() = AppKey(uid, pkg)
}

internal data class NetworkPolicy(
    val restoreEnabledAtBoot: Int? = null,
    val apps: Map<String, ManagedNetworkApp> = emptyMap(),
    val restoreFailures: Set<String> = emptySet(),
    val imported: Boolean = false
) {
    fun shouldRestore(app: ManagedNetworkApp, boot: Int): Boolean =
        restoreEnabledAtBoot?.let { it < boot && app.verifiedBoot < boot } == true

    fun withRestriction(app: ManagedNetworkApp, blocked: Boolean): NetworkPolicy = copy(
        apps = if (blocked) apps + (app.pkg to app) else apps - app.pkg,
        restoreFailures = restoreFailures - app.pkg
    )

    fun encode(): String = JSONObject().put("restoreEnabledAtBoot", restoreEnabledAtBoot).put("imported", imported)
        .put("apps", JSONArray().apply {
            apps.values.forEach { app ->
                put(JSONObject().put("pkg", app.pkg).put("uid", app.uid).put("installedAt", app.installedAt)
                    .put("blocked", true).put("verifiedBoot", app.verifiedBoot))
            }
        }).toString()

    companion object {
        fun decode(text: String?): NetworkPolicy {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return NetworkPolicy()
            return NetworkPolicy(json.optInt("restoreEnabledAtBoot", -1).takeIf { it >= 0 },
                decodeApps(json.optJSONArray("apps") ?: JSONArray()), imported = json.optBoolean("imported"))
        }

        fun decodeApps(rows: JSONArray): Map<String, ManagedNetworkApp> = buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val pkg = row.optString("pkg")
                val uid = row.optInt("uid", -1)
                val installedAt = row.optLong("installedAt")
                val boot = row.optInt("verifiedBoot", -1)
                if (row.optBoolean("blocked") && pkg.isNotEmpty() && uid >= 0 && installedAt > 0 && boot >= 0) {
                    put(pkg, ManagedNetworkApp(pkg, uid, installedAt, boot))
                }
            }
        }
    }
}

/** Keeps the user's choices after Android discards its firewall rules at reboot. */
internal class NetworkManagement(
    context: Context,
    private val connectedProbe: () -> IProbeService?
) {
    private val prefs = context.getSharedPreferences("jiumi_settings", Context.MODE_PRIVATE)
    private val lock = Mutex()
    val boot: Int? = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
    private val policy = MutableStateFlow(NetworkPolicy.decode(prefs.getString(KEY, null)))
    val state = policy.asStateFlow()

    suspend fun setAutoRestore(enabled: Boolean): Boolean = transaction {
        val enabledAt = if (enabled) boot ?: return@transaction false else null
        val current = policy.value
        if (enabled == (current.restoreEnabledAtBoot != null)) return@transaction true
        save(current.copy(restoreEnabledAtBoot = enabledAt, restoreFailures = emptySet()))
    }

    /** Only verified changes to the per-app switch update the restriction list. */
    suspend fun change(key: AppKey, blocked: Boolean, expert: Boolean): JSONObject? = transaction {
        val service = connectedProbe() ?: return@transaction JSONObject().put("lock", "monitor")
        val result = call(service, key, if (blocked) "networkDeny" else "networkAllow", expert = expert)
            ?: return@transaction null
        val app = verifiedApp(key, blocked, result)
        if (app != null && !save(policy.value.withRestriction(app, blocked))) result.put("policySaveFailed", true)
        result
    }

    suspend fun onConnected(service: IProbeService) {
        transaction {
            val current = policy.value
            if (current.imported || connectedProbe() != service) return@transaction
            val existing = runCatching { JSONArray(service.ownedNetworkRules()) }.getOrNull() ?: return@transaction
            save(current.copy(apps = NetworkPolicy.decodeApps(existing) + current.apps, imported = true))
        }
        val currentBoot = boot ?: return
        // Each row is a separate transaction so manual changes can take effect during restoration.
        for (pkg in policy.value.apps.keys) transaction {
            val current = policy.value
            val app = current.apps[pkg] ?: return@transaction
            if (!current.shouldRestore(app, currentBoot) || connectedProbe() != service) return@transaction
            val result = call(service, app.key, "networkDeny", app.installedAt)
            val updated = result?.let { verifiedApp(app.key, true, it) }
            val saved = when {
                result?.optBoolean("stale") == true -> save(current.withRestriction(app, blocked = false))
                updated != null -> save(current.withRestriction(updated, blocked = true))
                else -> false
            }
            if (!saved) {
                policy.value = current.copy(restoreFailures = current.restoreFailures + pkg)
            }
        }
    }

    private fun call(
        service: IProbeService, key: AppKey, action: String, installedAt: Long? = null, expert: Boolean = false
    ): JSONObject? {
        val pkg = key.pkg ?: return null
        return runCatching { JSONObject(service.appAccess(pkg, key.uid, action, installedAt?.toString().orEmpty(), expert)) }.getOrNull()
    }

    private fun verifiedApp(key: AppKey, blocked: Boolean, result: JSONObject): ManagedNetworkApp? {
        val pkg = key.pkg ?: return null
        val installedAt = result.optLong("installedAt")
        val verifiedBoot = result.optInt("boot", -1)
        if (installedAt <= 0 || verifiedBoot < 0 || result.optString("lock").isNotEmpty() || result.optBoolean("failed") ||
            !result.has("allowed") || result.optBoolean("allowed") == blocked || blocked && !result.optBoolean("owned")) return null
        return ManagedNetworkApp(pkg, key.uid, installedAt, verifiedBoot)
    }

    @SuppressLint("UseKtx") // The KTX extension discards the result of commit().
    private fun save(next: NetworkPolicy): Boolean {
        if (next == policy.value) return true
        if (!prefs.edit().putString(KEY, next.encode()).commit()) return false
        policy.value = next
        return true
    }

    private suspend fun <T> transaction(change: () -> T): T = withContext(Dispatchers.IO) {
        lock.withLock { withContext(NonCancellable) { change() } }
    }

    private companion object {
        const val KEY = "network_management"
    }
}
