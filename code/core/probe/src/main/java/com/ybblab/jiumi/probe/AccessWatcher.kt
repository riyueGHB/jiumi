// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import org.json.JSONObject
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Receives the access events AppOps already reports to watchers. Repeated identical accesses
 * within [WINDOW_MS] are folded into one record with a count, so a chatty caller costs one line
 * a minute. Nothing here changes how the system or the caller behaves.
 *
 * When a listener cannot observe other apps, its events come from reading AppOps every
 * [POLL_MS] instead ([AccessPolls]). Android 11's shell lacks WATCH_APPOPS and needs both reads.
 */
internal class AccessWatcher(context: Context, private val spool: EventSpool, private val states: UidStates) {
    private val thread = HandlerThread("access-watch").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val buckets = LinkedHashMap<NotedKey, ObservationBucket>()
    private var listeners: List<Any> = emptyList()
    private var finishing = false
    private val watchesOthers =
        context.checkSelfPermission(WATCH_APPOPS) == PackageManager.PERMISSION_GRANTED
    private var polledOps: Array<String> = emptyArray()
    private var polls: AccessPolls? = null
    private val poll = object : Runnable {
        override fun run() {
            val current = polls ?: return
            runCatching { readSnapshot() }
                .onSuccess { entries -> current.diff(entries, System.currentTimeMillis()).forEach(::polled) }
                .onFailure { ProbeMain.log("AppOps poll failed: ${it.javaClass.simpleName}: ${it.message}") }
            handler.postDelayed(this, POLL_MS)
        }
    }

    private data class NotedKey(
        val op: String, val uid: Int, val pkg: String?, val attribution: String?, val flags: Int?, val result: Int
    )

    /** Replaces the watched operations; unknown names on this Android version are skipped. */
    fun watch(ops: List<String>) = handler.post {
        if (finishing) return@post
        stop()
        val supported = ops.filter { op -> runCatching { opCode(op) >= 0 }.getOrDefault(false) }
        if (supported.isEmpty()) return@post
        val noted = if (watchesOthers) runCatching { watchNoted(supported) }
            .onFailure { ProbeMain.log("noted watch failed: ${it.cause?.javaClass?.simpleName ?: it.javaClass.simpleName}") }
            .getOrNull() else null
        val active = if (watchesOthers) runCatching { watchActive(supported) }
            .onFailure { ProbeMain.log("active watch failed: ${it.cause?.javaClass?.simpleName ?: it.javaClass.simpleName}") }
            .getOrNull() else null
        listeners = listOfNotNull(noted, active)
        if (noted == null || active == null) {
            polledOps = supported.toTypedArray()
            polls = AccessPolls(System.currentTimeMillis(), includeNoted = noted == null, includeActive = active == null)
            handler.post(poll)
        }
        ProbeMain.log("AppOps ${supported.size} operations: noted=${if (noted == null) "poll" else "watch"}, " +
            "active=${if (active == null) "poll" else "watch"}")
    }

    fun finishRecording() {
        val drained = java.util.concurrent.CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicReference<Result<Unit>>()
        check(handler.post {
            finishing = true
            result.set(runCatching { stop() })
            drained.countDown()
        })
        check(drained.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "AppOps watcher could not be drained" }
        result.get().getOrThrow()
    }

    private fun stop() {
        handler.removeCallbacks(poll)
        polls = null
        flush(force = true)
        listeners.forEach { listener ->
            runCatching {
                val iface = listener.javaClass.interfaces.first()
                val name = if (iface.simpleName == "OnOpNotedListener") "stopWatchingNoted" else "stopWatchingActive"
                AppOpsManager::class.java.getMethod(name, iface).invoke(appOps, listener)
            }
        }
        listeners = emptyList()
    }

