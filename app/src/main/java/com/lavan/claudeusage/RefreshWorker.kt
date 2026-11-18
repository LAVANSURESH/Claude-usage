package com.lavan.claudeusage

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Background refresh of the widget (Android allows a minimum interval of 15 minutes). */
class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val c = applicationContext
        val out = UsageFetcher.fetch(c)
        val r = out.result
        if (r != null) {
            UsageStore.saveSync(c, r.sessionPct, r.sessionResetMs, r.weeklyPct, r.weeklyResetMs)
            UsageWidget.refreshAll(c)
        }
        UsageStore.setStatus(c, out.status)
        return Result.success()
    }

    companion object {
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(c: Context) {
            val req = PeriodicWorkRequest.Builder(RefreshWorker::class.java, 15, TimeUnit.MINUTES)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(c)
                .enqueueUniquePeriodicWork("usage_refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(c: Context) {
            WorkManager.getInstance(c).enqueue(
                OneTimeWorkRequest.Builder(RefreshWorker::class.java).setConstraints(online).build()
            )
        }
    }
}
