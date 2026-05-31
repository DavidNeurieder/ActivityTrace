package com.activitytrace

import android.app.Application
import com.activitytrace.capture.CaptureIngestor
import com.activitytrace.search.SearchEngine
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.RetentionCleanupWorker

class ActivityTraceApplication : Application() {
    lateinit var searchEngine: SearchEngine
        private set
    lateinit var captureDao: com.activitytrace.store.CaptureDao
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            CaptureIngestor.init(this)
            RetentionCleanupWorker.scheduleDaily(this)
            restoreFileIndexingSchedule()
            val db = ActivityTraceDatabase.getInstance(this)
            captureDao = db.captureDao()
            searchEngine = SearchEngine(captureDao)
        } catch (_: Exception) {
            // DB or keystore unavailable; search will be unavailable until app restart
        }
    }

    private fun restoreFileIndexingSchedule() {
        val prefs = getSharedPreferences("activity_trace", MODE_PRIVATE)
        val schedule = prefs.getString("file_index_schedule", "never") ?: "never"
        when (schedule) {
            "daily" -> com.activitytrace.capture.FileIndexingWorker.scheduleDaily(this)
            "never" -> com.activitytrace.capture.FileIndexingWorker.cancelDaily(this)
        }
    }
}
