package com.activitytrace.store


import com.activitytrace.store.ContentHasher.hash
import com.activitytrace.store.CaptureDao.HashBatchRow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentHashBackfillWorkerTest {

    @Test
    fun `old records receive hashes`() = runTest {
        val dao = mockk<CaptureDao>()
        coEvery { dao.getNullHashBatch(any()) } returnsMany listOf(
            listOf(HashBatchRow(1, "old text", "com.a", "screen")),
            emptyList(),
        )
        coEvery { dao.setContentHash(any(), any()) } returns 1

        val backfilled = ContentHashBackfillWorker.backfillContentHashes(dao)

        assertEquals(1, backfilled)
        coVerify { dao.setContentHash(1, hash("com.a", "screen", "old text")) }
    }

    @Test
    fun `processes all batches until empty`() = runTest {
        val dao = mockk<CaptureDao>()
        val rows = (1..450).map { HashBatchRow(it.toLong(), "text $it", "com.a", "screen") }
        coEvery { dao.getNullHashBatch(any()) } returnsMany listOf(rows.take(200), rows.drop(200).take(200), rows.drop(400), emptyList())
        coEvery { dao.setContentHash(any(), any()) } returns 1

        val backfilled = ContentHashBackfillWorker.backfillContentHashes(dao)

        assertEquals(450, backfilled)
        coVerify(exactly = 450) { dao.setContentHash(any(), any()) }
    }

    @Test
    fun `stops early when a batch makes no progress`() = runTest {
        val dao = mockk<CaptureDao>()
        val unresolved = listOf(HashBatchRow(1, "old text", "com.a", "screen"))
        coEvery { dao.getNullHashBatch(any()) } returnsMany listOf(unresolved, unresolved, unresolved)
        coEvery { dao.setContentHash(any(), any()) } returns 0

        val backfilled = ContentHashBackfillWorker.backfillContentHashes(dao)

        assertEquals(0, backfilled)
        coVerify(exactly = 1) { dao.setContentHash(1, any()) }
        coVerify(exactly = 1) { dao.getNullHashBatch(any()) }
    }

    @Test
    fun `skips rows that cannot be updated without aborting the run`() = runTest {
        val dao = mockk<CaptureDao>()
        val conflicting = listOf(
            HashBatchRow(1, "dup content", "com.a", "screen"),
            HashBatchRow(2, "other content", "com.a", "screen"),
        )
        coEvery { dao.getNullHashBatch(any()) } returnsMany listOf(conflicting, emptyList())
        coEvery { dao.setContentHash(1, any()) } throws
            android.database.sqlite.SQLiteConstraintException("UNIQUE constraint failed")
        coEvery { dao.setContentHash(2, any()) } returns 1

        val backfilled = ContentHashBackfillWorker.backfillContentHashes(dao)

        assertEquals(1, backfilled)
        coVerify(exactly = 1) { dao.setContentHash(1, any()) }
        coVerify { dao.setContentHash(2, hash("com.a", "screen", "other content")) }
    }

    @Test
    fun `hash used is normalized and deterministic`() = runTest {
        val dao = mockk<CaptureDao>()
        coEvery { dao.getNullHashBatch(any()) } returnsMany listOf(
            listOf(HashBatchRow(1, "  OLD TEXT\nwith   space  ", "com.a", "screen")),
            emptyList(),
        )
        coEvery { dao.setContentHash(any(), any()) } returns 1

        ContentHashBackfillWorker.backfillContentHashes(dao)

        val hashSlot = slot<String>()
        coVerify { dao.setContentHash(1, capture(hashSlot)) }
        assertEquals(hash("com.a", "screen", "  OLD TEXT\nwith   space  "), hashSlot.captured)
        assertTrue(hashSlot.captured.matches(Regex("[0-9a-f]{64}")))
    }
}