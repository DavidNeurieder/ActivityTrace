package com.activitytrace

import android.app.Application
import com.activitytrace.capture.CaptureIngestor
import com.activitytrace.search.SearchEngine
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.RetentionCleanupWorker

class ActivityTraceApplication : Application() {
    lateinit var searchEngine: SearchEngine
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            CaptureIngestor.init(this)
            RetentionCleanupWorker.scheduleDaily(this)
            val db = ActivityTraceDatabase.getInstance(this)
            searchEngine = SearchEngine(db.captureDao())
        } catch (_: Exception) {
            // DB or keystore unavailable; search will be unavailable until app restart
        }
    }
}
