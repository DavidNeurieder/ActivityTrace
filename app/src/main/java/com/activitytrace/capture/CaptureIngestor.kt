package com.activitytrace.capture

import android.content.ContentValues
import android.content.Context
import android.provider.BaseColumns
import com.activitytrace.model.CapturedItem
import com.activitytrace.store.ActivityTraceDatabase

object CaptureIngestor {
    private var db: ActivityTraceDatabase? = null

    fun init(ctx: Context) {
        db = ActivityTraceDatabase.getInstance(ctx.applicationContext)
    }

    suspend fun ingest(
        text: String,
        appPackage: String,
        contentType: String,
        metadata: String? = null,
    ) {
        try {
            db?.captureDao()?.insert(
                CapturedItem(
                    text = text,
                    appPackage = appPackage,
                    contentType = contentType,
                    timestamp = System.currentTimeMillis(),
                    metadata = metadata,
                )
            )
        } catch (_: Exception) {
        }
    }
}
