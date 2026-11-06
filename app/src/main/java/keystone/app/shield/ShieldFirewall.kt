// Copyright (C) 2026 Tylerlikesps4
// SPDX-License-Identifier: GPL-3.0-or-later
package keystone.app.shield

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/** Firewall settings and live stats, shared by the VPN service and the Settings screen. */
object ShieldFirewall {
    private lateinit var prefs: SharedPreferences
    val running = mutableStateOf(false)
    val blockedCount = mutableIntStateOf(0)
    val allowedCount = mutableIntStateOf(0)
    val lastError = mutableStateOf<String?>(null)
    /** Newest first: "app · domain". */
    val recentBlocks = mutableStateListOf<String>()

    fun init(ctx: Context) { prefs = ctx.getSharedPreferences("shield_fw", Context.MODE_PRIVATE) }

    var blockTrackers: Boolean
        get() = prefs.getBoolean("trackers", true)
        set(v) = prefs.edit().putBoolean("trackers", v).apply()
    var blockedApps: Set<String>
        get() = prefs.getStringSet("apps", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("apps", v).apply()
    var customDomains: List<String>
        get() = prefs.getString("domains", "")!!.lines().filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("domains", v.joinToString("\n")).apply()
    var wantOn: Boolean
        get() = prefs.getBoolean("want_on", false)
        set(v) = prefs.edit().putBoolean("want_on", v).apply()

    fun rulesText(ctx: Context): String = buildString {
        if (blockTrackers) append(ctx.assets.open("shield_blocklist.txt").bufferedReader().use { it.readText() }).append('\n')
        customDomains.forEach { append(it).append('\n') }
    }

    fun recordBlock(what: String) {
        blockedCount.intValue++
        recentBlocks.add(0, what)
        while (recentBlocks.size > 50) recentBlocks.removeAt(recentBlocks.lastIndex)
    }
}
