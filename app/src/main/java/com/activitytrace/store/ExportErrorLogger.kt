package com.activitytrace.store

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportErrorLogger {

    private const val LOG_DIR = "ActivityTrace"

    fun saveErrorLog(context: Context, operation: String, exception: Exception) {
        val content = buildLogContent(operation, exception)

        if (!tryWriteToDownloads(context, content)) {
            writeToFallbackDir(context, content)
        }
    }

    private fun buildLogContent(operation: String, exception: Exception): String {
        val sb = StringBuilder()
        sb.appendLine("=== Activity Trace Export Error ===")
        sb.appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Operation: $operation")
        sb.appendLine("Exception: ${exception.javaClass.name}: ${exception.message}")
        sb.appendLine("Stack trace:")
        exception.stackTrace.forEach { sb.appendLine("  $it") }
        var cause = exception.cause
        while (cause != null) {
            sb.appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
            cause.stackTrace.forEach { sb.appendLine("  $it") }
            cause = cause.cause
        }
        return sb.toString()
    }

    private fun tryWriteToDownloads(context: Context, content: String): Boolean {
        val fileName = logFileName()
        return if (Build.VERSION.SDK_INT >= 29) {
            writeViaMediaStore(context, fileName, content)
        } else {
            writeViaLegacyApi(fileName, content)
        }
    }

    private fun logFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "export_error_$timestamp.log"
    }

    @RequiresApi(29)
    private fun writeViaMediaStore(context: Context, fileName: String, content: String): Boolean {
        try {
            val pendingValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/ActivityTrace")
                if (Build.VERSION.SDK_INT >= 30) {
                    put("is_pending", 1)
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                pendingValues,
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: return false

            if (Build.VERSION.SDK_INT >= 30) {
                val finalValues = ContentValues().apply { put("is_pending", 0) }
                context.contentResolver.update(uri, finalValues, null, null)
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun writeViaLegacyApi(fileName: String, content: String): Boolean {
        try {
            val exportDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS,
            ) ?: return false

            val appDir = File(exportDir, LOG_DIR)
            appDir.mkdirs()

            FileOutputStream(File(appDir, fileName)).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun writeToFallbackDir(context: Context, content: String) {
        try {
            val dir: File
            if (Environment.getExternalStorageState(context.getExternalFilesDir(null)) == Environment.MEDIA_MOUNTED) {
                val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (documentsDir != null) {
                    dir = File(documentsDir, LOG_DIR)
                } else {
                    dir = File(context.filesDir, LOG_DIR)
                }
            } else {
                dir = File(context.filesDir, LOG_DIR)
            }
            dir.mkdirs()
            File(dir, logFileName()).bufferedWriter().use { it.write(content) }
        } catch (_: Exception) {
            // last resort — silently ignore
        }
    }
}
