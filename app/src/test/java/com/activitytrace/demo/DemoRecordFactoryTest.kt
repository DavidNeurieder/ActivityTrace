package com.activitytrace.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DemoRecordFactoryTest {

    private val referenceTime = Instant.parse("2026-03-10T18:00:00Z")

    private fun showcase(): List<DemoRecord> =
        DemoRecordFactory.recordsFor(DemoDataScenario.SHOWCASE, DemoDataConfig(referenceTime = referenceTime))

    @Test
    fun `showcase generation is deterministic`() {
        val first = showcase()
        val second = showcase()

        assertEquals(first, second)
        assertEquals(first.map { it.offset }, second.map { it.offset })
    }

    @Test
    fun `showcase produces the expected record count`() {
        assertEquals(DemoRecordFactory.SHOWCASE_RECORD_COUNT, showcase().size)
        assertTrue("showcase should be in the 100-160 range", showcase().size in 100..160)
    }

    @Test
    fun `every showcase record lies within the 90 day retention window`() {
        for (record in showcase()) {
            val timestamp = referenceTime.minus(record.offset)
            assertTrue("timestamp must be <= referenceTime", timestamp <= referenceTime)
            assertTrue(
                "record too old: ${record.offset}",
                record.offset <= Duration.ofDays(90),
            )
        }
    }

    @Test
    fun `showcase spans recent and old buckets`() {
        val offsets = showcase().map { it.offset }
        assertTrue("missing today bucket", offsets.any { it < Duration.ofHours(12) })
        assertTrue("missing yesterday bucket", offsets.any { it >= Duration.ofDays(1) && it < Duration.ofDays(2) })
        assertTrue("missing 61-89 day bucket", offsets.any { it >= Duration.ofDays(61) })
        assertTrue("missing 31-60 day bucket", offsets.any { it >= Duration.ofDays(31) && it < Duration.ofDays(60) })
    }

    @Test
    fun `every showcase record is content-addressed uniquely`() {
        val items = showcase().map { it.toCapturedItem(referenceTime, DemoDataScenario.SHOWCASE_DATASET_ID) }
        val hashes = items.map { it.contentHash }
        assertEquals("content_hash must be unique or the DB unique index drops rows", hashes.size, hashes.toSet().size)
    }

    @Test
    fun `all records carry the demo dataset id`() {
        val items = showcase().map { it.toCapturedItem(referenceTime, DemoDataScenario.SHOWCASE_DATASET_ID) }
        assertTrue(items.all { it.demoDatasetId == DemoDataScenario.SHOWCASE_DATASET_ID })
        assertTrue(items.none { it.demoDatasetId == null })
    }

    @Test
    fun `showcase contains the app-name weighting triplet`() {
        val invoiceRecords = showcase().filter { it.text == "Your invoice is ready" }
        assertEquals(setOf("gmail", "signal", "whatsapp"), invoiceRecords.map { it.appPackage }.toSet())
    }

    @Test
    fun `showcase contains the engineered bm25 relevance cluster`() {
        val texts = showcase().map { it.text }
        assertTrue(texts.any { it == "Project Aurora design review" })
        assertTrue(texts.any { it == "Project Aurora Project Aurora Project Aurora design review" })
        assertTrue(texts.any { it.contains("Today's meeting covered Project Aurora") })
        assertTrue(texts.any { it == "Tomorrow's lunch reservation is confirmed" })
    }

    @Test
    fun `showcase contains a long document`() {
        val longText = showcase().map { it.text }.maxByOrNull { it.split(" ").size }!!
        assertTrue(
            "long document expected, got ${longText.split(" ").size} words",
            longText.split(" ").size >= 500,
        )
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
}