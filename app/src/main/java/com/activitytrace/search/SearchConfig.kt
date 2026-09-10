package com.activitytrace.search

/**
 * Single source of truth for search configuration. Keeping the FTS BM25
 * column weights next to the ranking weights makes the relationship between
 * the SQLite index and the application ranking explicit.
 *
 * The FTS5 index (`captured_items_fts`, schema v9) is declared as
 * `fts5(text, app_name, content=captured_items, content_rowid=id)` — the
 * *column order matters* for `bm25()` positional weights:
 *
 * ```
 * column 0 = text
 * column 1 = app_name
 * ```
 */
object SearchConfig {

    /** BM25 weight for `text` (column 0): primary relevance signal. */
    const val FTS_TEXT_WEIGHT: Double = 1.0

    /** BM25 weight for `app_name` (column 1): strong contextual signal. */
    const val FTS_APP_NAME_WEIGHT: Double = 2.0

    /** Relevance weight in the RRF combination. */
    const val BM25_WEIGHT: Double = 0.8

    /** Recency weight in the RRF combination (no explicit time filter). */
    const val RECENCY_WEIGHT: Double = 0.2

    /**
     * Recency weight used when the user supplied an explicit time filter
     * ("today", "March", date ranges). The user has already constrained the
     * temporal context, so recency should not overpower it.
     */
    const val RECENCY_WEIGHT_WITH_TIME_FILTER: Double = 0.1

    /** How many best-BM25 candidates to retrieve. */
    const val BM25_CANDIDATE_LIMIT: Int = 200

    /** How many most-recent matching candidates to retrieve. */
    const val RECENT_CANDIDATE_LIMIT: Int = 100

    /** The `k` smoothing constant of Reciprocal Rank Fusion. */
    const val RRF_K: Int = 60
}