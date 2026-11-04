package com.activitytrace.search

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class SearchPage(
    val items: List<CapturedItem>,
    val isLastPage: Boolean = false,
)

/**
 * Read side of the search component, consumed by [com.activitytrace.ui.SearchViewModel].
 * Extracted so UI tests can substitute a lightweight fake without the MockK
 * JVMTI agent (whose native library is not 16 KB page aligned).
 *
 * The two methods are deliberately independent: [results] is capped by the
 * page size, while [statistics] aggregates over every matching row in SQLite.
 * Both take the *same* [SearchCriteria], so a count can never disagree with the
 * search it describes.
 */
interface SearchEngineApi {
    fun results(
        criteria: SearchCriteria,
        pageSize: Int = SearchEngine.PAGE_SIZE,
        offset: Int = 0,
    ): Flow<SearchPage>

    fun statistics(
        criteria: SearchCriteria,
        window: StatisticsWindow = StatisticsWindow.now(),
        dailyLimit: Int = DEFAULT_DAILY_BUCKETS,
    ): Flow<SearchStatistics>
}

class SearchEngine(
    private val captureDao: CaptureDao,
    private val ranker: SearchRanker = SearchRanker(),
) : SearchEngineApi {

    fun recentItems(
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
    ): Flow<List<CapturedItem>> {
        return recentPaged(contentType, appPackage, dateRange, pageSize = PAGE_SIZE, offset = 0)
            .map { it.items }
    }

    /** Legacy signature kept for existing callers and tests. */
    fun recentPaged(
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
        pageSize: Int = PAGE_SIZE,
        offset: Int = 0,
    ): Flow<SearchPage> {
        val resolved = SearchCriteriaResolver.resolve(
            SearchCriteria(contentType = contentType, appPackage = appPackage, dateRange = dateRange),
        )
        return pagedResults(resolved, pageSize, offset, browseWhenUnfiltered = true)
    }

    /** Legacy signature kept for existing callers and tests. */
    fun search(
        rawQuery: String,
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
    ): Flow<List<CapturedItem>> {
        val resolved = SearchCriteriaResolver.resolve(
            SearchCriteria(
                rawQuery = rawQuery,
                contentType = contentType,
                appPackage = appPackage,
                dateRange = dateRange,
            ),
        )
        return pagedResults(resolved, PAGE_SIZE, offset = 0, browseWhenUnfiltered = false, unlimited = true)
            .map { it.items }
    }

    /** Legacy signature kept for existing callers and tests. */
    fun searchPaged(
        rawQuery: String,
        contentType: String? = null,
        appPackage: String? = null,
        dateRange: Pair<Long, Long>? = null,
        pageSize: Int = PAGE_SIZE,
        offset: Int = 0,
    ): Flow<SearchPage> {
        val resolved = SearchCriteriaResolver.resolve(
            SearchCriteria(
                rawQuery = rawQuery,
                contentType = contentType,
                appPackage = appPackage,
                dateRange = dateRange,
            ),
        )
        return pagedResults(resolved, pageSize, offset, browseWhenUnfiltered = false)
    }

    override fun results(
        criteria: SearchCriteria,
        pageSize: Int,
        offset: Int,
    ): Flow<SearchPage> {
        val resolved = SearchCriteriaResolver.resolve(criteria)
        // A blank query browses the recent list; a query with text keeps the
        // search semantics (nothing to search for means no matches).
        return pagedResults(
            resolved = resolved,
            pageSize = pageSize,
            offset = offset,
            browseWhenUnfiltered = criteria.rawQuery.isBlank(),
        )
    }

    override fun statistics(
        criteria: SearchCriteria,
        window: StatisticsWindow,
        dailyLimit: Int,
    ): Flow<SearchStatistics> {
        val resolved = SearchCriteriaResolver.resolve(criteria)
        if (resolved.matchesNothing) return flowOf(SearchStatistics.EMPTY)
        if (!resolved.hasKeywordComponent && !resolved.hasAnyFilter && criteria.rawQuery.isNotBlank()) {
            return flowOf(SearchStatistics.EMPTY)
        }

        val summary = captureDao.statisticsSummary(resolved, window)
        val appCounts = captureDao.statisticsAppCounts(resolved)
        val dailyCounts = captureDao.statisticsDailyCounts(resolved, dailyLimit)
        val contentTypes = captureDao.statisticsContentTypes(resolved)

        return combine(summary, appCounts, dailyCounts, contentTypes) { s, apps, daily, types ->
            StatisticsParts(s, apps, daily, types)
        }.combine(captureDao.statisticsHourlyCounts(resolved)) { parts, hourly ->
            parts to hourly
        }.combine(captureDao.statisticsDayOfWeekCounts(resolved)) { (parts, hourly), dow ->
            SearchStatistics(
                totalCount = parts.summary.total,
                todayCount = parts.summary.today,
                weekCount = parts.summary.week,
                yesterdayCount = parts.summary.yesterday,
                prevWeekCount = parts.summary.prevWeek,
                firstTimestamp = parts.summary.firstTimestamp,
                lastTimestamp = parts.summary.lastTimestamp,
                topApps = parts.apps,
                dailyCounts = parts.daily,
                contentTypeBreakdown = parts.types,
                hourlyCounts = hourly,
                dayOfWeekCounts = dow,
            )
        }
    }

    private data class StatisticsParts(
        val summary: CaptureDao.StatisticsSummary,
        val apps: List<CaptureDao.AppCount>,
        val daily: List<CaptureDao.DateCount>,
        val types: List<CaptureDao.ContentTypeCount>,
    )

    /**
     * Shared execution path for paged results. [browseWhenUnfiltered] selects
     * what "no keyword, no filter" means: the recent list (browsing) or an
     * empty page (a query that filters nothing). [unlimited] removes the page
     * cap and is only used by the legacy result API.
     */
    private fun pagedResults(
        resolved: ResolvedSearchCriteria,
        pageSize: Int,
        offset: Int,
        browseWhenUnfiltered: Boolean,
        unlimited: Boolean = false,
    ): Flow<SearchPage> {
        if (!resolved.hasKeywordComponent) {
            if (!resolved.hasAnyFilter && !browseWhenUnfiltered) {
                return flowOf(SearchPage(emptyList(), isLastPage = true))
            }
            return captureDao.recentPagedQuery(
                contentType = resolved.contentType,
                appPackage = resolved.appPackage,
                startTime = resolved.timeRange?.first,
                endTime = resolved.timeRange?.second,
                queryLimit = if (unlimited) Long.MAX_VALUE else pageSize.toLong(),
                queryOffset = offset.toLong(),
            ).map { page ->
                SearchPage(page, isLastPage = !unlimited && page.size < pageSize)
            }
        }

        if (resolved.matchQuery.isBlank()) {
            return flowOf(SearchPage(emptyList(), isLastPage = true))
        }

        return rankAndMap(
            resolved.matchQuery,
            resolved.timeRange,
            resolved.contentType,
            resolved.appPackage,
        ) { ranked ->
            val page = if (unlimited) {
                ranked.map { it.item }
            } else {
                ranked.drop(offset).take(pageSize).map { it.item }
            }
            SearchPage(page, isLastPage = !unlimited && page.size < pageSize)
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

    companion object {
        const val PAGE_SIZE: Int = 50
    }
}
