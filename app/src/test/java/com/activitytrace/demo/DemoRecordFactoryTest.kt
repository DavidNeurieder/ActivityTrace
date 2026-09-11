package com.activitytrace.demo

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DemoRecordFactoryTest {

    private val referenceTime = Instant.parse("2026-03-10T18:00:00Z")

    private fun showcase(): List<DemoEvent> {
        val generator = DemoDataGenerator(
            captureDao = mockk(relaxed = true),
            database = mockk(relaxed = true),
        )
        return generator.showcaseDataset().events
    }

    @Test
    fun `showcase generation is deterministic`() {
        val first = showcase()
        val second = showcase()

        assertEquals(first, second)
        assertEquals(first.map { it.timestamp }, second.map { it.timestamp })
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `showcase produces the expected record count`() {
        val size = showcase().size
        assertTrue("showcase should be in the 120-150 range, got $size", size in 120..150)
    }

    @Test
    fun `every showcase event lies within the three day window`() {
        for (event in showcase()) {
            assertTrue(
                "event at ${event.timestamp} before DemoClock.start",
                !event.timestamp.isBefore(DemoClock.start),
            )
            assertTrue(
                "event at ${event.timestamp} at or after DemoClock.end",
                event.timestamp.isBefore(DemoClock.endExclusive),
            )
        }
    }

    @Test
    fun `every showcase event is content-addressed uniquely`() {
        val items = showcase().map { it.toCapturedItem(DemoDataScenario.SHOWCASE_DATASET_ID) }
        val hashes = items.map { it.contentHash }
        assertEquals("content_hash must be unique or the DB unique index drops rows", hashes.size, hashes.toSet().size)
    }

    @Test
    fun `all records carry the demo dataset id`() {
        val items = showcase().map { it.toCapturedItem(DemoDataScenario.SHOWCASE_DATASET_ID) }
        assertTrue(items.all { it.demoDatasetId == DemoDataScenario.SHOWCASE_DATASET_ID })
        assertTrue(items.none { it.demoDatasetId == null })
    }

    @Test
    fun `showcase is validated clean`() {
        val generator = DemoDataGenerator(
            captureDao = mockk(relaxed = true),
            database = mockk(relaxed = true),
        )
        val result = DemoDatasetValidator.validate(generator.showcaseDataset())
        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun `showcase covers all three days`() {
        val days = showcase().map { (it.timestamp.toEpochMilli() - DemoClock.start.toEpochMilli()) / 86_400_000L }.toSet()
        assertEquals(setOf(0L, 1L, 2L), days)
    }

    @Test
    fun `showcase contains a long document`() {
        val longest = showcase().map { it.text.split(Regex("\\s+")).size }.max()
        assertTrue("long document expected, got $longest words", longest >= 400)
    }

    @Test
    fun `showcase contains the four story fingerprints`() {
        val text = showcase().joinToString(" ") { it.text }
        assertTrue(text.contains("Project Aurora"))
        assertTrue(text.contains("Vienna"))
        assertTrue(text.contains("which neighbor??"))
        assertTrue(text.contains("€89.00"))
    }

    @Test
    fun `benchmark generation is deterministic`() {
        val first = DemoRecordFactory.benchmarkRecords(count = 1_000, seed = 77)
        val second = DemoRecordFactory.benchmarkRecords(count = 1_000, seed = 77)

        assertEquals(first, second)
    }

    @Test
    fun `benchmark generation respects the requested size`() {
        assertEquals(1_000, DemoRecordFactory.benchmarkRecords(count = 1_000).size)
        assertEquals(5_000, DemoRecordFactory.benchmarkRecords(count = 5_000).size)
    }

    @Test
    fun `benchmark records are within range and unique on content`() {
        val records = DemoRecordFactory.benchmarkRecords(count = 1_000)
        for (record in records) {
            assertTrue(record.offset <= Duration.ofDays(90))
        }
        val items = records.map { it.toCapturedItem(referenceTime, DemoDataScenario.BENCHMARK_DATASET_ID) }
        assertEquals(items.size, items.map { it.contentHash }.toSet().size)
    }

    @Test
    fun `benchmark includes the engineered candidate-pool cluster`() {
        val records = DemoRecordFactory.benchmarkRecords(count = 1_000)
        val strong = records.count { it.text.contains("zenith sonic blueprint repeated twice") }
        val weakFresh = records.count { it.text.contains("zenith sonic blueprint morning note") }
        assertTrue("expected > 200 strong candidates, got $strong", strong >= 200)
        assertEquals("exactly one weak fresh match", 1, weakFresh)
    }

    @Test
    fun `recordsFor rejects the curated showcase scenario`() {
        val exception = runCatching {
            DemoRecordFactory.recordsFor(DemoDataScenario.SHOWCASE, DemoDataConfig(referenceTime = referenceTime))
        }.exceptionOrNull()
        assertTrue("expected an exception for SHOWCASE, got $exception", exception is IllegalArgumentException)
    }

    @Test
    fun `recordsFor produces the benchmark corpus`() {
        val records = DemoRecordFactory.recordsFor(DemoDataScenario.SEARCH_BENCHMARK, DemoDataConfig(referenceTime = referenceTime))
        assertEquals(1_000, records.size)
    }
}