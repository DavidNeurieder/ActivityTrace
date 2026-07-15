package com.activitytrace.store

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.activitytrace.R
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object DataExporter {

    private const val TAG = "DataExporter"

    suspend fun exportToJson(
        context: Context,
        dao: CaptureDao,
        onProgress: (ExportStatus) -> Unit = {},
    ): ExportStatus = withContext(Dispatchers.IO) {
        try {
            onProgress(ExportStatus.Progress("Reading items\u2026"))
            val items = dao.getAllItems()

            onProgress(ExportStatus.Progress("Building JSON\u2026"))
            val json = buildJson(items)

            onProgress(ExportStatus.Progress("Writing file\u2026"))
            if (!writeToDownloads(context, "activity_trace.json", "application/json", json)) {
                throw RuntimeException("Failed to write JSON file to Downloads")
            }
            ExportStatus.Success("")
        } catch (e: Exception) {
            Log.e(TAG, "JSON export failed", e)
            ExportErrorLogger.saveErrorLog(context, "JSON export", e)
            ExportStatus.Error(context.getString(R.string.export_failed_log_created))
        }
    }

    fun buildJson(items: List<CapturedItem>): String {
        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("itemCount", items.size)
            put("items", JSONArray(items.map { item ->
                JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("appPackage", item.appPackage)
                    put("appName", item.appName ?: JSONObject.NULL)
                    put("contentType", item.contentType)
                    put("category", item.category ?: JSONObject.NULL)
                    put("timestamp", item.timestamp)
                    put("metadata", item.metadata ?: JSONObject.NULL)
                }
            }))
        }
        return root.toString(2)
    }

    suspend fun exportToCsv(
        context: Context,
        dao: CaptureDao,
        onProgress: (ExportStatus) -> Unit = {},
    ): ExportStatus = withContext(Dispatchers.IO) {
        try {
            onProgress(ExportStatus.Progress("Reading items\u2026"))
            val items = dao.getAllItems()

            onProgress(ExportStatus.Progress("Building CSV\u2026"))
            val csv = buildCsv(items)

            onProgress(ExportStatus.Progress("Writing file\u2026"))
            if (!writeToDownloads(context, "activity_trace.csv", "text/csv; charset=utf-8", csv)) {
                throw RuntimeException("Failed to write CSV file to Downloads")
            }
            ExportStatus.Success("")
        } catch (e: Exception) {
            Log.e(TAG, "CSV export failed", e)
            ExportErrorLogger.saveErrorLog(context, "CSV export", e)
            ExportStatus.Error(context.getString(R.string.export_failed_log_created))
        }
    }

    fun buildCsv(items: List<CapturedItem>): String {
        val sb = StringBuilder()

        sb.append('\uFEFF')
        sb.appendLine("id,text,appPackage,appName,contentType,category,timestamp,metadata")

        for (item in items) {
            sb.append(item.id).append(',')
            sb.append(escapeCsvField(item.text)).append(',')
            sb.append(escapeCsvField(item.appPackage)).append(',')
            sb.append(escapeCsvField(item.appName)).append(',')
            sb.append(escapeCsvField(item.contentType)).append(',')
            sb.append(escapeCsvField(item.category)).append(',')
            sb.append(item.timestamp).append(',')
            sb.appendLine(escapeCsvField(item.metadata))
        }

        return sb.toString()
    }

    private fun escapeCsvField(value: String?): String {
        if (value == null) return ""
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuoting) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun writeToDownloads(context: Context, fileName: String, mimeType: String, content: String): Boolean {
        return if (Build.VERSION.SDK_INT >= 29) {
            writeViaMediaStore(context, fileName, mimeType, content)
        } else {
            writeViaLegacyApi(fileName, content)
        }
    }

    @RequiresApi(29)
    private fun writeViaMediaStore(context: Context, fileName: String, mimeType: String, content: String): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/ActivityTrace")
            if (Build.VERSION.SDK_INT >= 30) {
                put("is_pending", 1)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues,
        ) ?: return false

        if (!writeContent(context.contentResolver.openOutputStream(uri), content)) return false

        if (Build.VERSION.SDK_INT >= 30) {
            val finalValues = ContentValues().apply { put("is_pending", 0) }
            context.contentResolver.update(uri, finalValues, null, null)
        }
        return true
    }

    private fun writeViaLegacyApi(fileName: String, content: String): Boolean {
        val exportDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        ) ?: return false

        val appDir = File(exportDir, "ActivityTrace")
        appDir.mkdirs()

        return writeContent(FileOutputStream(File(appDir, fileName)), content)
    }

    private fun writeContent(stream: OutputStream?, content: String): Boolean {
        stream?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: return false
        return true
    }
}