    private fun watchNoted(ops: List<String>): Any {
        val type = Class.forName("android.app.AppOpsManager\$OnOpNotedListener")
        val listener = proxy(type) { args ->
            val call = NotedCall.parse(args)
            noted(call.op as? String ?: opName(call.op as Int), call.uid, call.pkg, call.attribution, call.flags, call.result)
        }
        val start = AppOpsManager::class.java.methods.first {
            it.name == "startWatchingNoted" && it.parameterTypes.size == 2 && it.parameterTypes[1] == type
        }
        val opsArgument: Any = if (start.parameterTypes[0] == IntArray::class.java) ops.map(::opCode).toIntArray() else ops.toTypedArray()
        start.invoke(appOps, opsArgument, listener)
        return listener
    }

    private fun watchActive(ops: List<String>): Any {
        val type = AppOpsManager.OnOpActiveChangedListener::class.java
        val listener = proxy(type) { args ->
            val active = args.firstOrNull { it is Boolean } as Boolean? ?: return@proxy
            val attribution = args.getOrNull(3) as? String
            val t = System.currentTimeMillis()
            handler.post { active(args[0] as String, args[1] as Int, args[2] as String?, attribution, active, t) }
        }
        AppOpsManager::class.java.getMethod("startWatchingActive", Array<String>::class.java, Executor::class.java, type)
            .invoke(appOps, ops.toTypedArray(), executor, listener)
        return listener
    }

    private fun active(op: String, uid: Int, pkg: String?, attribution: String?, active: Boolean, t: Long) {
        if (finishing || spool.exclusions.blocks(uid, pkg, t)) return
        record(JSONObject()
            .put("kind", "active")
            .put("t", t)
            .put("op", op)
            .put("uid", uid)
            .put("pkg", pkg)
            .put("attr", attribution)
            .put("active", active)
            // A start carries the state AppOps recorded; an end, the state at the time it ended.
            .put("state", if (active) states.accessState(op, uid, pkg, t) else states.stateAt(uid, t))
            .put("screen", states.screenAt(t)))
    }

    private fun polled(event: AccessPolls.Event) = when (event) {
        is AccessPolls.Event.Noted -> noted(event.op, event.uid, event.pkg, event.attr, event.flags, event.result, event.t)
        is AccessPolls.Event.Active -> active(event.op, event.uid, event.pkg, event.attr, event.active, event.t)
    }

    /** Every app's entries for the polled operations, one per attribution tag. */
    private fun readSnapshot(): List<AccessPolls.Entry> {
        val forOps = AppOpsManager::class.java.getMethod("getPackagesForOps", Array<String>::class.java)
        val packages = forOps.invoke(appOps, polledOps) as? List<*> ?: return emptyList()
        val flagsArg = Int::class.javaPrimitiveType
        val entries = ArrayList<AccessPolls.Entry>()
        for (p in packages) {
            val packageOps = p ?: continue
            val type = packageOps.javaClass
            val uid = type.getMethod("getUid").invoke(packageOps) as Int
            val pkg = type.getMethod("getPackageName").invoke(packageOps) as String?
            for (opEntry in type.getMethod("getOps").invoke(packageOps) as List<*>) {
                val entry = opEntry ?: continue
                val op = entry.javaClass.getMethod("getOpStr").invoke(entry) as String
                val attributed = entry.javaClass.getMethod("getAttributedOpEntries").invoke(entry) as Map<*, *>
                for ((tag, a) in attributed) {
                    val attributedEntry = a ?: continue
                    val t = attributedEntry.javaClass
                    val access = t.getMethod("getLastAccessTime", flagsArg)
                    val reject = t.getMethod("getLastRejectTime", flagsArg)
                    val duration = t.getMethod("getLastDuration", flagsArg)
                    fun times(flags: Int) = AccessPolls.Times(
                        access.invoke(attributedEntry, flags) as Long,
                        reject.invoke(attributedEntry, flags) as Long,
                        duration.invoke(attributedEntry, flags) as Long
                    )
                    entries += AccessPolls.Entry(op, uid, pkg, tag as String?,
                        times(AccessPolls.SELF_FLAGS), times(AccessPolls.PROXIED_FLAGS),
                        t.getMethod("isRunning").invoke(attributedEntry) as Boolean)
                }
            }
        }
        return entries
    }

