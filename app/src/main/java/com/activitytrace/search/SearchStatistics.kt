package com.activitytrace.search

import com.activitytrace.store.CaptureDao
import java.util.Calendar

/**
 * Aggregate view of a search, computed inside SQLite over **every** row that
 * matches the [SearchCriteria] — never over the current result page, and never
 * capped by a page size.
 */
data class SearchStatistics(
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val yesterdayCount: Int = 0,
    val prevWeekCount: Int = 0,
    val firstTimestamp: Long? = null,
    val lastTimestamp: Long? = null,
    val topApps: List<CaptureDao.AppCount> = emptyList(),
    val dailyCounts: List<CaptureDao.DateCount> = emptyList(),
    val contentTypeBreakdown: List<CaptureDao.ContentTypeCount> = emptyList(),
    val hourlyCounts: List<CaptureDao.HourCount> = emptyList(),
    val dayOfWeekCounts: List<CaptureDao.DayOfWeekCount> = emptyList(),
) {
    companion object {
        val EMPTY = SearchStatistics()
    }
}

/**
 * Local-midnight anchors the summary buckets are counted against. Passed to
 * SQLite so "today" means the same thing in the UI and in the database.
 */
data class StatisticsWindow(
    val todayStart: Long,
    val yesterdayStart: Long,
    val weekStart: Long,
    val prevWeekStart: Long,
) {
    companion object {
        private const val DAY_MILLIS = 86_400_000L

        fun now(): StatisticsWindow {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis

            cal.add(Calendar.DAY_OF_YEAR, -6)
            val weekStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val prevWeekStart = cal.timeInMillis

            return StatisticsWindow(
                todayStart = todayStart,
                yesterdayStart = todayStart - DAY_MILLIS,
                weekStart = weekStart,
                prevWeekStart = prevWeekStart,
            )
        }
    }
}

/** Number of daily buckets to show: the explicit range length, or 30 days. */
fun SearchCriteria.dailyBucketCount(): Int {
    val range = dateRange ?: return DEFAULT_DAILY_BUCKETS
    val days = ((range.second - range.first) / 86_400_000L).toInt()
    return days.coerceAtLeast(1)
}

const val DEFAULT_DAILY_BUCKETS: Int = 30
