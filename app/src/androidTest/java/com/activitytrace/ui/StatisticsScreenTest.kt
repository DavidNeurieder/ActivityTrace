package com.activitytrace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.activitytrace.search.SearchStatistics
import com.activitytrace.store.CaptureDao
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun stats(
        total: Int = 0,
        today: Int = 0,
        week: Int = 0,
        apps: List<Pair<String, Int>> = emptyList(),
        types: List<Pair<String, Int>> = emptyList(),
        daily: List<CaptureDao.DateCount> = emptyList(),
        hourly: List<CaptureDao.HourCount> = emptyList(),
        dayOfWeek: List<CaptureDao.DayOfWeekCount> = emptyList(),
    ) = SearchStatistics(
        totalCount = total,
        todayCount = today,
        weekCount = week,
        yesterdayCount = 0,
        prevWeekCount = 0,
        firstTimestamp = null,
        lastTimestamp = null,
        topApps = apps.map { (pkg, count) -> CaptureDao.AppCount(pkg, count) },
        dailyCounts = daily,
        contentTypeBreakdown = types.map { (type, count) -> CaptureDao.ContentTypeCount(type, count) },
        hourlyCounts = hourly,
        dayOfWeekCounts = dayOfWeek,
    )

    @Test
    fun showsSectionHeaders() {
        val statistics = stats(
            total = 2,
            today = 2,
            week = 2,
            apps = listOf("com.a" to 1, "com.b" to 1),
            types = listOf("text" to 1, "image" to 1),
            daily = listOf(CaptureDao.DateCount("2026-11-03", 2)),
            hourly = listOf(CaptureDao.HourCount(9, 2)),
            dayOfWeek = listOf(CaptureDao.DayOfWeekCount(2, 2)),
        )
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(statistics) }
        }
        composeTestRule.onNodeWithText("Top apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Timeline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Content type breakdown").assertIsDisplayed()
    }

    @Test
    fun showsTotalCount() {
        val statistics = stats(total = 5, today = 5, week = 5)
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(statistics) }
        }
        composeTestRule.onNodeWithText("Total captured").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today").assertIsDisplayed()
        composeTestRule.onNodeWithText("This week").assertIsDisplayed()
    }

    @Test
    fun showsTopAppsList() {
        val statistics = stats(
            total = 2,
            today = 2,
            week = 2,
            apps = listOf("com.alpha" to 1, "com.beta" to 1),
        )
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(statistics) }
        }
        composeTestRule.onNodeWithText("Top apps").assertIsDisplayed()
    }

    @Test
    fun showsNoDataWhenEmpty() {
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(SearchStatistics.EMPTY) }
        }
        composeTestRule.onNodeWithText("Not enough data for chart").assertIsDisplayed()
    }

    @Test
    fun showsAppNameWhenFiltered() {
        val statistics = stats(total = 1, today = 1, week = 1, apps = listOf("com.test" to 1))
        composeTestRule.setContent {
            MaterialTheme {
                StatisticsScreen(statistics, selectedApp = "com.test")
            }
        }
        composeTestRule.onNodeWithText("Top apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Timeline").assertIsDisplayed()
    }

    @Test
    fun topAppsHeaderClickExpandsAndShowLessCollapses() {
        val apps = List(7) { i -> "com.app$i" to 1 }
        val statistics = stats(total = 7, today = 7, week = 7, apps = apps)
        composeTestRule.setContent {
            MaterialTheme { StatisticsScreen(statistics) }
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
        val statistics = stats(
            total = 2,
            today = 2,
            week = 2,
            apps = listOf("com.alpha" to 1, "com.beta" to 1),
        )
        var clicked: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                StatisticsScreen(statistics, onAppClick = { clicked = it })
            }
        }

        composeTestRule.onNodeWithText("com.beta").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 2_000) { clicked == "com.beta" }
        assertEquals("com.beta", clicked)
    }
}
