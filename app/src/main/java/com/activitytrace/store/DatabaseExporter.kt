package com.activitytrace.store

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseExporter {

    suspend fun export(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath("activity_trace.db")
            if (!dbFile.exists()) return@withContext false

            if (Build.VERSION.SDK_INT >= 29) {
                exportViaMediaStore(context, dbFile)
            } else {
                exportViaLegacyApi(dbFile)
            }
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(29)
    private fun exportViaMediaStore(context: Context, dbFile: File): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "activity_trace.db")
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/ActivityTrace")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues,
        ) ?: return false

        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(dbFile).use { input ->
                input.copyTo(output)
            }
        }
        return true
    }

    private fun exportViaLegacyApi(dbFile: File): Boolean {
        val exportDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        ) ?: return false

        val appDir = File(exportDir, "ActivityTrace")
        appDir.mkdirs()

        val exportFile = File(appDir, "activity_trace.db")
        FileInputStream(dbFile).use { input ->
            FileOutputStream(exportFile).use { output ->
                input.copyTo(output)
            }
        }
        return true
    }
}
