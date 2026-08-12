package com.activitytrace.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val app: Application = mockk()
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true).also {
        every { it.putString(any(), any()) } returns it
        every { it.putBoolean(any(), any()) } returns it
    }
    private lateinit var dispatcher: TestDispatcher
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        every { app.getSharedPreferences("activity_trace", Context.MODE_PRIVATE) } returns prefs
        every { prefs.getString("search_query", "") } returns ""
        every { prefs.getString("content_type_filter", null) } returns null
        every { prefs.getBoolean("bookmarked_filter", false) } returns false
        every { prefs.getBoolean("show_stats", false) } returns false
        every { prefs.edit() } returns editor
        every { searchEngine.recentItems(any(), any(), any()) } returns flowOf(emptyList())
        viewModel = SearchViewModel(searchEngine, app)
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
    fun `restores query from SharedPreferences`() {
        every { prefs.getString("search_query", "") } returns "saved"
        every { searchEngine.search("saved", any(), any(), any()) } returns flowOf(emptyList())
        val vm = SearchViewModel(searchEngine, app)
        assert(vm.query.value == "saved")
    }

    @Test
    fun `restores contentTypeFilter from SharedPreferences`() {
        every { prefs.getString("content_type_filter", null) } returns "notification"
        val vm = SearchViewModel(searchEngine, app)
        assert(vm.contentTypeFilter.value == "notification")
    }

    @Test
    fun `blank query shows recentItems`() {
        every { searchEngine.search("hello", any(), any(), any()) } returns flowOf(emptyList())
        every { searchEngine.recentItems(any(), any(), any()) } returns flowOf(
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
    fun `onQueryChange persists to SharedPreferences after debounce`() {
        viewModel.onQueryChange("persist-me")
        dispatcher.scheduler.advanceTimeBy(500)
        dispatcher.scheduler.runCurrent()
        verify { editor.putString("search_query", "persist-me") }
        verify { editor.apply() }
    }

    @Test
    fun `onSearch with blank query clears results`() {
        viewModel.onQueryChange("")
        assert(viewModel.results.value.isEmpty())
    }

    @Test
    fun `onSearch collects flow from search engine`() {
        val items = listOf(
            CapturedItem(text = "test", appPackage = "com.test", contentType = "text", timestamp = 1000L)
        )
        every { searchEngine.search("test", any(), any(), any()) } returns flowOf(items)

        viewModel.onQueryChange("test")
        assert(viewModel.results.value == items)
    }

    @Test
    fun `search with whitespace only clears results`() {
        viewModel.onQueryChange("   ")
        assert(viewModel.results.value.isEmpty())
    }

    @Test
    fun `setContentTypeFilter updates filter`() {
        viewModel.setContentTypeFilter("page")
        assert(viewModel.contentTypeFilter.value == "page")
    }

    @Test
    fun `setContentTypeFilter persists to SharedPreferences`() {
        viewModel.setContentTypeFilter("notification")
        verify { editor.putString("content_type_filter", "notification") }
        verify { editor.apply() }
    }

    @Test
    fun `setContentTypeFilter null clears filter`() {
        viewModel.setContentTypeFilter("notification")
        viewModel.setContentTypeFilter(null)
        assert(viewModel.contentTypeFilter.value == null)
    }

    @Test
    fun `restores showStats from SharedPreferences`() {
        every { prefs.getBoolean("show_stats", false) } returns true
        val vm = SearchViewModel(searchEngine, app)
        assert(vm.showStats.value)
    }

    @Test
    fun `setShowStats persists to SharedPreferences`() {
        viewModel.setShowStats(true)
        verify { editor.putBoolean("show_stats", true) }
        verify { editor.apply() }
    }
}
