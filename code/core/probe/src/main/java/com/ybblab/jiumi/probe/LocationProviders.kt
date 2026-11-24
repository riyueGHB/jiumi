// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import org.json.JSONArray
import org.json.JSONObject

/**
 * The apps the system has bound as its location providers, from `dumpsys location`. A provider scans
 * Wi-Fi, cells and Bluetooth to work out a fix for whichever app asked; the system notes that app's
 * own access as well.
 *
 * Layout (AOSP, Android 12 and later):
 *   Location Providers:
 *     network provider:
 *       ...
 *       identity=10266/com.oplus.location
 * A provider run by the platform itself names a uid below 10000 and an attribution in brackets.
 *
 * Android 11 names only the bound service, without its uid:
 *   Location Providers:
 *     network provider:
 *       ...
 *       service={com.google.android.gms/com.google.android.location.network.NetworkLocationService}@1[u0]
 * The uid is then looked up through [uidOf].
 */
internal object LocationProviders {
    private const val SECTION = "Location Providers:"
    private val header = Regex("^(\\w+) provider(?: \\[mock])?:$")
    private val identity = Regex("^identity=(\\d+)/([A-Za-z0-9_.]+)(?:\\[[^]]*])?$")
    private val service = Regex("^service=\\{([A-Za-z0-9_.]+)/[^}]+\\}@-?\\d+\\[u\\d+\\]$")

    /** JSON array of {"name","uid","pkg"}, one per provider whose identity the dump names. */
    fun parse(dump: String, uidOf: (String) -> Int? = { null }): JSONArray {
        val found = JSONArray()
        val lines = dump.lineSequence().iterator()
        var sectionIndent = -1
        while (lines.hasNext()) {
            val raw = lines.next()
            if (raw.trim() == SECTION) { sectionIndent = indentOf(raw); break }
        }
        if (sectionIndent < 0) return found
        var name: String? = null
        for (raw in lines) {
            if (raw.isBlank()) continue
            // The providers are listed first; the history and other managers follow at the section's level.
            if (indentOf(raw) <= sectionIndent) break
            val line = DumpText.normalize(raw)
            val opened = header.find(line)
            if (opened != null) { name = opened.groupValues[1]; continue }
            val provider = name ?: continue
            val named = identity.find(line)?.let { it.groupValues[1].toInt() to it.groupValues[2] }
                ?: service.find(line)?.let { m -> uidOf(m.groupValues[1])?.let { it to m.groupValues[1] } }
                ?: continue
            found.put(JSONObject().put("name", provider).put("uid", named.first).put("pkg", named.second))
            name = null
        }
        return found
    }

    private fun indentOf(line: String) = line.length - line.trimStart().length
}
