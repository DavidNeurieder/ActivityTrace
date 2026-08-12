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
    fun `ingest skips insert when a recent duplicate exists`() = runTest {
        val dao = mockk<CaptureDao>()
        val database = databaseWith(dao)
        CaptureIngestor.db = database

        coEvery { dao.countRecentDuplicate("com.test", "screen", "hello", any()) } returns 1
        coEvery { dao.insert(any()) } returns Unit

        CaptureIngestor.ingest(
            text = "hello",
            appPackage = "com.test",
            contentType = "screen",
        )

        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `ingest inserts when no recent duplicate exists`() = runTest {
        val dao = mockk<CaptureDao>()
        val database = databaseWith(dao)
        CaptureIngestor.db = database

        coEvery { dao.countRecentDuplicate("com.test", "toast", "world", any()) } returns 0
        coEvery { dao.insert(any()) } returns Unit

        CaptureIngestor.ingest(
            text = "world",
            appPackage = "com.test",
            contentType = "toast",
        )

        coVerify(exactly = 1) {
            dao.insert(match { item ->
                item.text == "world" &&
                    item.appPackage == "com.test" &&
                    item.contentType == "toast"
            })
        }
    }

    @Test
    fun `dedupWindowFor returns expected windows`() {
        assertEquals(60_000L, CaptureIngestor.dedupWindowFor("screen"))
        assertEquals(5_000L, CaptureIngestor.dedupWindowFor("toast"))
        assertEquals(5_000L, CaptureIngestor.dedupWindowFor("notification"))
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
