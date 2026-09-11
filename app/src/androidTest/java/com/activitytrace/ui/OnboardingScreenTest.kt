package com.activitytrace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Activity Trace").assertExists()
    }

    @Test
    fun showsDescription() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText(
            "Search across your notifications and more.\nEverything stays on your device, encrypted."
        ).assertExists()
    }

    @Test
    fun showsCaptureStatusCard() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Capture").assertExists()
        composeTestRule.onNodeWithText("Screen activity").assertExists()
        composeTestRule.onNodeWithText("Notifications").assertExists()
    }

    @Test
    fun captureCardShowsEnableButtonsWhenNothingGranted() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Enable notifications").assertExists()
        composeTestRule.onNodeWithText("Enable accessibility").assertExists()
    }

    @Test
    fun captureCardShowsRestrictedSettingsNote() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText(
            "Notification Access captures notifications in real time. " +
            "If blocked by Restricted Settings, go to " +
            "Settings → Apps → Activity Trace → Allow restricted settings. " +
            "Alternatively, enable the Accessibility Service (Android 14+)."
        ).assertExists()
    }

    @Test
    fun showsDemoDataEntry() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Demo data").assertExists()
        composeTestRule.onNodeWithText(
            "Explore ActivityTrace with fictional activity. Demo data is clearly separated from your real captures, uses the normal search pipeline and can be removed at any time."
        ).assertExists()
    }

    @Test
    fun demoDataEntryNavigates() {
        var navigated = false
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {}, onNavigateToDemoData = { navigated = true })
            }
        }

        composeTestRule.onNodeWithText(
            "Explore ActivityTrace with fictional activity. Demo data is clearly separated from your real captures, uses the normal search pipeline and can be removed at any time."
        ).performClick()
        assert(navigated)
    }

    @Test
    fun showsContinueButton() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun continueButtonFiresOnComplete() {
        var completed = false
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = { completed = true })
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        assert(completed)
    }
}