package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BackupImporter {

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
            val backupItems = readItemsFromSqlite(tempFile)

            onProgress(ExportStatus.Progress("Merging with existing data\u2026"))
            val existingKeys = dao.getAllItemKeys().toSet()
            val newItems = backupItems.filter { item ->
                CaptureDao.ItemKey(item.text, item.timestamp, item.appPackage) !in existingKeys
            }.map { it.copy(id = 0) }

            if (newItems.isEmpty()) return@withContext 0

            onProgress(ExportStatus.Progress("Importing ${newItems.size} items\u2026"))
            dao.insertAll(newItems)
            newItems.size
        } finally {
            tempFile.delete()
            tempFile.parentFile?.deleteRecursively()
        }
    }

    internal fun readItemsFromSqlite(file: File): List<CapturedItem> {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val cursor = db.rawQuery("SELECT id, text, app_package, app_name, content_type, category, timestamp, metadata FROM captured_items", null)
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
                        )
                    )
                }
            }
            return items
        } finally {
            db.close()
        }
    }

    private fun copyToTempFile(context: Context, uri: Uri): File {
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
