package com.activitytrace.search

import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import com.activitytrace.store.CaptureDao
import io.mockk.every
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks down the SQL shape of the statistics aggregates: they must filter on
 * the same criteria as the paged results and must not carry a page limit.
 * The FTS5 MATCH expression cannot run under Robolectric, so the query text is
 * the only thing a unit test can assert about it.
 */
class SearchStatisticsSqlTest {

    private val dao: CaptureDao = spyk()
    private val engine = SearchEngine(dao)

    private fun window() = StatisticsWindow(
        todayStart = 1_000L,
        yesterdayStart = 500L,
        weekStart = 100L,
        prevWeekStart = 50L,
    )

    private class RecordingProgram : SupportSQLiteProgram {
        val args = mutableMapOf<Int, Any?>()
        override fun bindNull(index: Int) { args[index] = null }
        override fun bindLong(index: Int, value: Long) { args[index] = value }
        override fun bindDouble(index: Int, value: Double) { args[index] = value }
        override fun bindString(index: Int, value: String) { args[index] = value }
        override fun bindBlob(index: Int, value: ByteArray) { args[index] = value }
        override fun clearBindings() { args.clear() }
        override fun close() = Unit
    }

    private fun SupportSQLiteQuery.arguments(): Map<Int, Any?> =
        RecordingProgram().also { bindTo(it) }.args

    private fun stubAggregates(
        appSlot: io.mockk.CapturingSlot<SupportSQLiteQuery>? = null,
        dailySlot: io.mockk.CapturingSlot<SupportSQLiteQuery>? = null,
    ) {
        every { dao.statisticsSummaryRaw(any()) } returns flowOf(
            CaptureDao.StatisticsSummary(0, 0, 0, 0, 0, null, null),
        )
        if (appSlot != null) {
            every { dao.statisticsAppCountsRaw(capture(appSlot)) } returns flowOf(emptyList())
        } else {
            every { dao.statisticsAppCountsRaw(any()) } returns flowOf(emptyList())
        }
        if (dailySlot != null) {
            every { dao.statisticsDailyCountsRaw(capture(dailySlot)) } returns flowOf(emptyList())
        } else {
            every { dao.statisticsDailyCountsRaw(any()) } returns flowOf(emptyList())
        }
        every { dao.statisticsContentTypesRaw(any()) } returns flowOf(emptyList())
        every { dao.statisticsHourlyCountsRaw(any()) } returns flowOf(emptyList())
        every { dao.statisticsDayOfWeekCountsRaw(any()) } returns flowOf(emptyList())
    }

    @Test
    fun `fts aggregates join the index and carry the filters without a limit`() = runTest {
        val appSlot = slot<SupportSQLiteQuery>()
        stubAggregates(appSlot = appSlot)

        engine.statistics(
            SearchCriteria(
                rawQuery = "hello",
                contentType = "notification",
                appPackage = "com.a",
            ),
            window(),
        ).first()

        val query = appSlot.captured
        val sql = query.sql
        assertTrue(sql.contains("JOIN captured_items_fts"))
        assertTrue(sql.contains("captured_items_fts MATCH ?"))
        assertTrue(sql.contains("captured_items.content_type LIKE ?"))
        assertTrue(sql.contains("captured_items.app_package LIKE ?"))
        assertTrue(sql.contains("GROUP BY captured_items.app_package"))
        assertFalse(sql.contains("LIMIT"))
        assertEquals("hello*", query.arguments()[1])
    }

    @Test
    fun `daily aggregates are the only ones with a limit`() = runTest {
        val dailySlot = slot<SupportSQLiteQuery>()
        stubAggregates(dailySlot = dailySlot)

        engine.statistics(SearchCriteria(rawQuery = "hello"), window(), dailyLimit = 7).first()

        val query = dailySlot.captured
        assertTrue(query.sql.contains("LIMIT ?"))
        assertEquals(7L, query.arguments()[query.argCount])
    }

    @Test
    fun `filter only statistics do not join the fts index`() = runTest {
        val appSlot = slot<SupportSQLiteQuery>()
        stubAggregates(appSlot = appSlot)

        engine.statistics(SearchCriteria(contentType = "notification"), window()).first()

        val sql = appSlot.captured.sql
        assertFalse(sql.contains("captured_items_fts"))
        assertTrue(sql.contains("FROM captured_items"))
    }

    @Test
    fun `a query that dissolves into nothing never touches the database`() = runTest {
        stubAggregates()

        val stats = engine.statistics(SearchCriteria(rawQuery = "*"), window()).first()

        assertEquals(SearchStatistics.EMPTY, stats)
        verify(exactly = 0) { dao.statisticsSummaryRaw(any()) }
    }
}
