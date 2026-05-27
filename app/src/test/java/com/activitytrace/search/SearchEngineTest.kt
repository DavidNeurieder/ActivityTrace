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
    fun `search calls dao with keywords joined by AND`() = runTest {
        searchEngine.search("hello world").collect { }

        verify { captureDao.search("hello AND world", null) }
    }

    @Test
    fun `search with single keyword`() = runTest {
        searchEngine.search("hello").collect { }

        verify { captureDao.search("hello", null) }
    }

    @Test
    fun `search with time range passes it to dao`() = runTest {
        searchEngine.search("hello today").collect { }

        verify { captureDao.search("hello", any()) }
    }

    @Test
    fun `search with empty string passes empty to dao`() = runTest {
        searchEngine.search("").collect { }

        verify { captureDao.search("", null) }
    }

    @Test
    fun `search strips time keywords from fts query`() = runTest {
        searchEngine.search("today tomorrow").collect { }

        verify { captureDao.search("tomorrow", any()) }
    }
}
