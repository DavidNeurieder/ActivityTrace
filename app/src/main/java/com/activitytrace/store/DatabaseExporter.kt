package com.activitytrace.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.activitytrace.R
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseExporter {

    private const val TAG = "DatabaseExporter"

    suspend fun exportPlaintextDatabase(context: Context): ExportStatus = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "export_temp/activity_trace.sqlite")
        try {
            tempFile.parentFile?.mkdirs()
            val roomDb = ActivityTraceDatabase.getInstance(context)
            val database = roomDb.openHelper.writableDatabase
            exportToPlainSqlite(database, tempFile)
            moveToDownloads(context, tempFile)
            ExportStatus.Success("")
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            ExportErrorLogger.saveErrorLog(context, "database export", e)
            ExportStatus.Error(context.getString(R.string.export_failed_log_created))
        } finally {
            tempFile.delete()
            tempFile.parentFile?.delete()
        }
    }

    internal fun exportToPlainSqlite(database: SupportSQLiteDatabase, outputFile: File) {
        outputFile.delete()
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

    internal suspend fun writeBytesToDownloads(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) {
            exportBytesViaMediaStore(context, bytes, displayName, mimeType)
        } else {
            exportBytesViaLegacyApi(bytes, displayName)
        }
    }

    @RequiresApi(29)
    private fun exportBytesViaMediaStore(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/ActivityTrace")
            if (Build.VERSION.SDK_INT >= 30) {
                put("is_pending", 1)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues,
        ) ?: throw RuntimeException("Failed to create MediaStore entry")

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: throw RuntimeException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= 30) {
            val finalValues = ContentValues().apply { put("is_pending", 0) }
            context.contentResolver.update(uri, finalValues, null, null)
        }
    }

    private fun exportBytesViaLegacyApi(bytes: ByteArray, displayName: String) {
        val exportDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        ) ?: throw RuntimeException("Failed to get external storage directory")

        val appDir = File(exportDir, "ActivityTrace")
        appDir.mkdirs()
        File(appDir, displayName).writeBytes(bytes)
    }

    @RequiresApi(29)
    private fun exportViaMediaStore(context: Context, file: File) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "activity_trace.sqlite")
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/ActivityTrace")
            if (Build.VERSION.SDK_INT >= 30) {
                put("is_pending", 1)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues,
        ) ?: throw RuntimeException("Failed to create MediaStore entry")

        context.contentResolver.openOutputStream(uri)?.use { output ->
            FileInputStream(file).use { input ->
                input.copyTo(output)
            }
        } ?: throw RuntimeException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= 30) {
            val finalValues = ContentValues().apply { put("is_pending", 0) }
            context.contentResolver.update(uri, finalValues, null, null)
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
