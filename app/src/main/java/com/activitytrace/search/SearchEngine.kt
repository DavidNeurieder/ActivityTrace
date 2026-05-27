package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SearchEngine(private val captureDao: CaptureDao) {

    fun recentItems(): Flow<List<CapturedItem>> = captureDao.recentItems()

    fun search(rawQuery: String): Flow<List<CapturedItem>> {
        val parsed = QueryParser.parse(rawQuery)
        val keywords = parsed.keywords

        if (keywords.isEmpty()) return flowOf(emptyList())

        return if (keywords.any { it.contains("*") }) {
            val patterns = keywords.map { it.replace("*", "%") }
            captureDao.searchLike(patterns, parsed.timeRange)
        } else {
            val ftsQuery = keywords.joinToString(" AND ") { "$it*" }
            captureDao.search(ftsQuery, parsed.timeRange)
        }
    }
}
