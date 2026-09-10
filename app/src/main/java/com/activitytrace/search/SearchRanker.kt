package com.activitytrace.search

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.activitytrace.model.CapturedItem

/**
 * A single candidate retrieved from the SQLite layer, before any application
 * ranking. [bm25Score] is the raw FTS5 BM25 value where **smaller is better**
 * (more negative = more relevant). Candidate lists arrive already ordered by
 * BM25 relevance from `ORDER BY rank`.
 */
data class SearchCandidate(
    @Embedded
    val item: CapturedItem,
    @ColumnInfo(name = "bm25_score")
    val bm25Score: Double,
)

/**
 * A fully ranked search result carrying the individual ranking signals so the
 * ranking is easy to debug and tune. [bm25Score] is the raw FTS5 value kept
 * for debugging; the actual fusion runs on [bm25Rank] / [recencyRank].
 */
data class SearchResult(
    val item: CapturedItem,
    val bm25Score: Double,
    val bm25Rank: Int,
    val recencyRank: Int,
    val bm25RrfScore: Double,
    val recencyRrfScore: Double,
    val finalScore: Double,
)

/**
 * Merges the BM25 pool and the recency pool by item id. A result that appears
 * in both pools is kept once.
 */
fun mergeCandidates(
    bm25Candidates: List<SearchCandidate>,
    recentCandidates: List<SearchCandidate>,
): List<SearchCandidate> = (bm25Candidates + recentCandidates)
    .associateBy { it.item.id }
    .values
    .toList()

/**
 * Two-signal search ranking using Reciprocal Rank Fusion (RRF).
 *
 * Rank fusion is used instead of min-max BM25 normalization because a
 * normalized score depends on the other documents in a particular candidate
 * pool. RRF converts each document's BM25 position and recency position into
 * `1 / (k + rank)` scores, so the output depends only on the *ordering* of the
 * pool — it is invariant to the absolute BM25 scale and to how the BM25
 * distribution differs between queries.
 *
 * Pure Kotlin so the whole algorithm is unit-testable without SQLite.
 */
class SearchRanker(
    private val bm25Weight: Double = SearchConfig.BM25_WEIGHT,
    private val recencyWeight: Double = SearchConfig.RECENCY_WEIGHT,
    private val rrfK: Double = SearchConfig.RRF_K.toDouble(),
) {

    fun rank(
        candidates: List<SearchCandidate>,
        recencyWeight: Double = this.recencyWeight,
    ): List<SearchResult> {
        if (candidates.isEmpty()) return emptyList()

        val bm25Ranks = byRelevance(candidates)
        val recencyRanks = byRecency(candidates)

        val ranked = ArrayList<SearchResult>(candidates.size)
        for (candidate in candidates) {
            val bm25Rank = bm25Ranks[candidate.item.id]!!
            val recencyRank = recencyRanks[candidate.item.id]!!
            val bm25RrfScore = rrf(bm25Rank)
            val recencyRrfScore = rrf(recencyRank)
            ranked += SearchResult(
                item = candidate.item,
                bm25Score = candidate.bm25Score,
                bm25Rank = bm25Rank,
                recencyRank = recencyRank,
                bm25RrfScore = bm25RrfScore,
                recencyRrfScore = recencyRrfScore,
                finalScore = bm25Weight * bm25RrfScore + recencyWeight * recencyRrfScore,
            )
        }

        return ranked.sortedWith(
            compareByDescending<SearchResult> { it.finalScore }
                .thenByDescending { it.item.timestamp }
                .thenByDescending { it.item.id },
        )
    }

    private fun byRelevance(candidates: List<SearchCandidate>): Map<Long, Int> =
        candidates.sortedWith(
            compareBy<SearchCandidate> { it.bm25Score }
                .thenByDescending { it.item.timestamp }
                .thenByDescending { it.item.id },
        ).withIndex().associate { (index, candidate) -> candidate.item.id to index + 1 }

    private fun byRecency(candidates: List<SearchCandidate>): Map<Long, Int> =
        candidates.sortedWith(
            compareByDescending<SearchCandidate> { it.item.timestamp }
                .thenByDescending { it.item.id },
        ).withIndex().associate { (index, candidate) -> candidate.item.id to index + 1 }

    private fun rrf(rank: Int): Double = 1.0 / (rrfK + rank)
}