    private fun noted(
        op: String, uid: Int, pkg: String?, attribution: String?, flags: Int?, result: Int,
        time: Long = System.currentTimeMillis()
    ) {
        handler.post {
            if (finishing || spool.exclusions.blocks(uid, pkg, time)) return@post
            val key = NotedKey(op, uid, pkg, attribution, flags, result)
            val observed = ObservationState(states.stateAt(uid, time), states.screenAt(time))
            val bucket = buckets[key]
            if (bucket?.add(time, observed, WINDOW_MS) == true) return@post
            bucket?.let { record(it.event()) }
            val first = buckets.isEmpty()
            val event = JSONObject().put("kind", "access").put("op", op).put("uid", uid).put("pkg", pkg)
                .put("attr", attribution).put("result", result)
            // Android 11 does not report flags; the record then leaves them out rather than guess.
            if (flags != null) event.put("flags", flags)
            buckets[key] = ObservationBucket(event, time, observed)
            if (first) handler.postDelayed({ flush(force = false) }, WINDOW_MS)
        }
    }

    private fun flush(force: Boolean) {
        val now = System.currentTimeMillis()
        val iterator = buckets.values.iterator()
        while (iterator.hasNext()) {
            val bucket = iterator.next()
            if (!force && now - bucket.first < WINDOW_MS) continue
            val written = record(bucket.event())
            if (finishing) written.getOrThrow()
            iterator.remove()
        }
        if (buckets.isNotEmpty()) handler.postDelayed({ flush(force = false) }, WINDOW_MS / 4)
    }

    private fun record(event: JSONObject) = runCatching { spool.append(event) }
        .onFailure { ProbeMain.log("spool write failed: ${it.javaClass.simpleName}") }

    private fun opCode(op: String): Int =
        AppOpsManager::class.java.getMethod("strOpToOp", String::class.java).invoke(null, op) as Int

    private fun opName(code: Int): String =
        AppOpsManager::class.java.getMethod("opToPublicName", Int::class.javaPrimitiveType).invoke(null, code) as String

    private fun proxy(type: Class<*>, onCall: (Array<Any?>) -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
            when (method.name) {
                "equals" -> self === args?.get(0)
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "JiumiWatcher"
                else -> {
                    if (method.returnType == Void.TYPE && args != null) {
                        runCatching { onCall(args) }.onFailure { ProbeMain.log("watch callback failed: ${it.javaClass.simpleName}") }
                    }
                    null
                }
            }
        }

    private companion object {
        const val WINDOW_MS = 60_000L
        /** One AppOps read every few seconds; an access shows up at its own time, at most this late. */
        const val POLL_MS = 5_000L
        const val WATCH_APPOPS = "android.permission.WATCH_APPOPS"
    }
}

/**
 * The arguments of one OnOpNotedListener call. Their shape depends on the Android release:
 * - Android 11: (code, uid, package, result), without attribution tag or flags
 * - Android 12–13: (code, uid, package, attributionTag, flags, result)
 * - later releases: (op, uid, package, attributionTag, flags, result), and on Android 15+ also
 *   (op, uid, package, attributionTag, virtualDeviceId, flags, result)
 * [op] is the operation name, or its code where the release passes a code.
 */
internal class NotedCall(
    val op: Any, val uid: Int, val pkg: String?, val attribution: String?, val flags: Int?, val result: Int
) {
    companion object {
        fun parse(args: Array<Any?>): NotedCall = when {
            args.size == 4 -> NotedCall(args[0]!!, args[1] as Int, args[2] as String?, null, null, args[3] as Int)
            args.size >= 6 -> NotedCall(args[0]!!, args[1] as Int, args[2] as String?, args[3] as String?,
                args[args.size - 2] as Int, args[args.size - 1] as Int)
            else -> throw IllegalArgumentException("unexpected onOpNoted arity ${args.size}")
        }
    }
}
