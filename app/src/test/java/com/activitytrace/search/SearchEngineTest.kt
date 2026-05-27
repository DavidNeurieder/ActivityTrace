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
    fun `search with no wildcard uses fts with auto prefix`() = runTest {
        searchEngine.search("hello world").collect { }

        verify { captureDao.search("hello* AND world*", null) }
    }

    @Test
    fun `search with single keyword uses fts with auto prefix`() = runTest {
        searchEngine.search("hello").collect { }

        verify { captureDao.search("hello*", null) }
    }

    @Test
    fun `search with wildcard uses like fallback`() = runTest {
        searchEngine.search("hello*").collect { }

        verify { captureDao.searchLike(listOf("hello%"), null) }
    }

    @Test
    fun `search with leading wildcard uses like fallback`() = runTest {
        searchEngine.search("*hello").collect { }

        verify { captureDao.searchLike(listOf("%hello"), null) }
    }

    @Test
    fun `search with surrounding wildcard uses like fallback`() = runTest {
        searchEngine.search("*hello*").collect { }

        verify { captureDao.searchLike(listOf("%hello%"), null) }
    }

    @Test
    fun `search with time range passes it to fts dao`() = runTest {
        searchEngine.search("hello today").collect { }

        verify { captureDao.search("hello*", any()) }
    }

    @Test
    fun `search with wildcard passes time range to like dao`() = runTest {
        searchEngine.search("*hello today").collect { }

        verify { captureDao.searchLike(listOf("%hello"), any()) }
    }

    @Test
    fun `search with empty string returns empty`() = runTest {
        val result = mutableListOf<List<CapturedItem>>()
        searchEngine.search("").collect { result.add(it) }

        assert(result[0].isEmpty())
    }

    @Test
    fun `search strips time keywords from fts query`() = runTest {
        searchEngine.search("today tomorrow").collect { }

        verify { captureDao.search("tomorrow*", any()) }
    }
}
