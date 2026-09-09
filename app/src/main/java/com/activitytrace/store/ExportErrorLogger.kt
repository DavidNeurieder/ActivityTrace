package com.activitytrace.store

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportErrorLogger {

    private const val LOG_DIR = "export_errors"

    fun saveErrorLog(context: Context, operation: String, exception: Exception) {
        val dir = logDir(context)
        dir.mkdirs()
        val content = buildLogContent(operation, exception)
        File(dir, logFileName()).writeText(content, Charsets.UTF_8)
    }

    fun getLogFiles(context: Context): List<File> =
        logDir(context).listFiles { f -> f.name.endsWith(".log") }?.toList().orEmpty()

    fun shareLogs(context: Context): Intent {
        val files = getLogFiles(context)
        if (files.isEmpty()) return Intent()
        val uris = files.map { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun clearLogs(context: Context) {
        logDir(context).deleteRecursively()
    }

    private fun logDir(context: Context): File = File(context.cacheDir, LOG_DIR)

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

    private fun logFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val nonce = Integer.toHexString((Math.random() * 0xFFFF).toInt())
        return "export_error_${timestamp}_$nonce.log"
    }
}
