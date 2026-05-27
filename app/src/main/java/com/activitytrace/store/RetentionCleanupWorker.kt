package com.activitytrace.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class RetentionCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result = runBlocking {
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val db = ActivityTraceDatabase.getInstance(applicationContext)
        db.captureDao().deleteOlderThan(cutoff)
        Result.success()
    }

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                scheduleDaily(context)
            }
        }
    }

    companion object {
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(
                1, TimeUnit.DAYS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "retention_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
