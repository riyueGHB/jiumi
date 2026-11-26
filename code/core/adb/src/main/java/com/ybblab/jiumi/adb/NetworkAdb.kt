// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.adb

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

/** Connects to an already-open classic ADB listener on this phone. Never changes adbd. */
class NetworkAdb(
    private val context: Context,
    private val scope: CoroutineScope,
    private val log: PairingLog
) {
    sealed interface Result {
        data class Ready(val port: Int) : Result
        data class NeedsAuthorization(val port: Int) : Result
        data object Unavailable : Result
        data object WrongIdentity : Result
        data object KeyUnavailable : Result
    }

    private val prefs = context.getSharedPreferences("jiumi_network_adb", Context.MODE_PRIVATE)
    private val _savedPort = MutableStateFlow(prefs.getInt("port", 0).takeIf { it in 1..65535 })
    val savedPort = _savedPort.asStateFlow()
    private val mutex = Mutex()

    fun rememberPort(port: Int) {
        require(port in 1..65535)
        prefs.edit().putInt("port", port).apply()
        _savedPort.value = port
    }

    /**
     * Only an explicit action can submit the public key for a system prompt, and only to a
     * particular port. Automatic attempts sign the challenge with the existing key quietly.
     */
    suspend fun use(
        port: Int? = null,
        allowAuthorization: Boolean = false,
        onShell: (AirAdbClient) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        require(port == null || port in 1..65535)
        require(!allowAuthorization || port != null)
        mutex.withLock {
            val key = try {
                AirAdbKey.loadOrCreate(File(context.filesDir, "adb_key"), File(context.filesDir, "adb_key.pub"),
                    replaceInvalidKey = true) { error ->
                    log.event("network ADB rebuilding invalid local key: ${error.diagnostic}")
                }
            } catch (error: IOException) {
                val diagnostic = (error as? InvalidAdbPrivateKeyException)?.diagnostic ?: error.javaClass.simpleName
                log.event("network ADB key unavailable: $diagnostic")
                return@withLock Result.KeyUnavailable
            }
            val tried = mutableSetOf<Int>()
            var failure: Result = Result.Unavailable
            fun tryPort(candidate: Int): Result? {
                if (!tried.add(candidate)) return null
                val client = AirAdbClient("127.0.0.1", candidate, key, allowAuthorization, legacyOnly = true)
                client.use {
                    try {
                        it.connect()
                        if (it.shell("id -u").uid != AdbShellResult.ANDROID_SHELL_UID) return Result.WrongIdentity
                    } catch (error: AirAdbAuthorizationException) {
                        return Result.NeedsAuthorization(candidate)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        log.event("network ADB port $candidate unavailable: ${error.javaClass.simpleName}")
                        return Result.Unavailable
                    }
                    ensureActive()
                    log.event("network ADB verified shell port=$candidate")
                    onShell(it)
                    return Result.Ready(candidate)
                }
            }

            // Prefer the previously verified port, then the system's classic port or the default.
            // Discovery is limited to the traditional ADB service and always connects to loopback.
            val candidates = port?.let(::listOf)
                ?: listOfNotNull(_savedPort.value, propertyPort("service.adb.tcp.port"),
                    propertyPort("persist.adb.tcp.port"), 5555)
            for (candidate in candidates) {
                ensureActive()
                when (val result = tryPort(candidate)) {
                    is Result.Ready -> return@withLock result
                    is Result.NeedsAuthorization, Result.WrongIdentity ->
                        if (failure == Result.Unavailable) failure = result
                    else -> Unit
                }
            }
            if (port == null && failure !is Result.NeedsAuthorization) {
                discoverPort()?.let { candidate ->
                    when (val result = tryPort(candidate)) {
                        is Result.Ready -> return@withLock result
                        is Result.NeedsAuthorization, Result.WrongIdentity ->
                            if (failure == Result.Unavailable) failure = result
                        else -> Unit
                    }
                }
            }
            failure
        }
    }

    private fun propertyPort(name: String): Int? = runCatching {
        ProcessBuilder("/system/bin/getprop", name).start().inputStream.bufferedReader().use {
            it.readText().trim().toIntOrNull()?.takeIf { port -> port in 1..65535 }
        }
    }.getOrNull()

    private suspend fun discoverPort(): Int? {
        val found = CompletableDeferred<Int>()
        val discovery = AirMdnsDiscovery(context, scope, "_adb._tcp", { _, port -> found.complete(port) })
        return try {
            discovery.start()
            withTimeoutOrNull(2_500) { found.await() }
        } finally {
            discovery.stop()
        }
    }
}
