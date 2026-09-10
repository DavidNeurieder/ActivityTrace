package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SearchEngineTest {

    private val captureDao: CaptureDao = mockk(relaxed = true)
    private lateinit var searchEngine: SearchEngine

    @Before
    fun setUp() {
        every { captureDao.searchFtsCandidates(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        every { captureDao.searchFtsRecentCandidates(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
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

        verify { captureDao.searchFtsCandidates("hello world", null, null, null) }
    }

    @Test
    fun `search with single keyword makes a full-word token`() = runTest {
        searchEngine.search("hello").collect { }

        verify { captureDao.searchFtsCandidates("hello", null, null, null) }
    }

    @Test
    fun `search with wildcard drops star and makes full-word token`() = runTest {
        searchEngine.search("hello*").collect { }

        verify { captureDao.searchFtsCandidates("hello", null, null, null) }
    }

    @Test
    fun `search with leading wildcard drops star and makes full-word token`() = runTest {
        searchEngine.search("*hello").collect { }

        verify { captureDao.searchFtsCandidates("hello", null, null, null) }
    }

    @Test
    fun `search with surrounding wildcards drops stars and makes full-word token`() = runTest {
        searchEngine.search("*hello*").collect { }

        verify { captureDao.searchFtsCandidates("hello", null, null, null) }
    }

    @Test
    fun `search with time range passes it to fts dao`() = runTest {
        searchEngine.search("hello today").collect { }

        verify { captureDao.searchFtsCandidates("hello", any(), null, null) }
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

        verify { captureDao.searchFtsCandidates("tomorrow", any(), null, null) }
    }

    @Test
    fun `search with type filter passes contentType to fts dao`() = runTest {
        searchEngine.search("type:notification hello").collect { }

        verify { captureDao.searchFtsCandidates("hello", null, "notification", null) }
    }

    @Test
    fun `search with in filter passes appPackage to fts dao`() = runTest {
        searchEngine.search("in:signal meeting").collect { }

        verify { captureDao.searchFtsCandidates("meeting", null, null, "signal") }
    }

    @Test
    fun `search with combined type and in filters and keyword`() = runTest {
        searchEngine.search("in:com.example type:screen notes").collect { }

        verify { captureDao.searchFtsCandidates("notes", null, "screen", "com.example") }
    }

    @Test
    fun `search with operator chars quotes the token`() = runTest {
        searchEngine.search("C++").collect { }

        verify { captureDao.searchFtsCandidates("\"c++\"", null, null, null) }
    }

    @Test
    fun `search with colon char quotes the token`() = runTest {
        searchEngine.search("3:30").collect { }

        verify { captureDao.searchFtsCandidates("\"3:30\"", null, null, null) }
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
    fun `searchPaged retrieves candidates and paginates after ranking`() = runTest {
        val items = (1..60).map { i ->
            CapturedItem(
                text = "found $i",
                appPackage = "com.x",
                contentType = "text",
                timestamp = (60 - i).toLong(),
                id = i.toLong(),
            )
        }
        every { captureDao.searchFtsCandidates("hello", null, null, null, any()) } returns flowOf(
            items.map { SearchCandidate(it, -it.id.toDouble()) },
        )
        every { captureDao.searchFtsRecentCandidates("hello", null, null, null, any()) } returns flowOf(emptyList())

        val pages = mutableListOf<SearchPage>()
        searchEngine.searchPaged("hello", null, null, null, pageSize = 10, offset = 20).collect { pages.add(it) }

        val page = pages.single()
        val ranked = items.reversed()
        assertEquals(ranked.slice(20 until 30).map { it.text }, page.items.map { it.text })
        assertEquals(10, page.items.size)
        assert(!page.isLastPage)
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

        verify(exactly = 0) { captureDao.searchFtsCandidates(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `searchPaged marks a short page as the last page`() = runTest {
        val items = listOf(
            CapturedItem(text = "found", appPackage = "com.x", contentType = "text", timestamp = 1L)
        )
        every { captureDao.searchFtsCandidates(any(), any(), any(), any(), any()) } returns flowOf(
            items.map { SearchCandidate(it, -1.0) },
        )
        every { captureDao.searchFtsRecentCandidates(any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        val pages = mutableListOf<SearchPage>()
        searchEngine.searchPaged("hello", null, null, null, pageSize = 50, offset = 0).collect { pages.add(it) }

        assert(pages.single().items == items)
        assert(pages.single().isLastPage)
    }

    @Test
    fun `searchPaged keeps has more when the page is full`() = runTest {
        val items = (1..50).map {
            CapturedItem(text = "full $it", appPackage = "com.x", contentType = "text", timestamp = it.toLong(), id = it.toLong())
        }
        every { captureDao.searchFtsCandidates(any(), any(), any(), any(), any()) } returns flowOf(
            items.map { SearchCandidate(it, -1.0) },
        )
        every { captureDao.searchFtsRecentCandidates(any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        val pages = mutableListOf<SearchPage>()
        searchEngine.searchPaged("hello", null, null, null, pageSize = 50, offset = 0).collect { pages.add(it) }

        assert(!pages.single().isLastPage)
    }

    @Test
    fun `search pulls both the bm25 and the recent candidate pools`() = runTest {
        val item = CapturedItem(text = "hit", appPackage = "com.x", contentType = "text", timestamp = 7L, id = 9L)
        every { captureDao.searchFtsCandidates("hello", null, null, null, any()) } returns flowOf(
            listOf(SearchCandidate(item, -3.0)),
        )
        every { captureDao.searchFtsRecentCandidates("hello", null, null, null, any()) } returns flowOf(emptyList())

        val hits = mutableListOf<List<CapturedItem>>()
        searchEngine.search("hello").collect { hits.add(it) }

        verify { captureDao.searchFtsRecentCandidates("hello", null, null, null, SearchConfig.RECENT_CANDIDATE_LIMIT.toLong()) }
        assertEquals(listOf(item), hits.single())
    }

    @Test
    fun `time range lowers the recency weight`() = runTest {
        val ranker = mockk<SearchRanker>()
        val range = 1000L to 2000L
        searchEngine = SearchEngine(captureDao, ranker)
        every { ranker.rank(any(), SearchConfig.RECENCY_WEIGHT_WITH_TIME_FILTER) } returns emptyList()
        every { ranker.rank(any(), SearchConfig.RECENCY_WEIGHT) } returns emptyList()

        searchEngine.search("hello today").collect { }

        verify { ranker.rank(any(), SearchConfig.RECENCY_WEIGHT_WITH_TIME_FILTER) }
    }

    @Test
    fun `recentPaged surfaces filters to the like dao`() = runTest {
        val range = 1000L to 2000L
        searchEngine.recentPaged("notification", "signal", range, pageSize = 50, offset = 50).collect { }

        verify { captureDao.searchLikePaged(emptyList<String>(), range, "notification", "signal", 50L, 50L) }
    }
}
