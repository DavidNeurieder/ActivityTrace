package com.activitytrace.store

import android.content.Context
import android.net.Uri
import android.util.Log
import com.activitytrace.R
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object EncryptedBackupExporter {

    private const val TAG = "EncryptedBackupExporter"
    private const val BACKUP_FILE_NAME = "activity_trace_backup.activitytrace"

    suspend fun export(context: Context, password: CharArray): ExportStatus =
        withContext(Dispatchers.IO) {
            if (password.isEmpty()) {
                return@withContext ExportStatus.Error(context.getString(R.string.backup_password_required))
            }
            val tempDir = File(context.cacheDir, "encrypted_export")
            try {
                tempDir.mkdirs()
                val plaintextSqlite = File(tempDir, "plain.sqlite")
                val roomDb = ActivityTraceDatabase.getInstance(context)
                val database = roomDb.openHelper.writableDatabase
                DatabaseExporter.exportToPlainSqlite(database, plaintextSqlite)
                val databaseBytes = plaintextSqlite.readBytes()

                val metadata = JSONObject().apply {
                    put("schema_version", ActivityTraceDatabase.CURRENT_VERSION)
                    put("exported_at", System.currentTimeMillis())
                }.toString()

                val payload = BackupPayload.zip(databaseBytes, metadata)
                plaintextSqlite.delete()
                val encrypted = BackupCrypto.encrypt(payload, password)

                DatabaseExporter.writeBytesToDownloads(
                    context,
                    encrypted,
                    BACKUP_FILE_NAME,
                    "application/octet-stream",
                )
                ExportStatus.Success(context.getString(R.string.encrypted_backup_exported))
            } catch (e: Exception) {
                Log.e(TAG, "Encrypted export failed", e)
                ExportErrorLogger.saveErrorLog(context, "encrypted database export", e)
                ExportStatus.Error(context.getString(R.string.export_failed_log_created))
            } finally {
                tempDir.deleteRecursively()
            }
        }

    suspend fun import(
        context: Context,
        backupUri: Uri,
        password: CharArray,
        dao: CaptureDao,
        onProgress: (ExportStatus) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        if (password.isEmpty()) {
            throw IllegalArgumentException(context.getString(R.string.backup_password_required))
        }
        onProgress(ExportStatus.Progress(context.getString(R.string.progress_copying_backup)))
        val tempDir = File(context.cacheDir, "encrypted_import")
        tempDir.mkdirs()
        val backupFile = File(tempDir, "backup.activitytrace")
        try {
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                backupFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw RuntimeException("Failed to open backup file")

            onProgress(ExportStatus.Progress(context.getString(R.string.progress_reading_backup)))
            val payload = BackupCrypto.decrypt(backupFile.readBytes(), password)
            val (databaseBytes, _) = BackupPayload.unzip(payload)

            val restoredSqlite = File(tempDir, "restored.sqlite")
            restoredSqlite.writeBytes(databaseBytes)
            onProgress(ExportStatus.Progress(context.getString(R.string.progress_merging_backup)))
            val items = BackupImporter.readItemsFromSqlite(restoredSqlite)
            BackupImporter.importItems(items, dao)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}