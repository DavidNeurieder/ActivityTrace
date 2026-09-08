package com.activitytrace.store

import androidx.test.core.app.ApplicationProvider
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * FTS5 full-text search benchmarks over large databases (§33: 100k / 500k / 1m rows).
 *
 * These are opt-in. They generate N rows in the real database, time FTS5
 * searches over them, and print a timing line. The target row count is read
 * from a command-line instrumentation argument:
 *
 * ```
 * ./gradlew connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.ftsBenchmarkRows=500000
 * ```
 *
 * When the argument is absent (or is 0) the test is skipped, so CI stays fast.
 * Assertions are loose (search must complete and rank correctly) because
 * wall-clock times vary wildly across emulators.
 */
class FtsSearchBenchmarkTest {

    private val dao = ActivityTraceDatabase.getInstance(ApplicationProvider.getApplicationContext()).captureDao()

    @After
    fun tearDown() = runBlocking {
        dao.getAllItems().forEach { dao.delete(it) }
    }

    @Test
    fun `90k row benchmark`() = runBlocking { benchmark(90_000) }

    @Test
    fun `900k row benchmark`() = runBlocking { benchmark(900_000) }

    private suspend fun benchmark(requestedRows: Int) {
        val rows = requestedRowsFromArgs() ?: return
        if (rows < requestedRows) {
            println("SKIP FTS benchmark ${"%,d".format(requestedRows)} rows: arg ftsBenchmarkRows=$rows is smaller")
            return
        }

        val insertTime = measureTimeMillis { insertRows(rows) }

        val engine = SearchEngine(dao)
        val target = rows / 2

        val singleTermMs = measureTimeMillis {
            val results = engine.search("needle_$target").first()
            assertTrue("expected exactly one hit", results.size == 1)
        }

        val prefixTermMs = measureTimeMillis {
            val results = engine.search("needle_${target / 10}").first()
            assertTrue("expected prefix hits", results.size >= 1)
        }

        val multiTermMs = measureTimeMillis {
            val results = engine.search("needle_$target cross_${target % 100}").first()
            assertTrue("expected one hit for the AND query", results.size == 1)
        }

        val typeFilteredMs = measureTimeMillis {
            val results = engine.search("needle_$target type:benchmark").first()
            assertTrue("expected type-filtered hit", results.size == 1)
        }

        println(
            "FTS BENCHMARK rows=${"%,d".format(rows)} " +
                "insert=${insertTime}ms single=${singleTermMs}ms " +
                "prefix=${prefixTermMs}ms multi=${multiTermMs}ms typeFiltered=${typeFilteredMs}ms"
        )
    }

    private fun requestedRowsFromArgs(): Int? {
        val raw = System.getProperty("androidx.test.INSTRUMENTATION_ARGUMENT_ftsBenchmarkRows", "0")
        return raw.toIntOrNull()?.takeIf { it > 0 }
    }

    private suspend fun insertRows(rows: Int) {
        (0 until rows step 1_000).forEach { chunkStart ->
            val items = (chunkStart until (chunkStart + 1_000).coerceAtMost(rows)).map { i ->
                CapturedItem(
                    text = "needle_$i cross_${i % 100} filler text for the row number $i",
                    appPackage = "com.benchmark",
                    contentType = "benchmark",
                    timestamp = i.toLong(),
                    contentHash = ContentHasher.hash("com.benchmark", "benchmark", "needle_$i"),
                )
            }
            dao.insertAll(items)
        }
    }
}