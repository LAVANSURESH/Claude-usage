package com.lavan.claudeusage

import java.time.DayOfWeek
import java.time.ZonedDateTime

/** Parses the visible text of claude.ai > Settings > Usage. Layout may change; returns null if not recognised. */
object UsageParser {
    data class Result(
        val sessionPct: Int,
        val sessionResetMs: Long,   // ms until reset, 0 if the page shows no countdown
        val weeklyPct: Int,         // -1 if not found
        val weeklyResetMs: Long     // ms until weekly reset, 0 if unknown
    )

    private val pct = Regex("""(\d{1,3})\s*%""")
    private val resetIn = Regex(
        """Resets in\s*(?:(\d+)\s*(?:days?|d)\s*)?(?:(\d+)\s*(?:hours?|hrs?|h)\s*)?(?:(\d+)\s*(?:minutes?|mins?|m)\b)?""",
        RegexOption.IGNORE_CASE
    )
    private val resetAt = Regex(
        """Resets\s+(?:on\s+)?(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\w*,?\s+(\d{1,2}):(\d{2})\s*(AM|PM)?""",
        RegexOption.IGNORE_CASE
    )

    private fun relativeMs(block: String): Long {
        val m = resetIn.find(block) ?: return 0L
        val d = m.groupValues[1].toLongOrNull() ?: 0
        val h = m.groupValues[2].toLongOrNull() ?: 0
        val min = m.groupValues[3].toLongOrNull() ?: 0
        return ((d * 24 + h) * 60 + min) * 60_000L
    }

    /** e.g. "Resets Mon 3:30 AM" -> ms until the next such moment in the phone's time zone. */
    private fun absoluteMs(block: String): Long {
        val m = resetAt.find(block) ?: return 0L
        val dow = when (m.groupValues[1].lowercase().take(3)) {
            "mon" -> DayOfWeek.MONDAY; "tue" -> DayOfWeek.TUESDAY; "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY; "fri" -> DayOfWeek.FRIDAY; "sat" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
        var h = m.groupValues[2].toIntOrNull() ?: return 0L
        val min = m.groupValues[3].toIntOrNull() ?: return 0L
        when (m.groupValues[4].uppercase()) {
            "PM" -> if (h < 12) h += 12
            "AM" -> if (h == 12) h = 0
        }
        if (h > 23 || min > 59) return 0L
        val now = ZonedDateTime.now()
        var t = now.withHour(h).withMinute(min).withSecond(0).withNano(0)
        while (t.dayOfWeek != dow || !t.isAfter(now)) t = t.plusDays(1)
        return java.time.Duration.between(now, t).toMillis()
    }

    fun parse(raw: String): Result? {
        val t = raw.replace(' ', ' ')
        val s = t.indexOf("Current session", ignoreCase = true)
        if (s < 0) return null
        val w = t.indexOf("Weekly", s, ignoreCase = true)

        val sBlock = if (w > s) t.substring(s, w) else t.substring(s, minOf(t.length, s + 300))
        val sp = pct.find(sBlock)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val sMs = relativeMs(sBlock)

        var wp = -1
        var wMs = 0L
        if (w > s) {
            val wBlock = t.substring(w, minOf(t.length, w + 300))
            wp = pct.find(wBlock)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            wMs = relativeMs(wBlock).takeIf { it > 0 } ?: absoluteMs(wBlock)
        }
        return Result(sp.coerceIn(0, 100), sMs, wp, wMs)
    }
}
