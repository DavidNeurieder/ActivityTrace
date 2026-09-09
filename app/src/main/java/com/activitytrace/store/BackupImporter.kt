package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BackupImporter {

    internal const val IMPORT_BATCH_SIZE = 500

    /**
     * Imports from a plaintext `.sqlite` backup via the Settings → Restore path.
     * Streams rows in bounded batches, computes [CapturedItem.contentHash] and
     * lets the database-level [CapturedItem] unique index deduplicate.
     */
    suspend fun importFromBackup(
        context: Context,
        backupUri: Uri,
        dao: CaptureDao,
        onProgress: (ExportStatus) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        onProgress(ExportStatus.Progress("Copying backup\u2026"))
        val tempFile = copyToTempFile(context, backupUri)
        try {
            onProgress(ExportStatus.Progress("Reading backup\u2026"))
            if (!validateBackupSchema(tempFile)) {
                throw IllegalArgumentException("Backup database is missing expected tables or columns")
            }
            onProgress(ExportStatus.Progress("Merging with existing data\u2026"))
            importStreaming(tempFile, dao)
        } finally {
            tempFile.delete()
            tempFile.parentFile?.deleteRecursively()
        }
    }

    /**
     * Opens the backup SQLite file at [backupDbFile], streams rows in bounded
     * batches of [IMPORT_BATCH_SIZE], computes [CapturedItem.contentHash] for
     * each row, and inserts them via Room [dao]. The database's unique index
     * on [CapturedItem] deduplicates silently; [CaptureDao.insertAll] returns
     * `-1` for each ignored duplicate, so the count of newly inserted rows is
     * exact.
     */
    internal suspend fun importStreaming(backupDbFile: File, dao: CaptureDao): Int =
        withContext(Dispatchers.IO) {
            val db = SQLiteDatabase.openDatabase(
                backupDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
            )
            try {
                val cursor = db.rawQuery(
                    """SELECT text, app_package, app_name, content_type, category, timestamp, metadata
                       FROM captured_items""",
                    null,
                )
                var imported = 0
                val batch = mutableListOf<CapturedItem>()
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val text = c.getString(0)
                        val appPackage = c.getString(1)
                        val contentType = c.getString(3)
                        batch.add(
                            CapturedItem(
                                id = 0,
                                text = text,
                                appPackage = appPackage,
                                appName = c.getString(2),
                                contentType = contentType,
                                category = c.getString(4),
                                timestamp = c.getLong(5),
                                metadata = c.getString(6),
                                contentHash = ContentHasher.hash(appPackage, contentType, text),
                            ),
                        )
                        if (batch.size >= IMPORT_BATCH_SIZE) {
                            imported += dao.insertAll(batch.toList()).count { it != -1L }
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) {
                        imported += dao.insertAll(batch).count { it != -1L }
                    }
                }
                imported
            } finally {
                db.close()
            }
        }

    /**
     * Validates that the backup file contains a `captured_items` table with
     * the required columns. Returns `false` for corrupt or incompatible files.
     */
    internal fun validateBackupSchema(file: File): Boolean {
        val db = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) {
            return false
        }
        return try {
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='captured_items'",
                null,
            )
            val hasTable = cursor.use { it.moveToFirst() }
            if (!hasTable) return false
            val colCursor = db.rawQuery("PRAGMA table_info(captured_items)", null)
            val columns = colCursor.use { c ->
                val set = mutableSetOf<String>()
                while (c.moveToNext()) {
                    set.add(c.getString(c.getColumnIndexOrThrow("name")))
                }
                set
            }
            listOf("text", "app_package", "content_type", "timestamp").all { it in columns }
        } catch (_: Exception) {
            false
        } finally {
            db.close()
        }
    }

    /**
     * Legacy non-streaming path retained for unit tests that exercise
     * [readItemsFromSqlite] directly. Production code uses [importStreaming].
     */
    internal fun readItemsFromSqlite(file: File): List<CapturedItem> {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val cursor = db.rawQuery(
                "SELECT id, text, app_package, app_name, content_type, category, timestamp, metadata FROM captured_items",
                null,
            )
            val items = mutableListOf<CapturedItem>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    items.add(
                        CapturedItem(
                            id = c.getLong(0),
                            text = c.getString(1),
                            appPackage = c.getString(2),
                            appName = c.getString(3),
                            contentType = c.getString(4),
                            category = c.getString(5),
                            timestamp = c.getLong(6),
                            metadata = c.getString(7),
                        ),
                    )
                }
            }
            return items
        } finally {
            db.close()
        }
    }

    internal fun copyToTempFile(context: Context, uri: Uri): File {
        val tempDir = File(context.cacheDir, "import_temp")
        tempDir.mkdirs()
        val tempFile = File(tempDir, "backup.sqlite")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw RuntimeException("Failed to open backup file")
        return tempFile
    }
}
