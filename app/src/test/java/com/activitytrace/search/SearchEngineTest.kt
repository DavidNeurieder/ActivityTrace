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
    fun `search wraps keyword in percent signs for substring match`() = runTest {
        searchEngine.search("hello world").collect { }

        verify { captureDao.searchLike(listOf("%hello%", "%world%"), null) }
    }

    @Test
    fun `search with single keyword wraps in percent signs`() = runTest {
        searchEngine.search("hello").collect { }

        verify { captureDao.searchLike(listOf("%hello%"), null) }
    }

    @Test
    fun `search with wildcard converts star to percent and wraps`() = runTest {
        searchEngine.search("hello*").collect { }

        verify { captureDao.searchLike(listOf("%hello%"), null) }
    }

    @Test
    fun `search with leading wildcard converts and wraps`() = runTest {
        searchEngine.search("*hello").collect { }

        verify { captureDao.searchLike(listOf("%hello%"), null) }
    }

    @Test
    fun `search with surrounding wildcard converts and wraps`() = runTest {
        searchEngine.search("*hello*").collect { }

        verify { captureDao.searchLike(listOf("%hello%"), null) }
    }

    @Test
    fun `search with time range passes it to like dao`() = runTest {
        searchEngine.search("hello today").collect { }

        verify { captureDao.searchLike(listOf("%hello%"), any()) }
    }

    @Test
    fun `search with wildcard passes time range`() = runTest {
        searchEngine.search("*hello today").collect { }

        verify { captureDao.searchLike(listOf("%hello%"), any()) }
    }

    @Test
    fun `search with empty string returns empty`() = runTest {
        val result = mutableListOf<List<CapturedItem>>()
        searchEngine.search("").collect { result.add(it) }

        assert(result[0].isEmpty())
    }

    @Test
    fun `search strips time keywords from pattern`() = runTest {
        searchEngine.search("today tomorrow").collect { }

        verify { captureDao.searchLike(listOf("%tomorrow%"), any()) }
    }

    @Test
    fun `search with type filter passes contentType to dao`() = runTest {
        searchEngine.search("type:notification").collect { }

        verify { captureDao.searchLike(emptyList<String>(), null, "notification", null) }
    }

    @Test
    fun `search with type synonym maps to canonical value`() = runTest {
        searchEngine.search("type:accessibility").collect { }

        verify { captureDao.searchLike(emptyList<String>(), null, "screen", null) }
    }

    @Test
    fun `search with type synonym notif maps to notification`() = runTest {
        searchEngine.search("type:notif").collect { }

        verify { captureDao.searchLike(emptyList<String>(), null, "notification", null) }
    }

    @Test
    fun `search with in filter passes appPackage to dao`() = runTest {
        searchEngine.search("in:signal").collect { }

        verify { captureDao.searchLike(emptyList<String>(), null, null, "signal") }
    }

    @Test
    fun `search with combined type and in filters`() = runTest {
        searchEngine.search("in:com.example type:screen").collect { }

        verify { captureDao.searchLike(emptyList<String>(), null, "screen", "com.example") }
    }

    @Test
    fun `search with type filter and keyword`() = runTest {
        searchEngine.search("type:notification hello").collect { }

        verify { captureDao.searchLike(listOf("%hello%"), null, "notification", null) }
    }
}
