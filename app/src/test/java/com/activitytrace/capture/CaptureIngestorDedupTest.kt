package com.activitytrace.capture

import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.BlockedAppDao
import com.activitytrace.store.CaptureDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CaptureIngestorDedupTest {

    @Test
    fun `ingest skips insert when database rejects the duplicate`() = runTest {
        val dao = mockk<CaptureDao>()
        val database = databaseWith(dao)
        CaptureIngestor.db = database

        coEvery { dao.insert(any()) } returns -1L

        CaptureIngestor.ingest(
            text = "hello",
            appPackage = "com.test",
            contentType = "screen",
        )

        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `ingest inserts when no duplicate exists`() = runTest {
        val dao = mockk<CaptureDao>()
        val database = databaseWith(dao)
        CaptureIngestor.db = database

        coEvery { dao.insert(any()) } returns 1L

        CaptureIngestor.ingest(
            text = "world",
            appPackage = "com.test",
            contentType = "toast",
        )

        coVerify(exactly = 1) {
            dao.insert(match { item ->
                item.text == "world" &&
                    item.appPackage == "com.test" &&
                    item.contentType == "toast" &&
                    item.contentHash != null
            })
        }
    }

    @Test
    fun `ingest computes the content hash from package, type and text`() = runTest {
        val dao = mockk<CaptureDao>()
        val database = databaseWith(dao)
        CaptureIngestor.db = database

        val captured = mutableListOf<com.activitytrace.model.CapturedItem>()
        coEvery { dao.insert(capture(captured)) } returns 1L

        CaptureIngestor.ingest(
            text = "Message",
            appPackage = "com.test",
            contentType = "notification",
        )

        val item = captured.single()
        assertEquals(
            com.activitytrace.store.ContentHasher.hash("com.test", "notification", "Message"),
            item.contentHash,
        )
    }

    private fun databaseWith(dao: CaptureDao): ActivityTraceDatabase {
        val blockedAppDao = mockk<BlockedAppDao>()
        coEvery { blockedAppDao.getAllBlocked() } returns emptyList()
        val database = mockk<ActivityTraceDatabase>()
        every { database.captureDao() } returns dao
        every { database.blockedAppDao() } returns blockedAppDao
        return database
    }
}