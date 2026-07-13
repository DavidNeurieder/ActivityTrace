package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

        val patterns = keywords.map {
            val p = it.replace("*", "%")
            buildString {
                if (!p.startsWith("%")) append("%")
                append(p)
                if (!p.endsWith("%")) append("%")
            }
        }
        return captureDao.searchLike(patterns, effectiveRange, effectiveType, effectiveApp)
    }
}
