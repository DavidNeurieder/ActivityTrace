package com.activitytrace.capture

import android.content.Context
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.activitytrace.store.ActivityTraceDatabase
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class FileIndexingWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result = runBlocking {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStrings = prefs.getStringSet(PREF_DIRECTORY_URIS, emptySet()) ?: emptySet()
        if (uriStrings.isEmpty()) return@runBlocking Result.success()

        val db = ActivityTraceDatabase.getInstance(applicationContext)
        val dao = db.captureDao()

        uriStrings.forEach { uriString ->
            val uri = Uri.parse(uriString)
            FileIndexer.indexDirectory(applicationContext, uri, dao)
        }

        prefs.edit().putLong(PREF_LAST_RUN, System.currentTimeMillis()).apply()
        Result.success()
    }

    companion object {
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
