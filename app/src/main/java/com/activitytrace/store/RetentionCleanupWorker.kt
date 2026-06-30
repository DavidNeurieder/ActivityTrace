package com.activitytrace.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RetentionCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = getRetentionDays(applicationContext)
            if (retentionDays <= 0) return Result.success()
            val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
            val db = ActivityTraceDatabase.getInstance(applicationContext)
            db.captureDao().deleteOlderThan(cutoff)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Retention cleanup failed", e)
            Result.failure()
        }
    }

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                scheduleDaily(context)
            }
        }
    }

    companion object {
        private const val TAG = "RetentionCleanup"
        private const val PREFS_NAME = "activity_trace"
        private const val PREF_RETENTION_DAYS = "retention_days"
        private const val DEFAULT_RETENTION_DAYS = 7

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

        fun getRetentionDays(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(PREF_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        }

        fun setRetentionDays(context: Context, days: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_RETENTION_DAYS, days)
                .apply()
        }
    }
}
