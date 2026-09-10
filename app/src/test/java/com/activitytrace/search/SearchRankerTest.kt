package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {

    private val now = 1_000_000_000_000L
    private val halfLife = SearchRanker.RECENCY_HALF_LIFE_MS
    private val ranker = SearchRanker()

    private fun candidate(
        id: Long,
        bm25: Double,
        timestamp: Long,
    ) = SearchCandidate(
        item = CapturedItem(
            id = id,
            text = "t$id",
            appPackage = "com.x",
            contentType = "screen",
            timestamp = timestamp,
        ),
        bm25Score = bm25,
    )

    private fun daysAgo(days: Int): Long = now - days * 24L * 3600L * 1000L

    @Test
    fun `exact match beats weak match even when much older`() {
        val exact = candidate(id = 1, bm25 = -20.0, timestamp = daysAgo(200))
        val weak = candidate(id = 2, bm25 = -1.0, timestamp = daysAgo(1))

        val ranked = ranker.rank(listOf(exact, weak), now)

        assertEquals(exact.item.id, ranked[0].item.id)
        assertEquals(1.0, ranked[0].bm25Normalized, 1e-9)
        assertEquals(0.0, ranked[1].bm25Normalized, 1e-9)
        assertTrue(ranked[0].finalScore > ranked[1].finalScore)
    }

    @Test
    fun `recent result can beat marginally better old result`() {
        val top = candidate(id = 1, bm25 = -20.0, timestamp = now)
        val old = candidate(id = 2, bm25 = -12.0, timestamp = daysAgo(90))
        val recent = candidate(id = 3, bm25 = -11.8, timestamp = daysAgo(0))

        val ranked = ranker.rank(listOf(top, old, recent), now)

        assertEquals(top.item.id, ranked[0].item.id)
        assertEquals(recent.item.id, ranked[1].item.id)
        assertEquals(old.item.id, ranked[2].item.id)
    }

    @Test
    fun `identical bm25 orders newer results first`() {
        val old = candidate(id = 1, bm25 = -5.0, timestamp = daysAgo(30))
        val fresh = candidate(id = 2, bm25 = -5.0, timestamp = daysAgo(0))

        val ranked = ranker.rank(listOf(old, fresh), now)

        assertEquals(fresh.item.id, ranked[0].item.id)
        assertTrue(ranked[0].recencyScore > ranked[1].recencyScore)
    }

    @Test
    fun `identical bm25 and timestamps break ties by id descending`() {
        val a = candidate(id = 5, bm25 = -5.0, timestamp = daysAgo(10))
        val b = candidate(id = 3, bm25 = -5.0, timestamp = daysAgo(10))

        val ranked = ranker.rank(listOf(a, b), now)

        assertEquals(listOf(5L, 3L), ranked.map { it.item.id })
    }

    @Test
    fun `identical bm25 values never divide by zero`() {
        val a = candidate(id = 1, bm25 = -5.0, timestamp = daysAgo(10))
        val b = candidate(id = 2, bm25 = -5.0, timestamp = daysAgo(5))

        val ranked = ranker.rank(listOf(a, b), now)

        assertEquals(2, ranked.size)
        assertEquals(1.0, ranked[0].bm25Normalized, 1e-9)
        assertEquals(1.0, ranked[1].bm25Normalized, 1e-9)
    }

    @Test
    fun `future timestamp caps recency at 1`() {
        val future = candidate(id = 1, bm25 = -5.0, timestamp = now + daysAgo(-5))

        val ranked = ranker.rank(listOf(future), now)

        assertEquals(1.0, ranked[0].recencyScore, 1e-9)
        assertEquals(1.0, ranked[0].bm25Normalized, 1e-9)
    }

    @Test
    fun `very old result approaches zero recency but never goes negative`() {
        val ancient = candidate(id = 1, bm25 = -5.0, timestamp = daysAgo(300))

        val ranked = ranker.rank(listOf(ancient), now)

        assertTrue(ranked[0].recencyScore > 0.0)
        assertTrue(ranked[0].recencyScore < 0.001)
    }

    @Test
    fun `half life means a result exactly half-life old scores 0_5 recency`() {
        val aged = candidate(id = 1, bm25 = -5.0, timestamp = now - halfLife.toLong())

        val ranked = ranker.rank(listOf(aged), now)

        assertEquals(0.5, ranked[0].recencyScore, 0.01)
    }

    @Test
    fun `empty candidate set returns empty without error`() {
        val ranked = ranker.rank(emptyList(), now)

        assertTrue(ranked.isEmpty())
    }
}