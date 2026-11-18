package com.lavan.claudeusage

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONTokener

/** Signs in to claude.ai in a WebView, reads Settings > Usage, stores the numbers locally. */
class SyncActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var web: WebView
    private lateinit var info: TextView
    private var done = false
    private var redirects = 0

    private val poll = object : Runnable {
        override fun run() {
            check()
            if (!done) handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info = TextView(this).apply { text = "Loading claude.ai…"; setPadding(24, 48, 24, 16) }
        web = WebView(this)
        root.addView(info)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = web.settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = WebViewClient()
        web.loadUrl(USAGE_URL)
        handler.postDelayed(poll, 3000)
    }

    private fun check() {
        val u = web.url ?: return
        if (!u.contains("claude.ai")) return
        val onLogin = u.contains("login") || u.contains("magic") || u.contains("auth")
        if (!u.contains("settings/usage")) {
            if (onLogin) {
                info.text = "Sign in to claude.ai (email login works best; Google sign-in may be blocked)."
            } else if (redirects < 5) {
                redirects++
                web.loadUrl(USAGE_URL)
            }
            return
        }
        info.text = "Reading usage…"
        web.evaluateJavascript("document.body.innerText") { raw ->
            if (done || raw == null) return@evaluateJavascript
            val text = try { JSONTokener(raw).nextValue() as? String } catch (e: Exception) { null }
                ?: return@evaluateJavascript
            val r = UsageParser.parse(text) ?: return@evaluateJavascript
            done = true
            UsageStore.saveSync(this, r.sessionPct, r.sessionResetMs, r.weeklyPct, r.weeklyResetMs)
            UsageWidget.refreshAll(this)
            RefreshWorker.schedule(this)
            Toast.makeText(this, "Synced: session ${r.sessionPct}%", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val USAGE_URL = "https://claude.ai/settings/usage"
    }
}
