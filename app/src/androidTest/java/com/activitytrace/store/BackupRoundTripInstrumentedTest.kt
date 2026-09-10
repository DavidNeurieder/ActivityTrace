package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Full backup/restore round-trip against the REAL encrypted SQLCipher database.
 *
 * Deliberately a plain (non-Compose) instrumented class: the exercise
 * ATTACH + `sqlcipher_export` on the live Room connection, which can race the
 * SettingsScreen blocked-apps Flow if that composable is composed at the same
 * time (intermittent "file is not a database" under the ComposeTestRule). No
 * compose content is mounted here, so the connection has no concurrent Flow.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        ActivityTraceDatabase.resetForTesting()
        context.getDatabasePath("activity_trace.db").delete()
        File("${context.getDatabasePath("activity_trace.db").path}-wal").delete()
        File("${context.getDatabasePath("activity_trace.db").path}-shm").delete()
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace/activity_trace.sqlite",
        ).delete()
        File(context.cacheDir, "export_temp").deleteRecursively()
    }

    @Test
    fun exportCsvAndImportSqliteRoundTrip() {
        runBlocking {
            val dao = ActivityTraceDatabase.getInstance(context).captureDao()

            dao.insert(
                CapturedItem(
                    text = "roundtrip one",
                    appPackage = "com.roundtrip",
                    appName = null,
                    contentType = "text",
                    category = null,
                    timestamp = 10000L,
                    metadata = null,
                )
            )
            dao.insert(
                CapturedItem(
                    text = "roundtrip two",
                    appPackage = "com.roundtrip",
                    appName = null,
                    contentType = "notification",
                    category = null,
                    timestamp = 20000L,
                    metadata = null,
                )
            )

            assert(DataExporter.exportToCsv(context, dao) is ExportStatus.Success) { "CSV export should succeed" }

            val dbResult = DatabaseExporter.exportPlaintextDatabase(context)
            assert(dbResult is ExportStatus.Success) { "Database export should succeed, got: ${(dbResult as? ExportStatus.Error)?.message}" }

            val backupFile = File(context.cacheDir, "import_roundtrip_test/backup.sqlite").also {
                it.parentFile?.mkdirs()
                it.delete()
            }
            val roomDb = ActivityTraceDatabase.getInstance(context)
            DatabaseExporter.exportToPlainSqlite(roomDb.openHelper.writableDatabase, backupFile)

            val dedupCount = BackupImporter.importFromBackup(context, Uri.fromFile(backupFile), dao)
            assert(dedupCount == 0) { "Importing same items should dedup to 0, got $dedupCount" }

            val manualBackupDir = File(context.cacheDir, "import_roundtrip_test")
            manualBackupDir.mkdirs()
            val manualBackupFile = File(manualBackupDir, "manual_backup.sqlite").also { it.delete() }
            SQLiteDatabase.openOrCreateDatabase(manualBackupFile, null).use { db ->
                db.execSQL(
                    """
                CREATE TABLE captured_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text TEXT NOT NULL,
                    app_package TEXT NOT NULL,
                    app_name TEXT,
                    content_type TEXT NOT NULL,
                    category TEXT,
                    timestamp INTEGER NOT NULL,
                    metadata TEXT
                )
                """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO captured_items (text, app_package, app_name, content_type, category, timestamp, metadata) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf("new item a", "com.new", "NewApp", "text", null, 30000L, null),
                )
                db.execSQL(
                    "INSERT INTO captured_items (text, app_package, app_name, content_type, category, timestamp, metadata) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf("new item b", "com.new2", "NewApp2", "text", null, 40000L, null),
                )
            }

            val newCount = BackupImporter.importFromBackup(context, Uri.fromFile(manualBackupFile), dao)
            assert(newCount == 2) { "Should import 2 new items, got $newCount" }

            val rededupCount = BackupImporter.importFromBackup(context, Uri.fromFile(manualBackupFile), dao)
            assert(rededupCount == 0) { "Re-importing should dedup to 0, got $rededupCount" }

            val allKeys = dao.getAllItemKeys()
            assert(allKeys.size == 4) { "Total items should be 4, got ${allKeys.size}" }

            manualBackupDir.deleteRecursively()
        }
    }
}