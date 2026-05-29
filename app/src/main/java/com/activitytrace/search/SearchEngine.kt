package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SearchEngine(private val captureDao: CaptureDao) {

    fun recentItems(contentType: String? = null): Flow<List<CapturedItem>> {
        return if (contentType != null) captureDao.recentItemsFiltered(contentType)
        else captureDao.recentItems()
    }

    fun search(rawQuery: String, contentType: String? = null): Flow<List<CapturedItem>> {
        val parsed = QueryParser.parse(rawQuery)
        val keywords = parsed.keywords

        if (keywords.isEmpty()) return flowOf(emptyList())

        return if (keywords.any { it.contains("*") }) {
            val patterns = keywords.map { it.replace("*", "%") }
            captureDao.searchLike(patterns, parsed.timeRange, contentType)
        } else {
            val ftsQuery = keywords.joinToString(" AND ") { "$it*" }
            captureDao.search(ftsQuery, parsed.timeRange, contentType)
        }
    }
}
