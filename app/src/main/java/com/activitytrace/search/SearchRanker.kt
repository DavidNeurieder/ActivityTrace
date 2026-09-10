package com.activitytrace.search

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.activitytrace.model.CapturedItem
import kotlin.math.exp

/**
 * A single BM25 candidate retrieved from the FTS5 index, before any
 * application-layer ranking has been applied. [bm25Score] is the raw FTS5
 * BM25 value where **smaller is better** (more negative = more relevant).
 */
data class SearchCandidate(
    @Embedded
    val item: CapturedItem,
    @ColumnInfo(name = "bm25_score")
    val bm25Score: Double,
)

/**
 * A fully ranked search result carrying the individual signal scores so that
 * the ranking is easy to debug and tune.
 */
data class SearchResult(
    val item: CapturedItem,
    val bm25Score: Double,
    val bm25Normalized: Double,
    val recencyScore: Double,
    val finalScore: Double,
)

/**
 * Two-stage search ranking: FTS5 BM25 relevance and recency are combined into
 * a single score. BM25 values are min-max normalized to [0,1] across the
 * candidate pool (raw FTS5 scores are negative and scale differently from a
 * recency score), and recency decays exponentially with age.
 *
 * Pure Kotlin so the whole algorithm is unit-testable without SQLite.
 */
class SearchRanker(
    private val bm25Weight: Double = BM25_WEIGHT,
    private val recencyWeight: Double = RECENCY_WEIGHT,
    private val halfLifeMs: Double = RECENCY_HALF_LIFE_MS,
) {

    fun rank(candidates: List<SearchCandidate>, now: Long): List<SearchResult> {
        if (candidates.isEmpty()) return emptyList()

        val best = candidates.minOf { it.bm25Score }
        val worst = candidates.maxOf { it.bm25Score }
        val bm25Range = worst - best

        val ranked = ArrayList<SearchResult>(candidates.size)
        for (candidate in candidates) {
            val bm25Normalized = if (bm25Range == 0.0) {
                1.0
            } else {
                (worst - candidate.bm25Score) / bm25Range
            }
            val age = maxOf(0L, now - candidate.item.timestamp).toDouble()
            val recency = exp(-AGE_DECAY * age / halfLifeMs)
            val finalScore = bm25Weight * bm25Normalized + recencyWeight * recency
            ranked += SearchResult(
                item = candidate.item,
                bm25Score = candidate.bm25Score,
                bm25Normalized = bm25Normalized.coerceIn(0.0, 1.0),
                recencyScore = recency.coerceIn(0.0, 1.0),
                finalScore = finalScore,
            )
        }

        return ranked.sortedWith(
            compareByDescending<SearchResult> { it.finalScore }
                .thenByDescending { it.item.timestamp }
                .thenByDescending { it.item.id },
        )
    }

    companion object {
        /** BM25 weight for the `text` column of the FTS5 index. */
        const val BM25_TEXT_WEIGHT: Double = 1.0

        /** BM25 weight for the `app_name` column of the FTS5 index. */
        const val BM25_APP_WEIGHT: Double = 2.0

        /** How many BM25 candidates to retrieve before application ranking. */
        const val SEARCH_CANDIDATE_LIMIT: Int = 200

        /** Recency half-life in days; recency(age == half-life) == 0.5. */
        const val RECENCY_HALF_LIFE_DAYS: Double = 30.0

        const val BM25_WEIGHT: Double = 0.8
        const val RECENCY_WEIGHT: Double = 0.2

        /** ln(2) / half-life so a result at the half-life age scores exactly 0.5. */
        private const val AGE_DECAY: Double = 0.6931471805599453

        val RECENCY_HALF_LIFE_MS: Double = RECENCY_HALF_LIFE_DAYS * 24.0 * 3600.0 * 1000.0
    }
}