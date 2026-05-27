package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SearchEngine(private val captureDao: CaptureDao) {

    fun search(rawQuery: String): Flow<List<CapturedItem>> {
        val parsed = QueryParser.parse(rawQuery)
        val ftsQuery = parsed.keywords.joinToString(" AND ") { "\"$it\"" }
        return captureDao.search(ftsQuery)
    }
}
