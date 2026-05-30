package com.activitytrace.capture

import android.content.Context
import android.util.Log
import com.activitytrace.model.CapturedItem
import com.activitytrace.store.ActivityTraceDatabase

object CaptureIngestor {
    private var db: ActivityTraceDatabase? = null
    private const val TAG = "CaptureIngestor"

    fun init(ctx: Context) {
        db = ActivityTraceDatabase.getInstance(ctx.applicationContext)
    }

    suspend fun ingest(
        text: String,
        appPackage: String,
        contentType: String,
        metadata: String? = null,
        appName: String? = null,
        category: String? = null,
        timestamp: Long? = null,
    ) {
        try {
            db?.captureDao()?.insert(
                CapturedItem(
                    text = text,
                    appPackage = appPackage,
                    appName = appName,
                    contentType = contentType,
                    category = category,
                    timestamp = timestamp ?: System.currentTimeMillis(),
                    metadata = metadata,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ingest item (type=$contentType, pkg=$appPackage)", e)
        }
    }
}
