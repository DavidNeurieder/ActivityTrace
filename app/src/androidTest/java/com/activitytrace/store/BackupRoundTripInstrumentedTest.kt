package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Full backup/restore round-trip against the REAL encrypted SQLCipher database.
 *
 * Deliberately a plain (non-Compose) instrumented class: the export exercises
 * ATTACH + `sqlcipher_export` through a short-lived dedicated SQLCipher
 * connection (never Room's pooled write connection), and the restore path
 * performs raw ATTACH-based streaming writes while the settings blocked-apps
 * Flow is never mounted here. Keep Room-exercising round-trips out of compose
 * UI tests.
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
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace",
        ).deleteRecursively()
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
            DatabaseExporter.exportToPlainSqlite(context, backupFile)

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

    /**
     * The dedicated-connection export must succeed while room is actively
     * serving concurrent consumers (paged recent-list flow, demo-count flow).
     * Before the dedicated-connection change this ATTACH + sqlcipher_export on
     * the shared Room write connection raced those concurrent reads and threw
     * intermittent "file is not a database" / SQLiteDiskIOException on real
     * hardware.
     */
    @Test
    fun plaintext_export_succeeds_under_concurrent_room_reads() {
        runBlocking {
            // Other instrumented classes leave rows in the shared activity_trace.db
            // without cleanup, so establish a known baseline before asserting exact
            // counts (independent of test-class execution order).
            ActivityTraceDatabase.resetForTesting()
            val db = context.getDatabasePath(ActivityTraceDatabase.DB_NAME)
            db.delete()
            File("${db.path}-wal").delete()
            File("${db.path}-shm").delete()

            val dao = ActivityTraceDatabase.getInstance(context).captureDao()

            for (i in 1..60) {
                dao.insert(
                    CapturedItem(
                        text = "stress_sentinel_$i",
                        appPackage = "com.stress",
                        appName = null,
                        contentType = "text",
                        category = null,
                        timestamp = i.toLong(),
                        metadata = null,
                    )
                )
            }

            val readJob = launch(Dispatchers.Default) {
                repeat(4) {
                    launch(Dispatchers.Default) {
                        dao.recentPagedQuery(null, null, null, null, 20L, 0L).collect { }
                    }
                }
            }
            val countJob = launch(Dispatchers.Default) {
                dao.demoCountFlow("com.activitytrace.demo.showcase").collect { }
            }
            delay(250)

            try {
                val backupFile = File(context.cacheDir, "concurrent_export_test/backup.sqlite").also {
                    it.parentFile?.mkdirs()
                    it.delete()
                }
                DatabaseExporter.exportToPlainSqlite(context, backupFile)

                assert(backupFile.exists()) { "Dedicated-connection export should produce a file" }
                SQLiteDatabase.openDatabase(
                    backupFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { db ->
                    val texts = db.rawQuery("SELECT text FROM captured_items ORDER BY text", null).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) add(cursor.getString(0))
                        }
                    }
                    val expected = (1..60).map { "stress_sentinel_$it" }
                    val missing = expected.filter { it !in texts }
                    assert(missing.isEmpty()) { "Exported DB missing: $missing (got ${texts.size} rows)" }
                    val count = texts.size
                    assert(count == 60) { "Exported database should contain all 60 rows, got $count" }
                }
                backupFile.delete()
            } finally {
                readJob.cancel()
                countJob.cancel()
            }
        }
    }
}