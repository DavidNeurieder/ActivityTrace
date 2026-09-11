package com.activitytrace.ui

import android.content.Context
import android.os.Environment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.RetentionCleanupWorker
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
        context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityTraceDatabase.resetForTesting()
        val dbFile = context.getDatabasePath("activity_trace.db")
        dbFile.delete()
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
        composeTestRule.setContent {
            MaterialTheme {
                SettingsScreen(onBack = {})
            }
        }
    }

    @After
    fun tearDown() {
        RetentionCleanupWorker.setRetentionDays(context, 7)
        val dbFile = context.getDatabasePath("activity_trace.db")
        ActivityTraceDatabase.resetForTesting()
        dbFile.delete()
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
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
    fun permissionsSectionIsRemoved() {
        composeTestRule.onNodeWithText("Permissions").assertDoesNotExist()
    }

    @Test
    fun showsCaptureStatusCard() {
        composeTestRule.onNodeWithText("Capture").assertExists()
        composeTestRule.onNodeWithText("Screen activity").assertExists()
        composeTestRule.onNodeWithText("Notifications").assertExists()
    }

    @Test
    fun captureCardShowsBlockedAppsCount() {
        composeTestRule.onNode(hasText("apps blocked from capture", substring = true)).assertExists()
    }

    @Test
    fun showsPrivacySection() {
        composeTestRule.onNodeWithText("Privacy").assertExists()
        composeTestRule.onNodeWithText("What gets captured?").assertExists()
    }

    @Test
    fun showsPrivacySensitiveContentDescription() {
        composeTestRule.onNodeWithText("Sensitive content").assertExists()
        composeTestRule.onNode(
            hasText("Everything stays on this device in an encrypted database and is never sent anywhere.", substring = true)
        ).assertExists()
    }

    @Test
    fun captureSectionShowsRestrictedSettingsNote() {
        composeTestRule.onNodeWithText(
            "Notification Access captures notifications in real time. " +
            "If blocked by Restricted Settings, go to " +
            "Settings → Apps → Activity Trace → Allow restricted settings. " +
            "Alternatively, enable the Accessibility Service (Android 14+)."
        ).assertExists()
    }

    @Test
    fun showsRetentionSection() {
        composeTestRule.onNodeWithText("Retention period").assertExists()
    }

    @Test
    fun showsRetentionOptions() {
        composeTestRule.onNodeWithText("7 days").assertExists()
        composeTestRule.onNodeWithTag("retention_dropdown").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Always").assertExists()
        composeTestRule.onNodeWithText("30 days").assertExists()
        composeTestRule.onNodeWithText("90 days").assertExists()
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
        composeTestRule.onNodeWithText("Create backup").assertExists()
    }

    @Test
    fun passwordVisibilityToggleFlipsBetweenShowAndHide() {
        composeTestRule.onNodeWithText("Create backup").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val toggle = composeTestRule.onNodeWithTag("backup_password_visibility_toggle")
        toggle.assertExists()
        toggle.assert(hasContentDescription("Show password"))

        toggle.performClick()
        toggle.assert(hasContentDescription("Hide password"))

        toggle.performClick()
        toggle.assert(hasContentDescription("Show password"))
    }

    @Test
    fun showsPasswordField() {
        composeTestRule.onNodeWithText("Create backup").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Backup password").assertExists()
    }

    @Test
    fun clickingExportButtonDoesNotCrash() {
        composeTestRule.onNodeWithText("Create backup").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Encrypted backup").assertExists()
    }

    @Test
    fun backupDialogShowsUnencryptedOptionWithWarning() {
        composeTestRule.onNodeWithText("Create backup").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Backup unencrypted").assertExists()
        composeTestRule.onNode(
            hasText("anyone who can access this file", substring = true)
        ).assertExists()
    }

    @Test
    fun clickingUnencryptedOptionOpensWarningConfirm() {
        composeTestRule.onNodeWithText("Create backup").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Backup unencrypted").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Export plain SQLite").assertExists()
        composeTestRule.onNode(
            hasText("This export is NOT encrypted.", substring = true)
        ).assertExists()
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
