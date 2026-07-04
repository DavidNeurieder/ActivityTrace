package com.activitytrace.ui

import android.content.Context
import android.os.Environment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
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
        composeTestRule.onNodeWithText("Export database").assertExists()
    }

    @Test
    fun clickingExportButtonDoesNotCrash() {
        composeTestRule.onNodeWithText("Export database").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Export database").assertExists()
    }

    @Test
    fun exportFunctionExportsPlainSqlite() = runBlocking {
        val result = DatabaseExporter.export(context)

        assert(result)
        val exportFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace/activity_trace.sqlite",
        )
        assert(exportFile.exists()) { "Expected export file at ${exportFile.absolutePath}" }
        val magic = exportFile.readBytes().take(16).toByteArray()
        assert(magic.contentEquals("SQLite format 3\u0000".toByteArray())) { "Not a valid SQLite file" }
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
