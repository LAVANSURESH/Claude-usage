package com.lavan.claudeusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class UsageWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_INC -> { UsageStore.increment(context); refreshAll(context) }
            ACTION_RESET -> { UsageStore.reset(context); refreshAll(context) }
        }
    }

    companion object {
        const val ACTION_INC = "com.lavan.claudeusage.INC"
        const val ACTION_RESET = "com.lavan.claudeusage.RESET"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, UsageWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, build(context))
        }

        private fun action(context: Context, action: String, code: Int): PendingIntent {
            val i = Intent(context, UsageWidget::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun build(context: Context): RemoteViews {
            val synced = UsageStore.syncedSessionPct(context)
            val pct: Int
            val countText: String
            val resetText: String
            if (synced != null) {
                pct = synced
                countText = "$synced% used"
                resetText = UsageStore.syncedResetText(context)
            } else {
                val used = UsageStore.used(context)
                val limit = UsageStore.limit(context)
                pct = (used * 100 / limit).coerceAtMost(100)
                countText = "$used / $limit"
                resetText = UsageStore.resetText(context)
            }
            val v = RemoteViews(context.packageName, R.layout.widget_usage)
            v.setTextViewText(R.id.count, countText)
            v.setTextViewText(R.id.percent, "$pct%")
            v.setTextViewText(R.id.reset, resetText)
            v.setTextViewText(R.id.weekly, UsageStore.weeklyText(context))
            v.setOnClickPendingIntent(
                R.id.btn_sync,
                PendingIntent.getActivity(
                    context, 4, Intent(context, SyncActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            )
            v.setProgressBar(R.id.bar, 100, pct, false)
            v.setTextColor(R.id.percent, if (pct >= 80) Color.parseColor("#D32F2F") else Color.parseColor("#D97757"))
            v.setOnClickPendingIntent(R.id.btn_inc, action(context, ACTION_INC, 1))
            v.setOnClickPendingIntent(R.id.btn_reset, action(context, ACTION_RESET, 2))
            val open = PendingIntent.getActivity(
                context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.title, open)
            return v
        }
    }
}
