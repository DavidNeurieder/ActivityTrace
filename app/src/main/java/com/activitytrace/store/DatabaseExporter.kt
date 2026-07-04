package com.activitytrace.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseExporter {

    suspend fun export(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val roomDb = ActivityTraceDatabase.getInstance(context)
            val database = roomDb.openHelper.writableDatabase
            val tempFile = File(context.cacheDir, "export_temp/activity_trace.sqlite").also {
                it.parentFile?.mkdirs()
            }
            exportToPlainSqlite(database, tempFile)
            moveToDownloads(context, tempFile)
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun exportToPlainSqlite(database: SupportSQLiteDatabase, outputFile: File) {
        SQLiteDatabase.openOrCreateDatabase(outputFile.absolutePath, null).close()
        val escapedPath = outputFile.absolutePath.replace("'", "''")
        database.execSQL("ATTACH DATABASE '$escapedPath' AS plain KEY ''")
        database.query("SELECT sqlcipher_export('plain')").use { it.moveToFirst() }
        database.execSQL("DETACH DATABASE plain")
    }

    private fun moveToDownloads(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 29) {
            exportViaMediaStore(context, file)
        } else {
            exportViaLegacyApi(file)
        }
    }

    @RequiresApi(29)
    private fun exportViaMediaStore(context: Context, file: File) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "activity_trace.sqlite")
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/ActivityTrace")
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues,
        ) ?: throw RuntimeException("Failed to create MediaStore entry")

        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(file).use { input ->
                input.copyTo(output)
            }
        }
    }

    private fun exportViaLegacyApi(file: File) {
        val exportDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        ) ?: throw RuntimeException("Failed to get external storage directory")

        val appDir = File(exportDir, "ActivityTrace")
        appDir.mkdirs()

        val exportFile = File(appDir, "activity_trace.sqlite")
        FileInputStream(file).use { input ->
            FileOutputStream(exportFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
