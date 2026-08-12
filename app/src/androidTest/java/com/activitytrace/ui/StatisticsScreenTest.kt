package com.activitytrace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.activitytrace.model.CapturedItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun item(text: String, pkg: String, type: String, ts: Long) =
        CapturedItem(
            text = text,
            appPackage = pkg,
            appName = null,
            contentType = type,
            category = null,
            timestamp = ts,
            metadata = null,
        )

    @Test
    fun showsSectionHeaders() {
        val items = listOf(
            item("a", "com.a", "text", System.currentTimeMillis()),
            item("b", "com.b", "image", System.currentTimeMillis()),
        )
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(items) }
        }
        composeTestRule.onNodeWithText("Top apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Timeline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Content type breakdown").assertIsDisplayed()
    }

    @Test
    fun showsTotalCount() {
        val items = List(5) { i ->
            item("text$i", "com.app$i", "text", System.currentTimeMillis())
        }
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(items) }
        }
        composeTestRule.onNodeWithText("Total captured").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("This week").assertIsDisplayed()
    }

    @Test
    fun showsTopAppsList() {
        val items = listOf(
            item("a", "com.alpha", "text", System.currentTimeMillis()),
            item("b", "com.beta", "text", System.currentTimeMillis()),
        )
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(items) }
        }
        composeTestRule.onNodeWithText("Top apps").assertIsDisplayed()
    }

    @Test
    fun showsNoDataWhenEmpty() {
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(emptyList()) }
        }
        composeTestRule.onNodeWithText("Not enough data for chart").assertIsDisplayed()
    }

    @Test
    fun showsAppNameWhenFiltered() {
        val items = listOf(
            item("a", "com.test", "text", System.currentTimeMillis()),
        )
        composeTestRule.setContent {
            MaterialTheme {
                StatisticsScreen(items, selectedApp = "com.test")
            }
        }
        composeTestRule.onNodeWithText("Top apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Timeline").assertIsDisplayed()
    }

    @Test
    fun topAppsHeaderClickExpandsAndShowLessCollapses() {
        val items = List(7) { i ->
            item("text$i", "com.app$i", "text", System.currentTimeMillis())
        }
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(items) }
        }

        composeTestRule.onNodeWithText("Show all 7").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Top apps").performClick()
        composeTestRule.onNodeWithText("com.app5").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("com.app6").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Show less").assertIsDisplayed()

        composeTestRule.onNodeWithText("Show less").performClick()
        composeTestRule.onNodeWithText("Show all 7").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("com.app5").assertDoesNotExist()
    }

    @Test
    fun topAppsRowClickInvokesCallback() {
        val items = listOf(
            item("a", "com.alpha", "text", System.currentTimeMillis()),
            item("b", "com.beta", "text", System.currentTimeMillis()),
        )
        var clicked: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                StatisticsScreen(items, onAppClick = { clicked = it })
            }
        }

        composeTestRule.onNodeWithText("com.beta").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 2_000) { clicked == "com.beta" }
        assertEquals("com.beta", clicked)
    }
}
