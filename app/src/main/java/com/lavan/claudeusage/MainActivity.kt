package com.lavan.claudeusage

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var limitInput: EditText
    private lateinit var hoursInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply { text = "Claude Usage"; textSize = 26f })
        status = TextView(this).apply { textSize = 18f; setPadding(0, pad, 0, pad) }
        root.addView(status)

        limitInput = number("Message limit per window")
        hoursInput = number("Window length (hours)")
        root.addView(label("Message limit per window")); root.addView(limitInput)
        root.addView(label("Window length (hours)")); root.addView(hoursInput)

        root.addView(button("Save settings") {
            UsageStore.setConfig(
                this,
                limitInput.text.toString().toIntOrNull() ?: 45,
                hoursInput.text.toString().toIntOrNull() ?: 5
            )
            render()
        })
        root.addView(button("Sync from claude.ai") {
            startActivity(android.content.Intent(this, SyncActivity::class.java))
        })
        root.addView(button("+1 message") { UsageStore.increment(this); render() })
        root.addView(button("Reset") { UsageStore.reset(this); render() })

        root.addView(TextView(this).apply {
            text = "Add the widget: long-press your home screen > Widgets > Claude Usage. " +
                "Tap +1 each time you send a message; the counter clears itself when the window ends."
            setPadding(0, pad, 0, 0)
            gravity = Gravity.START
        })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        limitInput.setText(UsageStore.limit(this).toString())
        hoursInput.setText(UsageStore.windowHours(this).toString())
        val synced = UsageStore.syncedSessionPct(this)
        status.text = if (synced != null)
            "Session $synced% (synced)\n${UsageStore.syncedResetText(this)}\n${UsageStore.weeklyText(this)}"
        else
            "${UsageStore.used(this)} / ${UsageStore.limit(this)} messages (local counter)\n${UsageStore.resetText(this)}"
        UsageWidget.refreshAll(this)
    }

    private fun label(t: String) = TextView(this).apply { text = t; setPadding(0, 16, 0, 0) }
    private fun number(h: String) = EditText(this).apply {
        hint = h; inputType = InputType.TYPE_CLASS_NUMBER
    }
    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; setOnClickListener { onClick() }
    }
}
