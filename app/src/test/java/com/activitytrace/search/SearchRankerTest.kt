package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerTest {

    private val now = 1_000_000_000_000L
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

        val ranked = ranker.rank(listOf(exact, weak))

        assertEquals(exact.item.id, ranked[0].item.id)
        assertEquals(1, ranked[0].bm25Rank)
        assertEquals(2, ranked[0].recencyRank)
        assertTrue(ranked[0].finalScore > ranked[1].finalScore)
    }

    @Test
    fun `recent result can beat marginally worse old result when pool is large`() {
        val pool = ArrayList<SearchCandidate>(12)
        pool += candidate(id = 1, bm25 = -30.0, timestamp = daysAgo(365))
        pool += candidate(id = 2, bm25 = -29.9, timestamp = now)
        for (id in 3L..10L) {
            pool += candidate(id = id, bm25 = -10.0 - id, timestamp = daysAgo(id.toInt() * 4))
        }

        val ranked = ranker.rank(pool)

        assertEquals(2L, ranked[0].item.id)
        assertEquals(1L, ranked[1].item.id)
    }

    @Test
    fun `default weights combine rrf scores exactly`() {
        val a = candidate(id = 1, bm25 = -5.0, timestamp = daysAgo(10))
        val b = candidate(id = 2, bm25 = -4.9, timestamp = daysAgo(9))

        val ranked = ranker.rank(listOf(a, b))

        val expected = 0.8 * (1.0 / 61.0) + 0.2 * (1.0 / 62.0)
        assertEquals(expected, ranked[0].finalScore, 1e-9)
        assertEquals(1.0 / 61.0, ranked[0].bm25RrfScore, 1e-9)
        assertEquals(1.0 / 62.0, ranked[0].recencyRrfScore, 1e-9)
    }

    @Test
    fun `recency weight can be overridden per call`() {
        val old = candidate(id = 1, bm25 = -30.0, timestamp = daysAgo(1))
        val fresh = candidate(id = 2, bm25 = -29.0, timestamp = now)

        val ranked = ranker.rank(listOf(old, fresh), recencyWeight = 0.0)

        assertTrue(ranked[0].item.id == 1L)
        assertEquals(1.0 / 62.0, ranked[0].recencyRrfScore, 1e-9)
        assertEquals(0.8 * (1.0 / 61.0), ranked[0].finalScore, 1e-9)
    }

    @Test
    fun `identical bm25 orders newer results first`() {
        val old = candidate(id = 1, bm25 = -5.0, timestamp = daysAgo(30))
        val fresh = candidate(id = 2, bm25 = -5.0, timestamp = daysAgo(0))

        val ranked = ranker.rank(listOf(old, fresh))

        assertEquals(fresh.item.id, ranked[0].item.id)
        assertEquals(1, fresh.let { r -> ranked[0].recencyRank })
        assertEquals(2, ranked[1].recencyRank)
    }

    @Test
    fun `identical bm25 and timestamps break ties by id descending`() {
        val a = candidate(id = 5, bm25 = -5.0, timestamp = daysAgo(10))
        val b = candidate(id = 3, bm25 = -5.0, timestamp = daysAgo(10))

        val ranked = ranker.rank(listOf(a, b))

        assertEquals(listOf(5L, 3L), ranked.map { it.item.id })
    }

    @Test
    fun `ranking is invariant to bm25 scale`() {
        val one = listOf(candidate(1, -10.0, daysAgo(5)), candidate(2, -5.0, daysAgo(4)))
        val scaled = listOf(candidate(1, -10_000.0, daysAgo(5)), candidate(2, -5_000.0, daysAgo(4)))

        assertEquals(ranker.rank(one).map { it.item.id }, ranker.rank(scaled).map { it.item.id })
    }

    @Test
    fun `bm25 rank orders candidates by relevance`() {
        val best = candidate(id = 1, bm25 = -30.0, timestamp = daysAgo(100))
        val mid = candidate(id = 2, bm25 = -10.0, timestamp = daysAgo(50))
        val worst = candidate(id = 3, bm25 = -5.0, timestamp = now)

        val ranked = ranker.rank(listOf(worst, best, mid))

        val byId = ranked.associateBy { it.item.id }
        assertEquals(1, byId[1L]!!.bm25Rank)
        assertEquals(2, byId[2L]!!.bm25Rank)
        assertEquals(3, byId[3L]!!.bm25Rank)
    }

    @Test
    fun `recency rank orders candidates by timestamp descending`() {
        val new = candidate(id = 1, bm25 = -5.0, timestamp = now)
        val mid = candidate(id = 2, bm25 = -10.0, timestamp = daysAgo(20))
        val old = candidate(id = 3, bm25 = -30.0, timestamp = daysAgo(80))

        val ranked = ranker.rank(listOf(old, new, mid))

        val byId = ranked.associateBy { it.item.id }
        assertEquals(1, byId[1L]!!.recencyRank)
        assertEquals(2, byId[2L]!!.recencyRank)
        assertEquals(3, byId[3L]!!.recencyRank)
    }

    @Test
    fun `empty candidate set returns empty without error`() {
        assertTrue(ranker.rank(emptyList()).isEmpty())
    }
}

class MergeCandidatesTest {

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

    @Test
    fun `merges pools and dedupes by item id`() {
        val bm25 = listOf(candidate(1, -5.0, 100L), candidate(2, -4.0, 200L))
        val recent = listOf(candidate(3, -1.0, 300L), candidate(2, -4.0, 200L))

        val merged = mergeCandidates(bm25, recent)

        assertEquals(listOf(1L, 2L, 3L), merged.map { it.item.id })
    }
}