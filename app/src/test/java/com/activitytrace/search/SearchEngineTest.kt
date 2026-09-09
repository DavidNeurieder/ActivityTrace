package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SearchEngineTest {

    private val captureDao: CaptureDao = mockk(relaxed = true)
    private lateinit var searchEngine: SearchEngine

    @Before
    fun setUp() {
        searchEngine = SearchEngine(captureDao)
    }

    @Test
    fun `search with no keywords but filters uses like dao`() = runTest {
        searchEngine.search("type:notification").collect { }

        verify { captureDao.searchLike(emptyList<String>(), null, "notification", null) }
    }

    @Test
    fun `search with no keywords but time range uses like dao`() = runTest {
        searchEngine.search("today").collect { }

        verify { captureDao.searchLike(emptyList<String>(), any(), null, null) }
    }

    @Test
    fun `search wraps keywords in full-word tokens for fts`() = runTest {
        searchEngine.search("hello world").collect { }

        verify { captureDao.searchFts("hello world", null, null, null) }
    }

    @Test
    fun `search with single keyword makes a full-word token`() = runTest {
        searchEngine.search("hello").collect { }

        verify { captureDao.searchFts("hello", null, null, null) }
    }

    @Test
    fun `search with wildcard drops star and makes full-word token`() = runTest {
        searchEngine.search("hello*").collect { }

        verify { captureDao.searchFts("hello", null, null, null) }
    }

    @Test
    fun `search with leading wildcard drops star and makes full-word token`() = runTest {
        searchEngine.search("*hello").collect { }

        verify { captureDao.searchFts("hello", null, null, null) }
    }

    @Test
    fun `search with surrounding wildcards drops stars and makes full-word token`() = runTest {
        searchEngine.search("*hello*").collect { }

        verify { captureDao.searchFts("hello", null, null, null) }
    }

    @Test
    fun `search with time range passes it to fts dao`() = runTest {
        searchEngine.search("hello today").collect { }

        verify { captureDao.searchFts("hello", any(), null, null) }
    }

    @Test
    fun `search with empty string returns empty`() = runTest {
        val result = mutableListOf<List<CapturedItem>>()
        searchEngine.search("").collect { result.add(it) }

        assert(result[0].isEmpty())
    }

    @Test
    fun `search strips time keywords from match query`() = runTest {
        searchEngine.search("today tomorrow").collect { }

        verify { captureDao.searchFts("tomorrow", any(), null, null) }
    }

    @Test
    fun `search with type filter passes contentType to fts dao`() = runTest {
        searchEngine.search("type:notification hello").collect { }

        verify { captureDao.searchFts("hello", null, "notification", null) }
    }

    @Test
    fun `search with in filter passes appPackage to fts dao`() = runTest {
        searchEngine.search("in:signal meeting").collect { }

        verify { captureDao.searchFts("meeting", null, null, "signal") }
    }

    @Test
    fun `search with combined type and in filters and keyword`() = runTest {
        searchEngine.search("in:com.example type:screen notes").collect { }

        verify { captureDao.searchFts("notes", null, "screen", "com.example") }
    }

    @Test
    fun `search with operator chars quotes the token`() = runTest {
        searchEngine.search("C++").collect { }

        verify { captureDao.searchFts("\"c++\"", null, null, null) }
    }

    @Test
    fun `search with colon char quotes the token`() = runTest {
        searchEngine.search("3:30").collect { }

        verify { captureDao.searchFts("\"3:30\"", null, null, null) }
    }

    @Test
    fun `search with only wildcard returns empty`() = runTest {
        val result = mutableListOf<List<CapturedItem>>()
        searchEngine.search("*").collect { result.add(it) }

        assert(result[0].isEmpty())
    }

    @Test
    fun `recentPaged uses like paged with page size and offset`() = runTest {
        searchEngine.recentPaged(null, null, null, pageSize = 50, offset = 25).collect { }

        verify { captureDao.searchLikePaged(emptyList<String>(), null, null, null, 50L, 25L) }
    }

    @Test
    fun `searchPaged uses fts paged with page size and offset`() = runTest {
        searchEngine.searchPaged("hello", null, null, null, pageSize = 50, offset = 25).collect { }

        verify { captureDao.searchFtsPaged("hello", null, null, null, 50L, 25L) }
    }

    @Test
    fun `searchPaged with filters and no keywords uses like paged`() = runTest {
        searchEngine.searchPaged("type:notification", null, null, null, pageSize = 50, offset = 0).collect { }

        verify { captureDao.searchLikePaged(emptyList<String>(), null, "notification", null, 50L, 0L) }
    }

    @Test
    fun `searchPaged with all wildcards returns an empty last page`() = runTest {
        val pages = mutableListOf<SearchPage>()
        searchEngine.searchPaged("***", null, null, null, pageSize = 50, offset = 0).collect { pages.add(it) }
        val page = pages.single()
        assert(page.items.isEmpty())
        assert(page.isLastPage)

        verify(exactly = 0) { captureDao.searchFtsPaged(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `searchPaged marks a short page as the last page`() = runTest {
        val items = listOf(
            CapturedItem(text = "found", appPackage = "com.x", contentType = "text", timestamp = 1L)
        )
        every { captureDao.searchFtsPaged(any(), any(), any(), any(), any(), any()) } returns flowOf(items)

        val pages = mutableListOf<SearchPage>()
        searchEngine.searchPaged("hello", null, null, null, pageSize = 50, offset = 0).collect { pages.add(it) }

        assert(pages.single().items == items)
        assert(pages.single().isLastPage)
    }

    @Test
    fun `searchPaged keeps has more when the page is full`() = runTest {
        val items = (1..50).map {
            CapturedItem(text = "full $it", appPackage = "com.x", contentType = "text", timestamp = it.toLong())
        }
        every { captureDao.searchFtsPaged(any(), any(), any(), any(), any(), any()) } returns flowOf(items)

        val pages = mutableListOf<SearchPage>()
        searchEngine.searchPaged("hello", null, null, null, pageSize = 50, offset = 0).collect { pages.add(it) }

        assert(!pages.single().isLastPage)
    }

    @Test
    fun `recentPaged surfaces filters to the like dao`() = runTest {
        val range = 1000L to 2000L
        searchEngine.recentPaged("notification", "signal", range, pageSize = 50, offset = 50).collect { }

        verify { captureDao.searchLikePaged(emptyList<String>(), range, "notification", "signal", 50L, 50L) }
    }
}
