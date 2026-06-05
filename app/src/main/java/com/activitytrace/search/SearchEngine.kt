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

        val effectiveType = contentType ?: parsed.typeFilter
        val appPackage = parsed.appFilter

        val patterns = keywords.map { "%${it.replace("*", "%")}%" }
        return captureDao.searchLike(patterns, parsed.timeRange, effectiveType, appPackage)
    }
}
