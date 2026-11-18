package com.lavan.claudeusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class UsageWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    companion object {
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, UsageWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, build(context))
        }

        private fun build(context: Context): RemoteViews {
            val synced = UsageStore.syncedSessionPct(context)
            val pct: Int
            val reset: String
            if (synced != null) {
                pct = synced
                reset = UsageStore.syncedResetText(context)
            } else {
                pct = (UsageStore.used(context) * 100 / UsageStore.limit(context)).coerceAtMost(100)
                reset = UsageStore.resetText(context)
            }
            val weekly = UsageStore.weeklyPct(context)

            val v = RemoteViews(context.packageName, R.layout.widget_usage)
            v.setTextViewText(R.id.s_pct, "$pct%")
            v.setTextViewText(R.id.s_reset, reset)
            v.setProgressBar(R.id.bar, 100, pct, false)
            v.setTextViewText(R.id.w_pct, if (weekly != null) "$weekly%" else "--%")
            v.setProgressBar(R.id.w_bar, 100, weekly ?: 0, false)
            v.setTextViewText(R.id.w_reset, UsageStore.weeklyResetText(context))

            // Tap anywhere on the widget to sync from claude.ai
            v.setOnClickPendingIntent(
                R.id.root,
                PendingIntent.getActivity(
                    context, 4, Intent(context, SyncActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            )
            return v
        }
    }
}
