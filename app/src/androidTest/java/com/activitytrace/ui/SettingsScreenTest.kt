package com.activitytrace.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.model.CapturedItem
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.BackupImporter
import com.activitytrace.store.DataExporter
import com.activitytrace.store.DatabaseExporter
import com.activitytrace.store.RetentionCleanupWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(onBack = {})
            }
        }
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        RetentionCleanupWorker.setRetentionDays(context, 7)
        context.getDatabasePath("activity_trace.db").delete()
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace/activity_trace.sqlite",
        ).delete()
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace/activity_trace.json",
        ).delete()
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace/activity_trace.csv",
        ).delete()
        File(context.cacheDir, "export_temp").deleteRecursively()
    }

    @Test
    fun showsPermissionsSection() {
        composeTestRule.onNodeWithText("Permissions").assertExists()
    }

    @Test
    fun showsNotificationAccessRow() {
        composeTestRule.onNodeWithText("Notification access").assertExists()
    }

    @Test
    fun showsAccessibilityServiceRow() {
        composeTestRule.onNodeWithText("Accessibility service").assertExists()
    }

    @Test
    fun showsPermissionsExplanation() {
        composeTestRule.onNodeWithText(
            "Notification Access captures notifications in real time. " +
            "If blocked by Restricted Settings, go to " +
            "Settings → Apps → Activity Trace → Allow restricted settings. " +
            "Alternatively, enable the Accessibility Service below (Android 14+)."
        ).assertExists()
    }

    @Test
    fun showsRetentionSection() {
        composeTestRule.onNodeWithText("Retention period").assertExists()
    }

    @Test
    fun showsRetentionOptions() {
        composeTestRule.onAllNodesWithText("Never")[0].assertExists()
        composeTestRule.onNodeWithText("3 days").assertExists()
        composeTestRule.onNodeWithText("7 days").assertExists()
        composeTestRule.onNodeWithText("14 days").assertExists()
        composeTestRule.onNodeWithText("30 days").assertExists()
    }

    @Test
    fun defaultRetentionIsSevenDays() {
        composeTestRule.onNodeWithText("7 days").assertExists()
    }

    @Test
    fun showsDataSection() {
        composeTestRule.onNodeWithText("Data").assertExists()
    }

    @Test
    fun showsExportButton() {
        composeTestRule.onNodeWithText("Backup to SQLite").assertExists()
    }

    @Test
    fun clickingExportButtonDoesNotCrash() {
        composeTestRule.onNodeWithText("Backup to SQLite").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Backup to SQLite").assertExists()
    }

    @Test
    fun showsJsonExportButton() {
        composeTestRule.onNodeWithText("Export as JSON").assertExists()
    }

    @Test
    fun clickingJsonExportButtonDoesNotCrash() {
        composeTestRule.onNodeWithText("Export as JSON").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Export as JSON").assertExists()
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

        assert(DataExporter.exportToCsv(context, dao)) { "CSV export should succeed" }

        val dbResult = DatabaseExporter.export(context)
        assert(dbResult) { "Database export should succeed" }
        val exportedFile = File(context.cacheDir, "export_temp/activity_trace.sqlite")
        assert(exportedFile.exists()) { "Exported temp SQLite should exist" }

        val dedupCount = BackupImporter.importFromBackup(context, Uri.fromFile(exportedFile), dao)
        assert(dedupCount == 0) { "Importing same items should dedup to 0, got $dedupCount" }

        val backupDir = File(context.cacheDir, "import_roundtrip_test")
        backupDir.mkdirs()
        val backupFile = File(backupDir, "backup.sqlite").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(backupFile, null).use { db ->
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
                arrayOf("new item b", "com.new", "NewApp", "notification", null, 40000L, null),
            )
        }

        val newCount = BackupImporter.importFromBackup(context, Uri.fromFile(backupFile), dao)
        assert(newCount == 2) { "Should import 2 new items, got $newCount" }

        val rededupCount = BackupImporter.importFromBackup(context, Uri.fromFile(backupFile), dao)
        assert(rededupCount == 0) { "Re-importing should dedup to 0, got $rededupCount" }

        val allKeys = dao.getAllItemKeys()
        assert(allKeys.size == 4) { "Total items should be 4, got ${allKeys.size}" }

        backupDir.deleteRecursively()
        }
    }

    @Test
    fun showsAboutSection() {
        composeTestRule.onNodeWithText("About").assertExists()
    }

    @Test
    fun showsVersion() {
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        composeTestRule.onNodeWithText("Version $versionName").assertExists()
    }

    @Test
    fun showsLicense() {
        composeTestRule.onNodeWithText("GPL-3.0-only").assertExists()
    }

    @Test
    fun showsSourceLink() {
        composeTestRule.onNodeWithText("github.com/DavidNeurieder/ActivityTrace").assertExists()
    }
}

class SettingsScreenBackButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun backButtonTriggersCallback() {
        var navigated = false

        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(onBack = { navigated = true })
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(navigated)
    }
}
