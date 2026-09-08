package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

class SearchEngine(private val captureDao: CaptureDao) {

    fun recentItems(
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
    ): Flow<List<CapturedItem>> {
        return captureDao.searchLike(
            patterns = emptyList(),
            timeRange = dateRange,
            contentType = contentType,
            appPackage = appPackage,
        )
    }

    fun search(
        rawQuery: String,
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
    ): Flow<List<CapturedItem>> {
        val parsed = QueryParser.parse(rawQuery)
        val keywords = parsed.keywords

        val effectiveType = contentType ?: parsed.typeFilter
        val effectiveApp = appPackage ?: parsed.appFilter
        val effectiveRange = dateRange ?: parsed.timeRange

        if (keywords.isEmpty() && effectiveRange == null && effectiveType == null && effectiveApp == null) {
            return flowOf(emptyList())
        }

        if (keywords.isEmpty()) {
            return captureDao.searchLike(
                patterns = emptyList(),
                timeRange = effectiveRange,
                contentType = effectiveType,
                appPackage = effectiveApp,
            )
        }

        val matchQuery = keywords.asSequence()
            .map { it.replace("*", "") }
            .filter { it.isNotBlank() }
            .map { ftsToken(it) }
            .joinToString(" ")

        if (matchQuery.isBlank()) {
            return flowOf(emptyList())
        }

        return captureDao.searchFts(
            matchQuery = matchQuery,
            timeRange = effectiveRange,
            contentType = effectiveType,
            appPackage = effectiveApp,
        )
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
        val FTS_OPERATORS = setOf("and", "or", "not", "near")
    }
}
