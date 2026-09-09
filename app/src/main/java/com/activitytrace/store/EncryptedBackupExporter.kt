package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.Log
import com.activitytrace.R
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.zip.ZipOutputStream

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

                val stagingSqlite = File(tempDir, "plain.sqlite")
                val roomDb = ActivityTraceDatabase.getInstance(context)
                val database = roomDb.openHelper.writableDatabase
                val escapedPath = stagingSqlite.absolutePath.replace("'", "''")
                database.execSQL(
                    "ATTACH DATABASE '$escapedPath' AS plain KEY ''",
                )
                database.query("SELECT sqlcipher_export('plain')").use { it.moveToFirst() }
                database.execSQL("DETACH DATABASE plain")

                val metadata = JSONObject().apply {
                    put("schema_version", ActivityTraceDatabase.CURRENT_VERSION)
                    put("exported_at", System.currentTimeMillis())
                }.toString()

                val payloadZip = File(tempDir, "payload.zip")
                payloadZip.outputStream().use { payloadOut ->
                    val bounded = BoundedOutputStream(payloadOut, BackupLimits.MAX_PAYLOAD_BYTES)
                    ZipOutputStream(bounded).use { zip ->
                        FileInputStream(stagingSqlite).use { staging ->
                            BackupPayload.writeEntries(staging, metadata, zip)
                        }
                    }
                }

                val envelope = File(tempDir, "backup.envelope")
                envelope.outputStream().use { envelopeOut ->
                    FileInputStream(payloadZip).use { payload ->
                        BackupCrypto.openEncryptStream(
                            envelopeOut,
                            password,
                            payloadZip.length(),
                        ).use { cipherOut ->
                            payload.copyTo(cipherOut, BackupEnvelope.IO_CHUNK)
                        }
                    }
                }
                stagingSqlite.delete()
                payloadZip.delete()

                DatabaseExporter.exportFileToDownloads(
                    context,
                    envelope,
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
    ): RestoreResult = withContext(Dispatchers.IO) {
        if (password.isEmpty()) {
            return@withContext RestoreResult.InvalidBackup(
                context.getString(R.string.backup_password_required),
            )
        }
        val tempDir = File(context.cacheDir, "encrypted_import")
        val backupFile = File(tempDir, "backup.activitytrace")
        val decryptedPayload = File(tempDir, "payload.zip")
        val restoredSqlite = File(tempDir, "restored.sqlite")
        try {
            tempDir.mkdirs()
            onProgress(ExportStatus.Progress(context.getString(R.string.progress_copying_backup)))
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                backupFile.outputStream().use { output ->
                    input.copyTo(
                        BoundedOutputStream(output, BackupLimits.MAX_ENCRYPTED_BACKUP_BYTES),
                        BackupEnvelope.IO_CHUNK,
                    )
                }
            } ?: throw IOException("Failed to open backup file")

            onProgress(ExportStatus.Progress(context.getString(R.string.progress_reading_backup)))
            backupFile.inputStream().use { input ->
                decryptedPayload.outputStream().use { output ->
                    BackupCrypto.decryptTo(
                        input,
                        output,
                        password,
                        BackupLimits.MAX_CIPHERTEXT_BYTES,
                    )
                }
            }
            decryptedPayload.inputStream().use { input ->
                restoredSqlite.outputStream().use { output ->
                    BackupPayload.unzipDatabase(
                        input,
                        BackupLimits.MAX_DATABASE_ENTRY_BYTES,
                        BackupLimits.MAX_METADATA_ENTRY_BYTES,
                        BoundedOutputStream(output, BackupLimits.MAX_DATABASE_ENTRY_BYTES),
                    )
                }
            }

            onProgress(ExportStatus.Progress(context.getString(R.string.progress_merging_backup)))
            val items = BackupImporter.readItemsFromSqlite(restoredSqlite)
            val imported = BackupImporter.importItems(items, dao)
            RestoreResult.Success(imported)
        } catch (e: GeneralSecurityException) {
            RestoreResult.InvalidBackup(context.getString(R.string.restore_invalid_backup))
        } catch (e: BackupTooLargeException) {
            RestoreResult.InvalidBackup(context.getString(R.string.restore_backup_too_large))
        } catch (e: SQLiteException) {
            RestoreResult.InvalidBackup(context.getString(R.string.restore_backup_database_unreadable))
        } catch (e: IllegalArgumentException) {
            RestoreResult.InvalidBackup(context.getString(R.string.restore_invalid_backup))
        } catch (e: IOException) {
            RestoreResult.Failed(context.getString(R.string.restore_failed), e)
        } catch (e: Exception) {
            ExportErrorLogger.saveErrorLog(context, "encrypted database restore", e)
            RestoreResult.Failed(context.getString(R.string.restore_failed), e)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}