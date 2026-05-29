package com.activitytrace.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.store.RetentionCleanupWorker
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
            "If blocked by Restricted Settings (common on F-Droid/sideloaded apps), " +
            "enable the Accessibility Service instead (Android 14+)."
        ).assertExists()
    }

    @Test
    fun showsRetentionSection() {
        composeTestRule.onNodeWithText("Retention period").assertExists()
    }

    @Test
    fun showsRetentionOptions() {
        composeTestRule.onNodeWithText("Never").assertExists()
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
    fun showsAboutSection() {
        composeTestRule.onNodeWithText("About").assertExists()
    }

    @Test
    fun showsVersion() {
        composeTestRule.onNodeWithText("Version 0.1.0").assertExists()
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
