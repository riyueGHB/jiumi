// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

/**
 * Accesses read from successive AppOps snapshots when a watcher cannot observe other apps.
 * Each snapshot keeps, per app, operation and attribution tag, the time of the latest
 * access and refusal, so a newer time than the last snapshot's is at least one access at that time.
 * Several accesses between two snapshots show as their latest one: fewer, never invented.
 *
 * Accesses are told apart by whether the app made them itself ([SELF_FLAGS]) or a provider made
 * them for it ([PROXIED_FLAGS]), as the watchers' flags would. A started operation (camera,
 * microphone, continuous location) shows as a session like the watchers' active events: open while
 * AppOps marks it running, or, if it began and ended between two snapshots, from its start for its
 * recorded duration.
 */
internal class AccessPolls(
    private val since: Long,
    private val includeNoted: Boolean = true,
    private val includeActive: Boolean = true
) {
    /** One snapshot entry; times are wall-clock milliseconds, 0 or less when there is none. */
    data class Entry(
        val op: String, val uid: Int, val pkg: String?, val attr: String?,
        val self: Times, val proxied: Times, val running: Boolean
    )

    /** Latest access, refusal and duration (-1 when unknown) for one group of flags. */
    data class Times(val access: Long, val reject: Long, val duration: Long)

    sealed class Event {
        abstract val t: Long
        data class Noted(val op: String, val uid: Int, val pkg: String?, val attr: String?,
                         val flags: Int, val result: Int, override val t: Long) : Event()
        data class Active(val op: String, val uid: Int, val pkg: String?, val attr: String?,
                          val active: Boolean, override val t: Long) : Event()
    }

    private data class Key(val op: String, val uid: Int, val pkg: String?, val attr: String?)

    private val seen = HashMap<Pair<Key, Int>, Long>()
    /** Operations AppOps marks running, and whether their start was reported. */
    private val running = HashMap<Key, Boolean>()

    /** What changed since the previous snapshot, in time order. */
    fun diff(entries: List<Entry>, now: Long): List<Event> {
        val out = ArrayList<Event>()
        val stillRunning = HashSet<Key>()
        for (e in entries) {
            val key = Key(e.op, e.uid, e.pkg, e.attr)
            val start = e.self.access
            if (e.running) {
                stillRunning += key
                if (key !in running) {
                    // One already running when monitoring began is not reported, as the watchers would not.
                    val reported = start > since
                    running[key] = reported
                    if (reported) out += Event.Active(e.op, e.uid, e.pkg, e.attr, true, start)
                }
            }
            if (newer(key, SELF_FLAGS, ACCESS, start) && !(e.running && key in running)) {
                if (e.self.duration > 0) {
                    out += Event.Active(e.op, e.uid, e.pkg, e.attr, true, start)
                    out += Event.Active(e.op, e.uid, e.pkg, e.attr, false, start + e.self.duration)
                } else {
                    out += Event.Noted(e.op, e.uid, e.pkg, e.attr, SELF_FLAGS, ALLOWED, start)
                }
            }
            if (newer(key, SELF_FLAGS, REJECT, e.self.reject)) {
                out += Event.Noted(e.op, e.uid, e.pkg, e.attr, SELF_FLAGS, IGNORED, e.self.reject)
            }
            if (newer(key, PROXIED_FLAGS, ACCESS, e.proxied.access)) {
                out += Event.Noted(e.op, e.uid, e.pkg, e.attr, PROXIED_FLAGS, ALLOWED, e.proxied.access)
            }
            if (newer(key, PROXIED_FLAGS, REJECT, e.proxied.reject)) {
                out += Event.Noted(e.op, e.uid, e.pkg, e.attr, PROXIED_FLAGS, IGNORED, e.proxied.reject)
            }
            if (!e.running && key in running) {
                val reported = running.remove(key) == true
                val end = if (e.self.duration >= 0 && start > 0) start + e.self.duration else now
                if (reported) out += Event.Active(e.op, e.uid, e.pkg, e.attr, false, minOf(end, now))
            }
        }
        // An app removed while its operation ran no longer appears; its session ends now.
        val gone = running.keys - stillRunning
        for (key in gone) {
            if (running.remove(key) == true) out += Event.Active(key.op, key.uid, key.pkg, key.attr, false, now)
        }
        // Keep the listener that registered successfully as the source for its events.
        if (!includeNoted) out.removeAll { it is Event.Noted }
        if (!includeActive) out.removeAll { it is Event.Active }
        out.sortBy { it.t }
        return out
    }

    /** Whether [t] is later than both the start of monitoring and the last time seen for this entry. */
    private fun newer(key: Key, flags: Int, kind: Int, t: Long): Boolean {
        if (t <= 0) return false
        val slot = key to (flags * 2 + kind)
        val previous = seen.put(slot, t)
        return t > since && (previous == null || t > previous)
    }

    companion object {
        /** AppOpsManager.OP_FLAG_SELF, OP_FLAG_TRUSTED_PROXY and OP_FLAG_UNTRUSTED_PROXY. */
        const val SELF_FLAGS = 0x1 or 0x2 or 0x4
        /** AppOpsManager.OP_FLAG_TRUSTED_PROXIED and OP_FLAG_UNTRUSTED_PROXIED. */
        const val PROXIED_FLAGS = 0x8 or 0x10
        private const val ACCESS = 0
        private const val REJECT = 1
        /** AppOpsManager.MODE_ALLOWED and MODE_IGNORED, the results the watchers report. */
        private const val ALLOWED = 0
        private const val IGNORED = 1
    }
}
