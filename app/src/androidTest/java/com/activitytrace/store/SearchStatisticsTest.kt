package com.activitytrace.store

import androidx.test.core.app.ApplicationProvider
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchCriteria
import com.activitytrace.search.SearchEngine
import com.activitytrace.search.SearchEngine.Companion.PAGE_SIZE
import com.activitytrace.search.StatisticsWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * End-to-end proof that statistics aggregate over *every* FTS match while the
 * result list stays capped at [PAGE_SIZE]. This has to be instrumented: the
 * Robolectric SQLite build has no FTS5 module.
 *
 * Method names use underscores because DEX versions below 040 (minSdk 26)
 * reject spaces in the synthetic lambda class names Kotlin generates.
 */
class SearchStatisticsTest {

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val dao = ActivityTraceDatabase.getInstance(app).captureDao()
    private val engine = SearchEngine(dao)

    @Before
    fun setUp() = runBlocking {
        dao.getAllItems().forEach { dao.delete(it) }
    }

    @After
    fun tearDown() = runBlocking {
        dao.getAllItems().forEach { dao.delete(it) }
    }

    private fun item(text: String, pkg: String, type: String, ts: Long) = CapturedItem(
        text = text,
        appPackage = pkg,
        contentType = type,
        timestamp = ts,
    )

    @Test
    fun statistics_aggregate_beyond_the_result_page() = runBlocking {
        val now = System.currentTimeMillis()
        repeat(150) { i ->
            dao.insert(
                item(
                    text = "needle probe $i",
                    pkg = if (i % 2 == 0) "com.probe.a" else "com.probe.b",
                    type = "notification",
                    ts = now - i * 1000L,
                ),
            )
        }

        val criteria = SearchCriteria(rawQuery = "needle")
        val statistics = engine.statistics(criteria, StatisticsWindow.now()).first()
        val page = engine.results(criteria, pageSize = PAGE_SIZE, offset = 0).first()

        assertEquals(150, statistics.totalCount)
        assertEquals(150, statistics.topApps.sumOf { it.count })
        assertEquals(150, statistics.contentTypeBreakdown.sumOf { it.count })
        assertEquals(75, statistics.topApps.first { it.appPackage == "com.probe.a" }.count)
        assertEquals(PAGE_SIZE, page.items.size)
    }

    @Test
    fun statistics_respect_fts_filters() = runBlocking {
        val now = System.currentTimeMillis()
        repeat(120) { i ->
            dao.insert(item("needle notification $i", "com.probe.a", "notification", now - i * 1000L))
        }
        repeat(30) { i ->
            dao.insert(item("needle screen $i", "com.probe.b", "screen", now - i * 1000L))
        }

        val byType = engine.statistics(
            SearchCriteria(rawQuery = "needle", contentType = "notification"),
            StatisticsWindow.now(),
        ).first()
        val byApp = engine
            .statistics(SearchCriteria(rawQuery = "needle", appPackage = "com.probe.b"), StatisticsWindow.now())
            .first()
        val everything = engine.statistics(SearchCriteria(rawQuery = "needle"), StatisticsWindow.now()).first()

        assertEquals(120, byType.totalCount)
        assertEquals(30, byApp.totalCount)
        assertEquals(150, everything.totalCount)
    }

    @Test
    fun statistics_only_count_the_keyword_matches() = runBlocking {
        val now = System.currentTimeMillis()
        repeat(60) { i -> dao.insert(item("needle $i", "com.probe.a", "notification", now - i * 1000L)) }
        repeat(90) { i -> dao.insert(item("haystack $i", "com.probe.a", "notification", now - i * 1000L)) }

        val statistics = engine.statistics(
            SearchCriteria(rawQuery = "needle"),
            StatisticsWindow.now(),
        ).first()

        assertEquals(60, statistics.totalCount)
    }
}
