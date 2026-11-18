package com.lavan.claudeusage

import android.content.Context

/** Local counter plus values synced from claude.ai > Settings > Usage. */
object UsageStore {
    private fun prefs(c: Context) = c.getSharedPreferences("usage", Context.MODE_PRIVATE)

    fun limit(c: Context): Int = prefs(c).getInt("limit", 45)
    fun windowHours(c: Context): Int = prefs(c).getInt("hours", 5)
    fun used(c: Context): Int { refresh(c); return prefs(c).getInt("used", 0) }
    private fun start(c: Context): Long = prefs(c).getLong("start", 0L)

    fun setConfig(c: Context, limit: Int, hours: Int) {
        prefs(c).edit().putInt("limit", limit.coerceAtLeast(1)).putInt("hours", hours.coerceAtLeast(1)).apply()
    }

    /** "2h 15m", "6d 2h", "57m" */
    fun fmt(ms: Long): String {
        val m = ms / 60_000
        val d = m / 1440
        val h = (m % 1440) / 60
        val mm = m % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${mm}m"
            else -> "${mm}m"
        }
    }

    // ---- Local counter ----

    fun refresh(c: Context) {
        val s = start(c)
        if (s > 0 && System.currentTimeMillis() >= s + windowHours(c) * 3_600_000L) reset(c)
    }

    fun increment(c: Context) {
        refresh(c)
        val p = prefs(c)
        val e = p.edit()
        if (p.getInt("used", 0) == 0 || start(c) == 0L) e.putLong("start", System.currentTimeMillis())
        e.putInt("used", p.getInt("used", 0) + 1).apply()
    }

    fun reset(c: Context) {
        prefs(c).edit().putInt("used", 0).putLong("start", 0L).apply()
    }

    fun remainingMs(c: Context): Long {
        refresh(c)
        val s = start(c)
        if (s == 0L) return 0L
        return (s + windowHours(c) * 3_600_000L - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun resetText(c: Context): String {
        val ms = remainingMs(c)
        return if (ms == 0L) "no active window" else "resets in ${fmt(ms)}"
    }

    // ---- Synced values ----

    fun saveSync(c: Context, sessionPct: Int, resetMs: Long, weeklyPct: Int, weeklyResetMs: Long) {
        val now = System.currentTimeMillis()
        prefs(c).edit()
            .putInt("syncPct", sessionPct)
            .putLong("syncAt", now)
            .putLong("syncResetAt", if (resetMs > 0) now + resetMs else 0L)
            .putInt("weekPct", weeklyPct)
            .putLong("weekResetAt", if (weeklyResetMs > 0) now + weeklyResetMs else 0L)
            .apply()
    }

    /** Session % from the last sync while still valid, else null (widget falls back to the local counter). */
    fun syncedSessionPct(c: Context): Int? {
        val p = prefs(c)
        val at = p.getLong("syncAt", 0L)
        if (at == 0L) return null
        val now = System.currentTimeMillis()
        val r = p.getLong("syncResetAt", 0L)
        if (r > 0 && now >= r) return null
        if (r == 0L && now - at > 30 * 60_000L) return null
        return p.getInt("syncPct", 0)
    }

    fun syncedResetText(c: Context): String {
        val p = prefs(c)
        val r = p.getLong("syncResetAt", 0L)
        val now = System.currentTimeMillis()
        if (r > now) return "resets in ${fmt(r - now)}"
        return "synced ${(now - p.getLong("syncAt", now)) / 60_000}m ago"
    }

    /** Weekly % from the last sync, or null if unknown or its week has ended. */
    fun weeklyPct(c: Context): Int? {
        val p = prefs(c)
        val w = p.getInt("weekPct", -1)
        if (w < 0) return null
        val r = p.getLong("weekResetAt", 0L)
        if (r > 0 && System.currentTimeMillis() >= r) return null
        return w
    }

    fun weeklyResetText(c: Context): String {
        val r = prefs(c).getLong("weekResetAt", 0L)
        val now = System.currentTimeMillis()
        return if (r > now) "resets in ${fmt(r - now)}" else ""
    }

    fun weeklyText(c: Context): String {
        val w = weeklyPct(c) ?: return ""
        val r = weeklyResetText(c)
        return "Week: $w%" + if (r.isNotEmpty()) " · $r" else ""
    }
}
