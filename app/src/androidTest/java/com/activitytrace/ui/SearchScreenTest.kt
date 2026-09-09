package com.activitytrace.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import com.activitytrace.search.SearchPage
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
        every { searchEngine.recentPaged(any(), any(), any(), any(), any()) } returns flowOf(SearchPage(items, true))
        every { searchEngine.searchPaged(any(), any(), any(), any(), any(), any()) } returns flowOf(SearchPage(emptyList(), true))
        return SearchViewModel(searchEngine, app, debounceMillis = 0)
    }

    private fun item(id: Long, text: String) = CapturedItem(
        id = id,
        text = text,
        appPackage = "com.test",
        contentType = "notification",
        timestamp = System.currentTimeMillis(),
    )

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
        every { searchEngine.searchPaged("test", any(), any(), any(), any(), any()) } returns flowOf(SearchPage(items, true))

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        viewModel.onQueryChange("test")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("1 result").assertExists()
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

        composeTestRule.onNodeWithText("Today").assertExists()
        composeTestRule.onNodeWithText("This week").assertExists()
        composeTestRule.onNodeWithText("2 results").assertExists()
    }

    @Test
    fun showsNoResultsText() {
        val viewModel = createViewModel()
        every { searchEngine.searchPaged("xyz", any(), any(), any(), any(), any()) } returns flowOf(SearchPage(emptyList(), true))

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No captured items yet").assertExists()

        viewModel.onQueryChange("xyz")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("xyz").assertExists()
        composeTestRule.onNodeWithText("No results for xyz").assertExists()
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
        composeTestRule.onNodeWithText("All apps").assertExists()
        composeTestRule.onNodeWithText("All time").assertExists()
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

    @Test
    fun showsLoadMoreButtonWhenMoreResultsExist() {
        val viewModel = createViewModel()
        val firstPage = (1..50).map { item(it.toLong(), "item $it") }
        val tailPage = listOf(item(51L, "item 51"))
        every { searchEngine.searchPaged("test", any(), any(), any(), any(), 0) } returns flowOf(SearchPage(firstPage, false))
        every { searchEngine.searchPaged("test", any(), any(), any(), any(), 50) } returns flowOf(SearchPage(tailPage, true))

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel, onNavigateToSettings = {})
            }
        }

        viewModel.onQueryChange("test")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("50+ results").assertExists()

        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Load more"))
        composeTestRule.onNodeWithText("Load more").performClick()
        composeTestRule.waitForIdle()

        verify { searchEngine.searchPaged("test", any(), any(), any(), any(), 50) }
        composeTestRule.onNodeWithText("51 results").assertExists()
    }
}
