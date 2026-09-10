package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow
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
        return captureDao.searchLikePaged(
            patterns = emptyList(),
            timeRange = dateRange,
            contentType = contentType,
            appPackage = appPackage,
            limit = pageSize.toLong(),
            offset = offset.toLong(),
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
            return captureDao.searchLike(
                patterns = emptyList(),
                timeRange = effectiveRange,
                contentType = effectiveType,
                appPackage = effectiveApp,
            )
        }

        val matchQuery = matchQueryFor(keywords)
        if (matchQuery.isBlank()) {
            return flowOf(emptyList())
        }

        return captureDao.searchFtsCandidates(
            matchQuery = matchQuery,
            timeRange = effectiveRange,
            contentType = effectiveType,
            appPackage = effectiveApp,
        ).map { candidates ->
            ranker.rank(candidates, System.currentTimeMillis()).map { it.item }
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
            return captureDao.searchLikePaged(
                patterns = emptyList(),
                timeRange = effectiveRange,
                contentType = effectiveType,
                appPackage = effectiveApp,
                limit = pageSize.toLong(),
                offset = offset.toLong(),
            ).map { SearchPage(it, it.size < pageSize) }
        }

        val matchQuery = matchQueryFor(keywords)
        if (matchQuery.isBlank()) {
            return flowOf(SearchPage(emptyList(), isLastPage = true))
        }

        return captureDao.searchFtsCandidates(
            matchQuery = matchQuery,
            timeRange = effectiveRange,
            contentType = effectiveType,
            appPackage = effectiveApp,
        ).map { candidates ->
            val ranked = ranker.rank(candidates, System.currentTimeMillis())
                .drop(offset)
                .take(pageSize)
                .map { it.item }
            SearchPage(ranked, ranked.size < pageSize)
        }
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
     * Maps a single search keyword to an FTS5 MATCH token. Plain tokens are
     * matched as full words (`hello`). Tokens containing FTS5 operators or
     * operator keywords (AND, OR, NOT, NEAR) are quoted with double quotes —
     * quoting is also a natural way to search literal punctuation and keeps
     * operator words (`or`) from being parsed as operators.
     */
    private fun ftsToken(token: String): String {
        val lower = token.lowercase(Locale.ROOT)
        val isPlainWord = token.all { it.isLetterOrDigit() || it == '_' } && lower !in FTS_OPERATORS
        return if (isPlainWord) {
            token
        } else {
            "\"" + token.replace("\"", "\"\"") + "\""
        }
    }

    private companion object {
        const val PAGE_SIZE: Int = 50
        val FTS_OPERATORS = setOf("and", "or", "not", "near")
    }
}
