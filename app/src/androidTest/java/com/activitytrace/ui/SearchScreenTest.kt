package com.activitytrace.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val searchEngine: SearchEngine = mockk()

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("activity_trace", 0).edit().clear().apply()
    }

    private fun createViewModel(items: List<CapturedItem> = emptyList()): SearchViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        every { searchEngine.recentItems(any()) } returns flowOf(items)
        every { searchEngine.search(any()) } returns flowOf(emptyList())
        every { searchEngine.search(any(), any()) } returns flowOf(emptyList())
        return SearchViewModel(searchEngine, app)
    }

    @Test
    fun displaysResults() {
        val viewModel = createViewModel()
        val items = listOf(
            CapturedItem(
                text = "Test message",
                appPackage = "com.test",
                contentType = "notification",
                timestamp = 1000L,
            )
        )
        every { searchEngine.search("test") } returns flowOf(items)

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        viewModel.onQueryChange("test")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("1 results").assertExists()
    }

    @Test
    fun showsDateGrouping() {
        val now = System.currentTimeMillis()
        val items = listOf(
            CapturedItem(
                id = 1L,
                text = "today item",
                appPackage = "com.example",
                contentType = "notification",
                timestamp = now,
            ),
            CapturedItem(
                id = 2L,
                text = "older item",
                appPackage = "com.example",
                contentType = "notification",
                timestamp = now - 3 * 24 * 60 * 60 * 1000L,
            ),
        )
        val viewModel = createViewModel(items)

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("── Today ──").assertExists()
        composeTestRule.onNodeWithText("── This Week ──").assertExists()
        composeTestRule.onNodeWithText("2 results").assertExists()
    }

    @Test
    fun showsNoResultsText() {
        val viewModel = createViewModel()
        every { searchEngine.search("xyz") } returns flowOf(emptyList())

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        viewModel.onQueryChange("xyz")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No results for \"xyz\"").assertExists()
    }

    @Test
    fun searchBarShowsQuery() {
        val viewModel = createViewModel()

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        viewModel.onQueryChange("hello")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("hello").assertExists()
    }

    @Test
    fun settingsButtonExists() {
        val viewModel = createViewModel()

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun settingsButtonTriggersCallback() {
        val viewModel = createViewModel()
        var navigated = false

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = { navigated = true })
            }
        }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        assert(navigated)
    }

    @Test
    fun showsFilterChips() {
        val viewModel = createViewModel()

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        composeTestRule.onNodeWithText("All").assertExists()
        composeTestRule.onNodeWithText("Notifications").assertExists()
        composeTestRule.onNodeWithText("Folders").assertExists()
        composeTestRule.onNodeWithText("Accessibility").assertExists()
    }

    @Test
    fun showsResultCount() {
        val items = listOf(
            CapturedItem(
                id = 1L,
                text = "item one",
                appPackage = "com.test",
                contentType = "notification",
                timestamp = System.currentTimeMillis(),
            ),
            CapturedItem(
                id = 2L,
                text = "item two",
                appPackage = "com.test",
                contentType = "notification",
                timestamp = System.currentTimeMillis(),
            ),
        )
        val viewModel = createViewModel(items)

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("2 results").assertExists()
    }
}
