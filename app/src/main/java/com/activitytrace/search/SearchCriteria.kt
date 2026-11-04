package com.activitytrace.search

import java.util.Locale

/**
 * Everything that decides whether a captured item matches the current search:
 * the raw text the user typed plus the filter chips.
 *
 * Search results and statistics both consume the *same* [SearchCriteria], so a
 * count can never disagree with the search it describes.
 */
data class SearchCriteria(
    val rawQuery: String = "",
    val contentType: String? = null,
    val appPackage: String? = null,
    val dateRange: Pair<Long, Long>? = null,
    val bookmarkedOnly: Boolean = false,
)

/**
 * [SearchCriteria] after the raw query has been parsed into its FTS5 match
 * expression and effective filters.
 *
 * - [keywords] is empty when the query has no keyword component (browsing the
 *   recent list, or a filter-only query such as `type:notification`).
 * - [matchQuery] is the FTS5 `MATCH` expression; it is blank when the keywords
 *   dissolve into nothing (for example a bare `*`), which means "no matches".
 */
data class ResolvedSearchCriteria(
    val keywords: List<String> = emptyList(),
    val matchQuery: String = "",
    val contentType: String? = null,
    val appPackage: String? = null,
    val timeRange: Pair<Long, Long>? = null,
    val bookmarkedOnly: Boolean = false,
) {
    /** Whether the search has a keyword component at all. */
    val hasKeywordComponent: Boolean get() = keywords.isNotEmpty()

    /** Keyword component present but unusable (e.g. `*`): nothing can match. */
    val matchesNothing: Boolean get() = hasKeywordComponent && matchQuery.isBlank()

    /** True when no filter narrows the query. */
    val hasAnyFilter: Boolean
        get() = contentType != null || appPackage != null || timeRange != null || bookmarkedOnly
}

/**
 * Turns user-facing [SearchCriteria] into the single resolved definition used
 * by both the paged result query and the statistics aggregates. Query parsing
 * and filter precedence live here and nowhere else.
 */
object SearchCriteriaResolver {

    /** Explicit UI filters win over filters written inside the query text. */
    fun resolve(criteria: SearchCriteria): ResolvedSearchCriteria {
        val parsed = QueryParser.parse(criteria.rawQuery)
        return ResolvedSearchCriteria(
            keywords = parsed.keywords,
            matchQuery = matchQueryFor(parsed.keywords),
            contentType = criteria.contentType ?: parsed.typeFilter,
            appPackage = criteria.appPackage ?: parsed.appFilter,
            timeRange = criteria.dateRange ?: parsed.timeRange,
            bookmarkedOnly = criteria.bookmarkedOnly,
        )
    }

    /**
     * Builds the FTS5 MATCH expression for the parsed keywords, or an empty
     * string when every keyword dissolves into nothing (for example a bare
     * `*`). An empty result means "no matches".
     */
    fun matchQueryFor(keywords: List<String>): String {
        return keywords.asSequence()
            .map { it.replace("*", "") }
            .filter { it.isNotBlank() }
            .map { ftsToken(it) }
            .joinToString(" ")
    }

    /**
     * Maps a single search keyword to an FTS5 MATCH token. Every keyword gets
     * an implicit prefix marker so bare input (`chat`) finds partial words
     * (`chatterbox`) — the same mechanism that makes `whats` find WhatsApp
     * instead of silently returning nothing. Tokens containing FTS5 operators
     * or operator keywords (AND, OR, NOT, NEAR) are quoted with double quotes —
     * quoting is also a natural way to search literal punctuation and keeps
     * operator words (`or`) from being parsed as operators. The prefix marker
     * is applied outside the quotes (`"c++"*`).
     */
    private fun ftsToken(token: String): String {
        val lower = token.lowercase(Locale.ROOT)
        val isPlainWord = token.all { it.isLetterOrDigit() || it == '_' } && lower !in FTS_OPERATORS
        return if (isPlainWord) {
            token + "*"
        } else {
            "\"" + token.replace("\"", "\"\"") + "\"*"
        }
    }

    val FTS_OPERATORS = setOf("and", "or", "not", "near")
}
