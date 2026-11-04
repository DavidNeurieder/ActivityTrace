package com.activitytrace.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCriteriaResolverTest {

    @Test
    fun `blank criteria resolves to browsing with no filters`() {
        val resolved = SearchCriteriaResolver.resolve(SearchCriteria())

        assertFalse(resolved.hasKeywordComponent)
        assertFalse(resolved.hasAnyFilter)
        assertFalse(resolved.matchesNothing)
        assertEquals("", resolved.matchQuery)
    }

    @Test
    fun `query filters are parsed into the resolved criteria`() {
        val resolved = SearchCriteriaResolver.resolve(
            SearchCriteria(rawQuery = "in:signal type:notification meeting"),
        )

        assertTrue(resolved.hasKeywordComponent)
        assertEquals("meeting*", resolved.matchQuery)
        assertEquals("notification", resolved.contentType)
        assertEquals("signal", resolved.appPackage)
    }

    @Test
    fun `explicit ui filters win over filters inside the query text`() {
        val resolved = SearchCriteriaResolver.resolve(
            SearchCriteria(
                rawQuery = "in:signal type:notification meeting",
                contentType = "screen",
                appPackage = "com.chosen",
            ),
        )

        assertEquals("screen", resolved.contentType)
        assertEquals("com.chosen", resolved.appPackage)
    }

    @Test
    fun `bookmarked only is carried through`() {
        val resolved = SearchCriteriaResolver.resolve(SearchCriteria(bookmarkedOnly = true))

        assertTrue(resolved.bookmarkedOnly)
        assertTrue(resolved.hasAnyFilter)
    }

    @Test
    fun `bare wildcard has a keyword component but matches nothing`() {
        val resolved = SearchCriteriaResolver.resolve(SearchCriteria(rawQuery = "*"))

        assertTrue(resolved.hasKeywordComponent)
        assertTrue(resolved.matchesNothing)
    }

    @Test
    fun `operator words are quoted and prefixed`() {
        assertEquals(
            "\"or\"* \"and\"*",
            SearchCriteriaResolver.matchQueryFor(listOf("or", "and")),
        )
    }

    @Test
    fun `tokens with punctuation are quoted and prefixed`() {
        assertEquals("hello* \"C++\"*", SearchCriteriaResolver.matchQueryFor(listOf("hello", "C++")))
    }

    @Test
    fun `daily bucket count defaults to thirty days`() {
        assertEquals(DEFAULT_DAILY_BUCKETS, SearchCriteria().dailyBucketCount())
    }

    @Test
    fun `daily bucket count follows the selected range`() {
        val start = 1_000_000L
        val week = SearchCriteria(dateRange = start to start + 7 * 86_400_000L)
        assertEquals(7, week.dailyBucketCount())
    }

    @Test
    fun `daily bucket count is never below one day`() {
        val tiny = SearchCriteria(dateRange = 0L to 1_000L)
        assertEquals(1, tiny.dailyBucketCount())
    }
}
