package com.activitytrace.ui

import androidx.lifecycle.SavedStateHandle
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import com.activitytrace.store.CaptureDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val searchEngine: SearchEngine = mockk()
    private val captureDao: CaptureDao = mockk()
    private lateinit var dispatcher: TestDispatcher
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        every { searchEngine.recentItems() } returns flowOf(emptyList())
        viewModel = SearchViewModel(searchEngine, captureDao, SavedStateHandle())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        assert(viewModel.query.value == "")
        assert(viewModel.results.value.isEmpty())
    }

    @Test
    fun `blank query shows recentItems`() {
        every { searchEngine.search("hello") } returns flowOf(emptyList())
        every { searchEngine.recentItems() } returns flowOf(
            listOf(CapturedItem(text = "x", appPackage = "com.x", contentType = "text", timestamp = 1L))
        )
        viewModel.onQueryChange("hello")
        viewModel.onQueryChange("")
        val result = viewModel.results.value
        assert(result.size == 1)
        assert(result[0].text == "x")
    }

    @Test
    fun `onQueryChange updates query`() {
        viewModel.onQueryChange("hello")
        assert(viewModel.query.value == "hello")
    }

    @Test
    fun `onSearch with blank query clears results`() {
        viewModel.onSearch("")
        assert(viewModel.results.value.isEmpty())
    }

    @Test
    fun `onSearch collects flow from search engine`() {
        val items = listOf(
            CapturedItem(text = "test", appPackage = "com.test", contentType = "text", timestamp = 1000L)
        )
        every { searchEngine.search("test") } returns flowOf(items)

        viewModel.onSearch("test")
        assert(viewModel.results.value == items)
    }

    @Test
    fun `search with whitespace only clears results`() {
        viewModel.onSearch("   ")
        assert(viewModel.results.value.isEmpty())
    }
}
