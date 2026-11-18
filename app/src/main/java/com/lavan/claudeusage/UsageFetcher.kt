package com.lavan.claudeusage

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

/**
 * Reads the same JSON claude.ai's Settings > Usage page uses, with the cookies saved by the Sync login.
 * Unofficial endpoint: may change or be blocked at any time.
 */
object UsageFetcher {
    data class Out(val result: UsageParser.Result?, val status: String)

    private const val BASE = "https://claude.ai/api/organizations"

    fun fetch(c: Context): Out {
        val cookie = CookieManager.getInstance().getCookie("https://claude.ai")
        if (cookie.isNullOrEmpty()) return Out(null, "not signed in - tap the widget to sign in")
        val ua = WebSettings.getDefaultUserAgent(c).replace("; wv", "")
        return try {
            var org = UsageStore.org(c)
            if (org.isEmpty()) {
                val (code, body) = get(BASE, cookie, ua)
                if (code != 200) return Out(null, "HTTP $code (organizations) - sign in again")
                org = pickOrg(JSONArray(body)) ?: return Out(null, "no organization found")
                UsageStore.setOrg(c, org)
            }
            val (code, body) = get("$BASE/$org/usage", cookie, ua)
            if (code != 200) {
                if (code == 401 || code == 403 || code == 404) UsageStore.setOrg(c, "")
                return Out(null, "HTTP $code (usage) - sign in again")
            }
            val root = JSONObject(body)
            val five = window(root.optJSONObject("five_hour"))
                ?: return Out(null, "unexpected response: " + root.keys().asSequence().take(8).joinToString(","))
            val seven = window(root.optJSONObject("seven_day"))
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            Out(
                UsageParser.Result(five.first, five.second, seven?.first ?: -1, seven?.second ?: 0L),
                "updated $time"
            )
        } catch (e: Exception) {
            Out(null, "error: ${e.javaClass.simpleName}")
        }
    }

    private fun get(url: String, cookie: String, ua: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("User-Agent", ua)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    /** Prefer an organization that has chat capability; otherwise the first one. */
    private fun pickOrg(arr: JSONArray): String? {
        var first: String? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("uuid")
            if (id.isEmpty()) continue
            if (first == null) first = id
            val caps = o.optJSONArray("capabilities")
            if (caps != null) for (j in 0 until caps.length()) if (caps.optString(j) == "chat") return id
        }
        return first
    }

    /** Returns (percent used, ms until reset or 0) from a {"utilization":..,"resets_at":..} style object. */
    private fun window(o: JSONObject?): Pair<Int, Long>? {
        if (o == null) return null
        var pct: Double? = null
        for (k in listOf("utilization", "used_percentage", "percent_used", "percentage")) {
            if (o.has(k) && !o.isNull(k)) { pct = o.optDouble(k); break }
        }
        if (pct == null) {
            for (k in o.keys()) {
                if (!k.contains("reset") && o.opt(k) is Number) { pct = o.optDouble(k); break }
            }
        }
        if (pct == null || pct.isNaN()) return null
        var ms = 0L
        val r = o.optString("resets_at", "")
        if (r.isNotEmpty() && r != "null") {
            ms = try {
                (OffsetDateTime.parse(r).toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L)
            } catch (e: Exception) { 0L }
        }
        return pct.toInt().coerceIn(0, 100) to ms
    }
}
