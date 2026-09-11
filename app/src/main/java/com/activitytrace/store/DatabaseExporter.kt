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
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseExporter {

    private const val TAG = "DatabaseExporter"

    suspend fun exportPlaintextDatabase(context: Context): ExportStatus = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "export_temp/activity_trace.sqlite")
        try {
            tempFile.parentFile?.mkdirs()
            exportToPlainSqlite(context, tempFile)
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

    /**
     * Opens a short-lived dedicated SQLCipher connection to the encrypted
     * database (identical passphrase to Room's own) and runs
     * [exportToPlainSqlite] on that standalone connection. Because the ATTACH +
     * sqlcipher_export never touches Room's pooled write/read connections the
     * export is immune to the concurrent-flow / concurrent-write collisions that
     * dropped the shared-connection variant with "file is not a database" /
     * SQLiteDiskIOException on real hardware.
     */
    internal fun exportToPlainSqlite(context: Context, outputFile: File) {
        val passphrase = EncryptionManager.getOrCreateKey(context)
        val factory = SupportOpenHelperFactory(passphrase)
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(context.applicationContext)
            .name(ActivityTraceDatabase.DB_NAME)
            .callback(NoOpCallback())
            .build()
        val helper = factory.create(configuration)
        val database = helper.writableDatabase
        try {
            database.query("PRAGMA busy_timeout = 10000").close()
            exportToPlainSqlite(database, outputFile)
        } finally {
            helper.close()
        }
    }

    /**
     * Core export path. Unit-tested against a mocked [SupportSQLiteDatabase].
     * Production callers should prefer [exportToPlainSqlite] (the context-
     * accepting overload) so the ATTACH + sqlcipher_export runs on its own
     * dedicated connection.
     */
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

    /**
     * Streams an already-encapsulated backup file to Downloads, so a large
     * backup is never buffered whole in memory.
     */
    internal suspend fun exportFileToDownloads(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String,
    ) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 29) {
            exportViaMediaStore(context, displayName, mimeType) { output ->
                FileInputStream(file).use { it.copyTo(output) }
            }
        } else {
            exportViaLegacyApi(displayName) { output ->
                FileInputStream(file).use { it.copyTo(output) }
            }
        }
    }

    @RequiresApi(29)
    private fun exportViaMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        write: (java.io.OutputStream) -> Unit,
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
            write(output)
        } ?: throw RuntimeException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= 30) {
            val finalValues = ContentValues().apply { put("is_pending", 0) }
            context.contentResolver.update(uri, finalValues, null, null)
        }
    }

    private fun exportViaLegacyApi(
        displayName: String,
        write: (java.io.OutputStream) -> Unit,
    ) {
        val exportDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        ) ?: throw RuntimeException("Failed to get external storage directory")

        val appDir = File(exportDir, "ActivityTrace")
        appDir.mkdirs()
        File(appDir, displayName).outputStream().use { write(it) }
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

    /**
     * Minimal callback for the raw SQLCipher [SupportSQLiteOpenHelper] opened
     * just for the export. The real DB schema was created by Room's own
     * [RoomDatabase.Callback]; the export helper never needs to run
     * [onCreate] / [onUpgrade] — it opens an existing file.
     */
    private class NoOpCallback : SupportSQLiteOpenHelper.Callback(ActivityTraceDatabase.CURRENT_VERSION) {
        override fun onOpen(db: SupportSQLiteDatabase) = Unit
        override fun onCreate(db: SupportSQLiteDatabase) = Unit
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
