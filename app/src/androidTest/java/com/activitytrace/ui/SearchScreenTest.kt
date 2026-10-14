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
import com.activitytrace.search.SearchEngineApi
import com.activitytrace.search.SearchPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeSearchEngine : SearchEngineApi {
        var recentPages: Flow<SearchPage> = flowOf(SearchPage(emptyList(), true))
        var onSearch: (query: String, offset: Int) -> Flow<SearchPage> =
            { _, _ -> flowOf(SearchPage(emptyList(), true)) }
        val searchCalls = mutableListOf<Pair<String, Int>>()

        override fun recentPaged(
            contentType: String?,
            appPackage: String?,
            dateRange: Pair<Long, Long>?,
            pageSize: Int,
            offset: Int,
        ): Flow<SearchPage> = recentPages

        override fun searchPaged(
            rawQuery: String,
            contentType: String?,
            appPackage: String?,
            dateRange: Pair<Long, Long>?,
            pageSize: Int,
            offset: Int,
        ): Flow<SearchPage> {
            searchCalls += rawQuery to offset
            return onSearch(rawQuery, offset)
        }
    }

    private val searchEngine = FakeSearchEngine()

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("activity_trace", 0).edit().clear().apply()
    }

    private fun createViewModel(items: List<CapturedItem> = emptyList()): SearchViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        searchEngine.recentPages = flowOf(SearchPage(items, true))
        searchEngine.onSearch = { _, _ -> flowOf(SearchPage(emptyList(), true)) }
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
        searchEngine.onSearch = { _, _ -> flowOf(SearchPage(items, true)) }

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
        searchEngine.onSearch = { _, _ -> flowOf(SearchPage(emptyList(), true)) }

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
        searchEngine.onSearch = { _, offset ->
            if (offset == 0) flowOf(SearchPage(firstPage, false)) else flowOf(SearchPage(tailPage, true))
        }

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

        assertTrue(searchEngine.searchCalls.contains("test" to 50))
        composeTestRule.onNodeWithText("51 results").assertExists()
    }
}
