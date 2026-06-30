package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.util.Log
import com.activitytrace.store.ActivityTraceDatabase
import java.util.concurrent.TimeUnit

class FileIndexingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStrings = prefs.getStringSet(PREF_DIRECTORY_URIS, emptySet()) ?: emptySet()
        if (uriStrings.isEmpty()) return Result.success()

        val db = ActivityTraceDatabase.getInstance(applicationContext)
        val dao = db.captureDao()

        uriStrings.forEach { uriString ->
            try {
                val uri = Uri.parse(uriString)
                FileIndexer.indexDirectory(applicationContext, uri, dao)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to index $uriString", e)
            }
        }

        prefs.edit().putLong(PREF_LAST_RUN, System.currentTimeMillis()).apply()
        return Result.success()
    }

    companion object {
        private const val TAG = "FileIndexingWorker"
        private const val PREFS_NAME = "activity_trace"
        const val PREF_DIRECTORY_URIS = "file_index_directory_uris"
        const val PREF_LAST_RUN = "file_index_last_run"
        const val PREF_SCHEDULE = "file_index_schedule"
        private const val WORK_NAME_DAILY = "file_indexing_daily"
        private const val WORK_NAME_MANUAL = "file_indexing_manual"

        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<FileIndexingWorker>(
                1, TimeUnit.DAYS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancelDaily(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY)
        }

        fun triggerNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FileIndexingWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_MANUAL,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
