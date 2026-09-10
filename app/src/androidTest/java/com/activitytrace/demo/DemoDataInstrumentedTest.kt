package com.activitytrace.demo

import androidx.test.core.app.ApplicationProvider
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * End-to-end demo lifecycle against the real (SQLCipher) database: the
 * generated records must flow through the exact production FTS5 search path,
 * and demo deletion must never reach real captures.
 */
class DemoDataInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val database = ActivityTraceDatabase.getInstance(context)
    private val dao: CaptureDao = database.captureDao()

    private val referenceTime: Instant = Instant.parse("2026-03-10T18:00:00Z")

    @After
    fun tearDown() = runBlocking {
        dao.getAllItems().forEach { dao.delete(it) }
    }

    @Test
    fun generate_showcase_then_search_finds_demo_records() = runBlocking {
        val generator = DemoDataGenerator(dao, database, DemoDataConfig(referenceTime = referenceTime))

        val inserted = generator.generate(DemoDataScenario.SHOWCASE)

        assertEquals(DemoRecordFactory.SHOWCASE_RECORD_COUNT, inserted)
        assertEquals(inserted, dao.countByDemoDatasetId(DemoDataScenario.SHOWCASE_DATASET_ID))

        val engine = SearchEngine(dao)
        val viennaResults = engine.search("Vienna").first()
        assertTrue("search must find Vienna records", viennaResults.isNotEmpty())
        assertTrue(
            "Vienna cluster should contain the train/ÖBB/platform fixture",
            viennaResults.any { it.text.contains("platform 7") || it.text.contains("ÖBB") },
        )
        assertTrue(
            "every result must be marked as demo",
            viennaResults.all { it.demoDatasetId == DemoDataScenario.SHOWCASE_DATASET_ID },
        )

        val invoiceResults = engine.search("invoice").first()
        assertTrue(invoiceResults.any { it.text.contains("2026-041") })

        val gmailOnly = engine.search("in:gmail invoice").first()
        assertTrue(gmailOnly.isNotEmpty())
        assertTrue("in:gmail filter must return only Gmail", gmailOnly.all { it.appPackage == "gmail" })
    }

    @Test
    fun regenerate_does_not_double_the_dataset() = runBlocking {
        val generator = DemoDataGenerator(dao, database, DemoDataConfig(referenceTime = referenceTime))

        val first = generator.generate(DemoDataScenario.SHOWCASE)
        val second = generator.generate(DemoDataScenario.SHOWCASE)

        assertEquals(first, second)
        assertEquals(first, dao.countByDemoDatasetId(DemoDataScenario.SHOWCASE_DATASET_ID))
        assertEquals(0, dao.countByDemoDatasetId(DemoDataScenario.BENCHMARK_DATASET_ID))
    }

    @Test
    fun clearing_showcase_keeps_real_records() = runBlocking {
        val real = CapturedItem(
            text = "the real private conversation about the mortgage",
            appPackage = "com.example.real",
            contentType = "screen",
            timestamp = 1_700_000_000_000L,
            contentHash = com.activitytrace.store.ContentHasher.hash("com.example.real", "screen", "x"),
        )
        dao.insert(real)
        val generator = DemoDataGenerator(dao, database, DemoDataConfig(referenceTime = referenceTime))
        generator.generate(DemoDataScenario.SHOWCASE)

        assertEquals(1 + DemoRecordFactory.SHOWCASE_RECORD_COUNT, dao.getAllItems().size)
        val removed = generator.clear(DemoDataScenario.SHOWCASE)

        assertEquals(DemoRecordFactory.SHOWCASE_RECORD_COUNT, removed)
        assertEquals(1, dao.getAllItems().size)
        assertTrue(
            "real record must survive and stay real",
            dao.getAllItems().all { it.demoDatasetId == null && it.text.contains("mortgage") },
        )
    }

    @Test
    fun clear_showcase_does_not_touch_benchmark() = runBlocking {
        val generator = DemoDataGenerator(dao, database, DemoDataConfig(referenceTime = referenceTime))
        generator.generate(DemoDataScenario.SHOWCASE)
        generator.generate(DemoDataScenario.SEARCH_BENCHMARK)

        val removed = generator.clear(DemoDataScenario.SHOWCASE)

        assertEquals(DemoRecordFactory.SHOWCASE_RECORD_COUNT, removed)
        assertEquals(0, dao.countByDemoDatasetId(DemoDataScenario.SHOWCASE_DATASET_ID))
        assertEquals(1_000, dao.countByDemoDatasetId(DemoDataScenario.BENCHMARK_DATASET_ID))
    }

    @Test
    fun benchmark_search_surfaces_recent_weak_match_beyond_bm25_cutoff() = runBlocking {
        val generator = DemoDataGenerator(dao, database, DemoDataConfig(referenceTime = referenceTime))
        generator.generate(DemoDataScenario.SEARCH_BENCHMARK)

        assertEquals(1_000, dao.countByDemoDatasetId(DemoDataScenario.BENCHMARK_DATASET_ID))

        val engine = SearchEngine(dao)
        val results = engine.search("zenith").first()

        assertTrue(
            "200 BM25 candidates must not push today's weak match out",
            results.any { it.text.contains("zenith sonic blueprint morning note") },
        )
        assertTrue(results.size >= 200)
    }
}