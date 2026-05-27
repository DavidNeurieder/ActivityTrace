package com.activitytrace.capture

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ScreenshotCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun handleScreenshot(context: android.content.Context, imageUri: android.net.Uri) {
            // TODO: OCR via ML Kit + ingest extracted text
        }
    }
}
