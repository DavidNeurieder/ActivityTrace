package com.activitytrace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val searchEngine: SearchEngine = mockk()
    private val viewModel = SearchViewModel(searchEngine)

    @Test
    fun displaysResults() {
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
                SearchScreen(viewModel)
            }
        }

        viewModel.onSearch("test")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Test message").assertExists()
    }

    @Test
    fun showsAppGroupHeader() {
        val items = listOf(
            CapturedItem(
                id = 1L,
                text = "msg1",
                appPackage = "com.example",
                contentType = "notification",
                timestamp = 1000L,
            ),
            CapturedItem(
                id = 2L,
                text = "msg2",
                appPackage = "com.example",
                contentType = "notification",
                timestamp = 2000L,
            ),
        )
        every { searchEngine.search("test") } returns flowOf(items)

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel)
            }
        }

        viewModel.onSearch("test")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("com.example").assertExists()
        composeTestRule.onNodeWithText("msg1").assertExists()
        composeTestRule.onNodeWithText("msg2").assertExists()
    }

    @Test
    fun showsNoResultsText() {
        every { searchEngine.search("xyz") } returns flowOf(emptyList())

        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel)
            }
        }

        viewModel.onQueryChange("xyz")
        viewModel.onSearch("xyz")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No results for \"xyz\"").assertExists()
    }

    @Test
    fun searchBarShowsQuery() {
        composeTestRule.setContent {
            MaterialTheme {
                SearchScreen(viewModel)
            }
        }

        viewModel.onQueryChange("hello")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("hello").assertExists()
    }
}
