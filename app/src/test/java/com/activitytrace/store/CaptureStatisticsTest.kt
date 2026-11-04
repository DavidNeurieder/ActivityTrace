package com.activitytrace.store

import androidx.room.Room
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchCriteria
import com.activitytrace.search.SearchEngine
import com.activitytrace.search.StatisticsWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Statistics must aggregate over every matching row, while the paged result
 * query stays capped. These tests use a real in-memory Room database with the
 * filter-only path (no keyword component) because Robolectric's SQLite build
 * has no FTS5 module; the FTS statistics path is covered by the instrumented
 * tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CaptureStatisticsTest {

    private val dayMillis = 86_400_000L
    private val todayStart: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private lateinit var db: ActivityTraceDatabase
    private lateinit var dao: CaptureDao
    private lateinit var engine: SearchEngine

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ActivityTraceDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.captureDao()
        engine = SearchEngine(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun window() = StatisticsWindow(
        todayStart = todayStart,
        yesterdayStart = todayStart - dayMillis,
        weekStart = todayStart - 6 * dayMillis,
        prevWeekStart = todayStart - 13 * dayMillis,
    )

    private suspend fun capture(
        text: String,
        pkg: String = "com.a",
        type: String = "notification",
        timestamp: Long = todayStart + 9 * 3_600_000L,
    ): Long = dao.insert(
        CapturedItem(
            text = text,
            appPackage = pkg,
            contentType = type,
            timestamp = timestamp,
        ),
    )

    @Test
    fun `statistics aggregate every matching row beyond the first page`() = runTest {
        repeat(120) { i ->
            capture(
                text = "note $i",
                pkg = if (i % 2 == 0) "com.a" else "com.b",
                timestamp = todayStart + 9 * 3_600_000L + i,
            )
        }

        val stats = engine.statistics(SearchCriteria(), window()).first()

        assertEquals(120, stats.totalCount)
        assertEquals(120, stats.todayCount)
        assertEquals(120, stats.weekCount)
        assertEquals(120, stats.topApps.sumOf { it.count })
        assertEquals(120, stats.contentTypeBreakdown.sumOf { it.count })
        assertEquals(120, stats.hourlyCounts.sumOf { it.count })
        assertEquals(120, stats.dayOfWeekCounts.sumOf { it.count })
        assertEquals(60, stats.topApps.first { it.appPackage == "com.a" }.count)
        assertEquals(60, stats.topApps.first { it.appPackage == "com.b" }.count)
        assertEquals(todayStart + 9 * 3_600_000L, stats.firstTimestamp)
        assertEquals(todayStart + 9 * 3_600_000L + 119, stats.lastTimestamp)
    }

    @Test
    fun `paged results stay capped at the page size`() = runTest {
        repeat(120) { i -> capture("note $i", timestamp = todayStart + 1000 + i) }

        val first = engine.results(SearchCriteria(), pageSize = 50, offset = 0).first()
        val second = engine.results(SearchCriteria(), pageSize = 50, offset = 50).first()

        assertEquals(50, first.items.size)
        assertEquals(50, second.items.size)
        assertEquals(0, first.items.intersect(second.items.toSet()).size)
    }

    @Test
    fun `statistics count the full dataset at the page boundaries`() = runTest {
        var inserted = 0
        for (target in listOf(20, 50, 100)) {
            while (inserted < target) {
                capture("note $inserted", timestamp = todayStart + 1000 + inserted)
                inserted++
            }

            val stats = engine.statistics(SearchCriteria(), window()).first()
            val page = engine.results(SearchCriteria(), pageSize = 50, offset = 0).first()

            assertEquals(target, stats.totalCount)
            assertEquals(minOf(target, 50), page.items.size)
        }
    }

    @Test
    fun `statistics follow new matching rows`() = runTest {
        repeat(60) { i -> capture("note $i", timestamp = todayStart + 1000 + i) }
        val flow = engine.statistics(SearchCriteria(), window())

        assertEquals(60, flow.first().totalCount)

        capture("brand new", timestamp = todayStart + 5000)

        assertEquals(61, flow.first().totalCount)
    }

    @Test
    fun `filters narrow the statistics`() = runTest {
        repeat(60) { i -> capture("note $i", pkg = "com.a", type = "notification") }
        repeat(10) { i -> capture("page $i", pkg = "com.b", type = "screen") }

        val byType = engine.statistics(SearchCriteria(contentType = "notification"), window()).first()
        val byApp = engine.statistics(SearchCriteria(appPackage = "com.a"), window()).first()
        val both = engine
            .statistics(SearchCriteria(contentType = "notification", appPackage = "com.b"), window())
            .first()

        assertEquals(60, byType.totalCount)
        assertEquals(60, byApp.totalCount)
        assertEquals(0, both.totalCount)
    }

    @Test
    fun `date range narrows the statistics`() = runTest {
        repeat(30) { i -> capture("recent $i", timestamp = todayStart + 3_600_000L + i) }
        repeat(90) { i -> capture("older $i", timestamp = todayStart - 3 * dayMillis + i) }

        val today = engine
            .statistics(SearchCriteria(dateRange = todayStart to todayStart + dayMillis), window())
            .first()
        val all = engine.statistics(SearchCriteria(), window()).first()

        assertEquals(30, today.totalCount)
        assertEquals(120, all.totalCount)
    }

    @Test
    fun `statistics are empty when nothing matches`() = runTest {
        repeat(60) { i -> capture("note $i", pkg = "com.a", type = "notification") }

        val stats = engine.statistics(SearchCriteria(contentType = "screen"), window()).first()

        assertEquals(0, stats.totalCount)
        assertEquals(0, stats.topApps.size)
        assertEquals(0, stats.dailyCounts.size)
        assertEquals(0, stats.contentTypeBreakdown.size)
    }

    @Test
    fun `statistics update after deletions`() = runTest {
        repeat(60) { i -> capture("note $i", timestamp = todayStart + 1000 + i) }
        assertEquals(60, engine.statistics(SearchCriteria(), window()).first().totalCount)

        dao.getAllItems().take(10).forEach { dao.delete(it) }

        assertEquals(50, engine.statistics(SearchCriteria(), window()).first().totalCount)
    }

    @Test
    fun `duplicate timestamps count as one row each`() = runTest {
        repeat(55) { capture("same", timestamp = todayStart + 1000) }

        val stats = engine.statistics(SearchCriteria(), window()).first()

        assertEquals(55, stats.totalCount)
        assertEquals(1, stats.dailyCounts.size)
        assertEquals(55, stats.dailyCounts.single().count)
    }

    @Test
    fun `bookmarked filter limits the statistics to bookmarks`() = runTest {
        repeat(60) { i -> capture("note $i", timestamp = todayStart + 1000 + i) }
        dao.getAllItems().take(7).forEach { dao.setBookmarked(it.id, true) }

        val bookmarked = engine.statistics(SearchCriteria(bookmarkedOnly = true), window()).first()
        val all = engine.statistics(SearchCriteria(), window()).first()

        assertEquals(7, bookmarked.totalCount)
        assertEquals(60, all.totalCount)
    }

    @Test
    fun `daily buckets are limited to the requested window`() = runTest {
        repeat(40) { day ->
            capture(
                text = "day $day",
                timestamp = todayStart - day * dayMillis + 12 * 3_600_000L,
            )
        }

        val stats = engine.statistics(SearchCriteria(), window(), dailyLimit = 30).first()

        assertEquals(30, stats.dailyCounts.size)
        assertEquals(30, stats.dailyCounts.sumOf { it.count })
        assertEquals(40, stats.totalCount)
    }

    @Test
    fun `today buckets compare against the supplied window`() = runTest {
        capture("today", timestamp = todayStart + 3_600_000L)
        capture("yesterday", timestamp = todayStart - 3_600_000L)
        capture("last week", timestamp = todayStart - 3 * dayMillis)
        capture("previous week", timestamp = todayStart - 8 * dayMillis)

        val stats = engine.statistics(SearchCriteria(), window()).first()

        assertEquals(4, stats.totalCount)
        assertEquals(1, stats.todayCount)
        assertEquals(1, stats.yesterdayCount)
        assertEquals(3, stats.weekCount)
        assertEquals(1, stats.prevWeekCount)
    }
}
