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
    fun showsGrantNotificationButton() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Notification Access (recommended)").assertExists()
    }

    @Test
    fun showsAccessibilityServiceButton() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Accessibility Service (Android 14+)").assertExists()
    }

    @Test
    fun showsRestrictedSettingsNote() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText(
            "Note: If Notification Access is blocked by Restricted Settings, go to Settings → Apps → Activity Trace → Allow restricted settings. Alternatively, use the Accessibility Service path above on Android 14+."
        ).assertExists()
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
