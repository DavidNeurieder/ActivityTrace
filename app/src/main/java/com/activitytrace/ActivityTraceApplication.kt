package com.activitytrace

import android.app.Application
import com.activitytrace.capture.CaptureIngestor
import com.activitytrace.store.RetentionCleanupWorker

class ActivityTraceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CaptureIngestor.init(this)
        RetentionCleanupWorker.scheduleDaily(this)
    }
}
