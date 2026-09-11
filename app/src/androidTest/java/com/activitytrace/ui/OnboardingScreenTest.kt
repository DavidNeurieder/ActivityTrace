package com.activitytrace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun captureCardShowsSummaryWhenNothingGranted() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Capture isn't enabled yet").assertExists()
        composeTestRule.onNodeWithText(
            "ActivityTrace needs access to your device to record your activity."
        ).assertExists()
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
    fun captureCardShowsContextualPermissionExplanations() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Notifications aren't being captured.").assertExists()
        composeTestRule.onNodeWithText("Screen activity isn't being captured.").assertExists()
        composeTestRule.onNodeWithText(
            "Accessibility access lets ActivityTrace see which app is on screen."
        ).assertExists()
    }

    @Test
    fun captureCardShowsRestrictedSettingsNote() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText(
            "If Android prevents enabling Notification Access, " +
            "use Allow restricted settings in ActivityTrace's app settings."
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
        composeTestRule.onNodeWithText("Explore ActivityTrace with fictional activity.").assertExists()
        composeTestRule.onNodeWithText("No demo data installed").assertExists()
        composeTestRule.onNodeWithText(
            "Demo data is separate from your real activity and can be removed at any time."
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

        composeTestRule.onNodeWithText("Explore ActivityTrace with fictional activity.").performScrollTo().performClick()
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

        composeTestRule.onNodeWithText("Continue").performScrollTo().performClick()
        assert(completed)
    }
}