package com.lavan.claudeusage

/** Parses the visible text of claude.ai > Settings > Usage. Layout may change; returns null if not recognised. */
object UsageParser {
    data class Result(
        val sessionPct: Int,
        val sessionResetMs: Long,   // ms until reset, 0 if the page shows no countdown
        val weeklyPct: Int,         // -1 if not found
        val weeklyReset: String     // raw text such as "Resets Mon 3:30 AM"
    )

    private val pct = Regex("""(\d{1,3})\s*%""")
    private val resetIn = Regex(
        """Resets in\s*(?:(\d+)\s*(?:days?|d)\s*)?(?:(\d+)\s*(?:hours?|hrs?|h)\s*)?(?:(\d+)\s*(?:minutes?|mins?|m)\b)?""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): Result? {
        val t = raw.replace(' ', ' ')
        val s = t.indexOf("Current session", ignoreCase = true)
        if (s < 0) return null
        val w = t.indexOf("Weekly", s, ignoreCase = true)

        val sBlock = if (w > s) t.substring(s, w) else t.substring(s, minOf(t.length, s + 300))
        val sp = pct.find(sBlock)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        var ms = 0L
        resetIn.find(sBlock)?.let { m ->
            val d = m.groupValues[1].toLongOrNull() ?: 0
            val h = m.groupValues[2].toLongOrNull() ?: 0
            val min = m.groupValues[3].toLongOrNull() ?: 0
            ms = ((d * 24 + h) * 60 + min) * 60_000L
        }

        var wp = -1
        var wr = ""
        if (w > s) {
            val wBlock = t.substring(w, minOf(t.length, w + 300))
            wp = pct.find(wBlock)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            wr = Regex("""Resets[^\n]{0,40}""", RegexOption.IGNORE_CASE).find(wBlock)?.value?.trim() ?: ""
        }
        return Result(sp.coerceIn(0, 100), ms, wp, wr)
    }
}
