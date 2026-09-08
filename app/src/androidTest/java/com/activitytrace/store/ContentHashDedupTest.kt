package com.activitytrace.store

import androidx.test.core.app.ApplicationProvider
import com.activitytrace.capture.CaptureIngestor
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ContentHashDedupTest {

    private val dao = ActivityTraceDatabase.getInstance(ApplicationProvider.getApplicationContext()).captureDao()

    @After
    fun tearDown() = runBlocking {
        dao.getAllItems().forEach { dao.delete(it) }
    }

    @Test
    fun `identical captures deduplicate to one row`() = runBlocking {
        val h = ContentHasher.hash("com.dedup", "screen", "hello")
        val firstId = dao.insert(item("hello", "com.dedup", "screen", 1000, h))
        val secondId = dao.insert(item("hello", "com.dedup", "screen", 2000, h))

        assertEquals(-1L, secondId)
        assertEquals(1, dao.getAllItems().size)
        assertEquals(firstId, dao.getAllItems().single().id)
    }

    @Test
    fun `different captures both insert`() = runBlocking {
        dao.insert(item("hello", "com.dedup", "screen", 1000, ContentHasher.hash("com.dedup", "screen", "hello")))
        dao.insert(item("world", "com.dedup", "screen", 2000, ContentHasher.hash("com.dedup", "screen", "world")))

        assertEquals(2, dao.getAllItems().size)
    }

    @Test
    fun `concurrent identical inserts produce one row`() = runBlocking {
        val h = ContentHasher.hash("com.dedup", "screen", "race")
        coroutineScope {
            repeat(16) {
                launch {
                    dao.insert(item("race", "com.dedup", "screen", it.toLong(), h))
                }
            }
        }

        assertEquals(1, dao.getAllItems().size)
    }

    @Test
    fun `ingestor stores identical content once (past cooldown)`() = runBlocking {
        CaptureIngestor.db = ActivityTraceDatabase.getInstance(ApplicationProvider.getApplicationContext())
        CaptureIngestor.ingest(
            text = "notification body",
            appPackage = "com.dedup",
            contentType = "notification",
        )
        Thread.sleep(2100L)
        CaptureIngestor.ingest(
            text = "notification body",
            appPackage = "com.dedup",
            contentType = "notification",
        )

        assertEquals(1, dao.getAllItems().size)
    }

    @Test
    fun `backfill assigns hashes to rows created before the migration column existed`() = runBlocking {
        dao.insert(item("legacy text", "com.dedup", "toast", 500, null))
        dao.insert(item("legacy text", "com.dedup", "screen", 600, null))

        assertEquals(0, dao.getAllItems().count { it.contentHash != null })

        val backfilled = ContentHashBackfillWorker.backfillContentHashes(dao)

        assertEquals(2, backfilled)
        val items = dao.getAllItems()
        assertNotNull(items.first { it.contentType == "toast" }.contentHash)
        assertNotNull(items.first { it.contentType == "screen" }.contentHash)
        assertNull(dao.getNullHashBatch(100).firstOrNull())
    }

    private fun item(
        text: String,
        appPackage: String,
        contentType: String,
        timestamp: Long,
        hash: String?,
    ) = CapturedItem(
        text = text,
        appPackage = appPackage,
        contentType = contentType,
        timestamp = timestamp,
        contentHash = hash,
    )
}