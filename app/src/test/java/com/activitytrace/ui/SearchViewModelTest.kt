package com.activitytrace.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import com.activitytrace.search.SearchPage
import com.activitytrace.search.SearchStatistics
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

    private fun item(id: Long, text: String = "text $id") = CapturedItem(
        id = id,
        text = text,
        appPackage = "com.x",
        contentType = "notification",
        timestamp = id,
    )

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
        every { searchEngine.results(any(), any(), any()) } returns flowOf(SearchPage(emptyList(), true))
        every { searchEngine.statistics(any(), any(), any()) } returns flowOf(SearchStatistics.EMPTY)
        viewModel = SearchViewModel(searchEngine, app, debounceMillis = 0)
    }

    private fun stubResults(
        query: String,
        contentType: String? = null,
        offset: Int = 0,
        page: SearchPage,
    ) {
        every {
            searchEngine.results(
                match { it.rawQuery == query && it.contentType == contentType },
                any(),
                offset,
            )
        } returns flowOf(page)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        assert(viewModel.query.value == "")
        assert(viewModel.results.value.isEmpty())
        assert(viewModel.loadedCount.value == 0)
        assert(!viewModel.hasMore.value)
    }

    @Test
    fun `restores query from SharedPreferences`() {
        every { prefs.getString("search_query", "") } returns "saved"
        val vm = SearchViewModel(searchEngine, app, debounceMillis = 0)
        assert(vm.query.value == "saved")
    }

    @Test
    fun `restores contentTypeFilter from SharedPreferences`() {
        every { prefs.getString("content_type_filter", null) } returns "notification"
        val vm = SearchViewModel(searchEngine, app, debounceMillis = 0)
        assert(vm.contentTypeFilter.value == "notification")
    }

    @Test
    fun `blank query shows recentItems`() {
        val recent = listOf(item(1, "x"))
        stubResults("", page = SearchPage(recent, true))
        viewModel.onQueryChange("hello")
        viewModel.onQueryChange("")
        val result = viewModel.results.value
        assert(result.size == 1)
        assert(result[0].text == "x")
        assert(viewModel.loadedCount.value == 1)
        assert(!viewModel.hasMore.value)
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
        val items = listOf(item(1, "test"), item(2, "test two"))
        stubResults("test", page = SearchPage(items, true))

        viewModel.onQueryChange("test")
        assert(viewModel.results.value == items)
        assert(viewModel.loadedCount.value == 2)
    }

    @Test
    fun `search with whitespace only clears results`() {
        viewModel.onQueryChange("   ")
        assert(viewModel.results.value.isEmpty())
    }

    @Test
    fun `a full page reports has more`() {
        val items = (1..50).map { item(it.toLong()) }
        stubResults("test", page = SearchPage(items, isLastPage = false))

        viewModel.onQueryChange("test")
        assert(viewModel.results.value.size == 50)
        assert(viewModel.hasMore.value)
        assert(viewModel.loadedCount.value == 50)
    }

    @Test
    fun `loadMore appends the following page and disables hasMore on the tail`() {
        val first = (1..50).map { item(it.toLong(), "first") }
        val second = listOf(item(51, "tail"))
        stubResults("test", offset = 0, page = SearchPage(first, false))
        stubResults("test", offset = 50, page = SearchPage(second, true))

        viewModel.onQueryChange("test")
        viewModel.loadMore()

        assert(viewModel.results.value.size == 51)
        assert(viewModel.results.value.last().text == "tail")
        assert(!viewModel.hasMore.value)
        assert(viewModel.loadedCount.value == 51)
    }

    @Test
    fun `loadMore is ignored when hasMore is false`() {
        val items = listOf(item(1))
        stubResults("test", page = SearchPage(items, true))
        viewModel.onQueryChange("test")
        viewModel.loadMore()

        verify(exactly = 1) { searchEngine.results(match { it.rawQuery == "test" }, any(), any()) }
    }

    @Test
    fun `changing the query resets pagination to the first page`() {
        val firstPage = (1..50).map { item(it.toLong(), "a") }
        val secondPage = listOf(item(51, "b"))
        val resetPage = listOf(item(1, "c"))
        stubResults("query", offset = 0, page = SearchPage(firstPage, false))
        stubResults("query", offset = 50, page = SearchPage(secondPage, true))
        stubResults("query", contentType = "notification", offset = 0, page = SearchPage(resetPage, true))

        viewModel.onQueryChange("query")
        viewModel.loadMore()
        assert(viewModel.results.value.size == 51)

        viewModel.setContentTypeFilter("notification")
        assert(viewModel.results.value == resetPage)
        assert(viewModel.loadedCount.value == 1)
        assert(!viewModel.hasMore.value)
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
        val vm = SearchViewModel(searchEngine, app, debounceMillis = 0)
        assert(vm.showStats.value)
    }

    @Test
    fun `setShowStats persists to SharedPreferences`() {
        viewModel.setShowStats(true)
        verify { editor.putBoolean("show_stats", true) }
        verify { editor.apply() }
    }

    @Test
    fun `search is not executed until the debounce window elapses`() {
        val items = listOf(item(1, "debounced"))
        stubResults("hello", page = SearchPage(items, true))
        val vm = SearchViewModel(searchEngine, app, debounceMillis = 300)

        vm.onQueryChange("hello")
        assert(vm.results.value.isEmpty())

        dispatcher.scheduler.advanceTimeBy(299)
        dispatcher.scheduler.runCurrent()
        assert(vm.results.value.isEmpty())

        verify(exactly = 0) { searchEngine.results(match { it.rawQuery == "hello" }, any(), any()) }

        dispatcher.scheduler.advanceTimeBy(2)
        dispatcher.scheduler.runCurrent()

        assert(vm.results.value == items)
        verify(exactly = 1) { searchEngine.results(match { it.rawQuery == "hello" }, any(), any()) }
    }

    @Test
    fun `typing during the debounce window runs only the last query`() {
        val items = listOf(item(1, "final"))
        stubResults("final", page = SearchPage(items, true))
        val vm = SearchViewModel(searchEngine, app, debounceMillis = 300)

        vm.onQueryChange("f")
        vm.onQueryChange("fi")
        vm.onQueryChange("final")

        dispatcher.scheduler.advanceTimeBy(301)
        dispatcher.scheduler.runCurrent()

        verify(exactly = 1) { searchEngine.results(match { it.rawQuery == "final" }, any(), any()) }
        verify(exactly = 0) { searchEngine.results(match { it.rawQuery == "f" }, any(), any()) }
        verify(exactly = 0) { searchEngine.results(match { it.rawQuery == "fi" }, any(), any()) }
        assert(vm.results.value == items)
    }

    @Test
    fun `statistics are not computed while the results list is visible`() {
        viewModel.onQueryChange("hello")

        verify(exactly = 0) { searchEngine.statistics(any(), any(), any()) }
    }

    @Test
    fun `enabling statistics queries the engine for the same criteria`() {
        val statistics = SearchStatistics(totalCount = 60)
        every { searchEngine.statistics(match { it.rawQuery == "hello" }, any(), any()) } returns flowOf(statistics)

        viewModel.onQueryChange("hello")
        viewModel.setShowStats(true)

        assert(viewModel.statistics.value == statistics)
        verify(exactly = 1) { searchEngine.statistics(match { it.rawQuery == "hello" }, any(), any()) }
    }

    @Test
    fun `statistics do not recompute when only the offset changes`() {
        val first = (1..50).map { item(it.toLong()) }
        val second = listOf(item(51))
        stubResults("test", offset = 0, page = SearchPage(first, false))
        stubResults("test", offset = 50, page = SearchPage(second, true))
        every { searchEngine.statistics(any(), any(), any()) } returns flowOf(SearchStatistics(totalCount = 60))

        viewModel.onQueryChange("test")
        viewModel.setShowStats(true)
        viewModel.loadMore()

        assert(viewModel.results.value.size == 51)
        verify(exactly = 1) { searchEngine.statistics(any(), any(), any()) }
    }

    @Test
    fun `statistics use the daily bucket count of the selected date range`() {
        val range = 0L to 7 * 86_400_000L
        every { searchEngine.statistics(any(), any(), 7) } returns flowOf(SearchStatistics(totalCount = 1))

        viewModel.setDateFilter(range)
        viewModel.setShowStats(true)

        verify { searchEngine.statistics(match { it.dateRange == range }, any(), 7) }
    }
}