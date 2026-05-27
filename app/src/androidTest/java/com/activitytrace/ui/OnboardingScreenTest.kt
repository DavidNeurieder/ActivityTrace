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
            "Search across your notifications, clipboard, and more.\nEverything stays on your device, encrypted."
        ).assertExists()
    }

    @Test
    fun showsGrantNotificationButton() {
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingScreen(onComplete = {})
            }
        }

        composeTestRule.onNodeWithText("Grant Notification Access").assertExists()
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
