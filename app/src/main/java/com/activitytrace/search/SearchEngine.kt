package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale

data class SearchPage(
    val items: List<CapturedItem>,
    val isLastPage: Boolean = false,
)

class SearchEngine(
    private val captureDao: CaptureDao,
    private val ranker: SearchRanker = SearchRanker(),
) {

    fun recentItems(
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
    ): Flow<List<CapturedItem>> {
        return recentPaged(contentType, appPackage, dateRange, pageSize = PAGE_SIZE, offset = 0)
            .map { it.items }
    }

    fun recentPaged(
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
        pageSize: Int = PAGE_SIZE,
        offset: Int = 0,
    ): Flow<SearchPage> {
        return captureDao.recentPagedQuery(
            contentType = contentType,
            appPackage = appPackage,
            startTime = dateRange?.first,
            endTime = dateRange?.second,
            queryLimit = pageSize.toLong(),
            queryOffset = offset.toLong(),
        ).map { SearchPage(it, it.size < pageSize) }
    }

    fun search(
        rawQuery: String,
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
    ): Flow<List<CapturedItem>> {
        val p = QueryParser.parse(rawQuery)
        val (keywords, effectiveType, effectiveApp, effectiveRange) = resolveFilters(p, contentType, appPackage, dateRange)

        if (keywords.isEmpty()) {
            if (effectiveRange == null && effectiveType == null && effectiveApp == null) {
                return flowOf(emptyList())
            }
            return captureDao.recentPagedQuery(
                contentType = effectiveType,
                appPackage = effectiveApp,
                startTime = effectiveRange?.first,
                endTime = effectiveRange?.second,
                queryLimit = Long.MAX_VALUE,
                queryOffset = 0L,
            )
        }

        val matchQuery = matchQueryFor(keywords)
        if (matchQuery.isBlank()) {
            return flowOf(emptyList())
        }

        return rankAndMap(matchQuery, effectiveRange, effectiveType, effectiveApp) { ranked ->
            ranked.map { it.item }
        }
    }

    fun searchPaged(
        rawQuery: String,
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
        pageSize: Int = PAGE_SIZE,
        offset: Int = 0,
    ): Flow<SearchPage> {
        val p = QueryParser.parse(rawQuery)
        val (keywords, effectiveType, effectiveApp, effectiveRange) = resolveFilters(p, contentType, appPackage, dateRange)

        if (keywords.isEmpty()) {
            if (effectiveRange == null && effectiveType == null && effectiveApp == null) {
                return flowOf(SearchPage(emptyList(), isLastPage = true))
            }
            return captureDao.recentPagedQuery(
                contentType = effectiveType,
                appPackage = effectiveApp,
                startTime = effectiveRange?.first,
                endTime = effectiveRange?.second,
                queryLimit = pageSize.toLong(),
                queryOffset = offset.toLong(),
            ).map { SearchPage(it, it.size < pageSize) }
        }

        val matchQuery = matchQueryFor(keywords)
        if (matchQuery.isBlank()) {
            return flowOf(SearchPage(emptyList(), isLastPage = true))
        }

        return rankAndMap(matchQuery, effectiveRange, effectiveType, effectiveApp) { ranked ->
            val page = ranked.drop(offset).take(pageSize).map { it.item }
            SearchPage(page, page.size < pageSize)
        }
    }

    /**
     * Runs the two-tier candidate retrieval (BM25 pool + recency pool), merges
     * them by id, applies RRF ranking and maps the ranked results with
     * [mapResults]. An explicit [timeRange] lowers the recency weight because
     * the user has already constrained the temporal context.
     */
    private fun <T> rankAndMap(
        matchQuery: String,
        timeRange: Pair<Long, Long>?,
        contentType: String?,
        appPackage: String?,
        mapResults: (List<SearchResult>) -> T,
    ): Flow<T> {
        val bm25Pool = captureDao.searchFtsCandidates(matchQuery, timeRange, contentType, appPackage)
        val recentPool = captureDao.searchFtsRecentCandidates(matchQuery, timeRange, contentType, appPackage)
        val effectiveRecencyWeight = if (timeRange != null) {
            SearchConfig.RECENCY_WEIGHT_WITH_TIME_FILTER
        } else {
            SearchConfig.RECENCY_WEIGHT
        }
        return combine(bm25Pool, recentPool) { bm25Candidates, recentCandidates ->
            val merged = mergeCandidates(bm25Candidates, recentCandidates)
            ranker.rank(merged, effectiveRecencyWeight)
        }.map { mapResults(it) }
    }

    private data class ResolvedQuery(
        val keywords: List<String>,
        val type: String?,
        val app: String?,
        val range: Pair<Long, Long>?,
    )

    private fun resolveFilters(
        parsed: ParsedQuery,
        contentType: String?,
        appPackage: String?,
        dateRange: Pair<Long, Long>?,
    ): ResolvedQuery {
        return ResolvedQuery(
            keywords = parsed.keywords,
            type = contentType ?: parsed.typeFilter,
            app = appPackage ?: parsed.appFilter,
            range = dateRange ?: parsed.timeRange,
        )
    }

    /**
     * Builds the FTS5 MATCH expression for the parsed keywords, or an empty
     * string when every keyword dissolves into nothing (for example a bare
     * `*`). An empty result means "no matches".
     */
    private fun matchQueryFor(keywords: List<String>): String {
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

    private companion object {
        const val PAGE_SIZE: Int = 50
        val FTS_OPERATORS = setOf("and", "or", "not", "near")
    }
}
