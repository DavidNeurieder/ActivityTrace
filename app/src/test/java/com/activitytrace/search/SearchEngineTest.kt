package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
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
}
