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
            val db = ActivityTraceDatabase.getInstance(this)
            captureDao = db.captureDao()
            searchEngine = SearchEngine(captureDao)
        } catch (_: Exception) {
            // DB or keystore unavailable; search will be unavailable until app restart
        }
    }
}
