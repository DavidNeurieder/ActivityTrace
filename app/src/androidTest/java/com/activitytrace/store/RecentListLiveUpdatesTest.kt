package com.activitytrace.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Regression guard for the "activity events only appear in the main window
 * after an app restart" bug.
 *
 * The main window reads its recent list through [CaptureDao.recentPagedQuery],
 * a plain bound @Query flow so Room's invalidation tracker can push updates.
 * A kept subscription must therefore re-emit the moment new rows are inserted —
 * without re-subscribing and without an app restart. This is the empirical
 * baseline for keeping the main window on the @Query path instead of the
 * @RawQuery path that went stale.
 */
class RecentListLiveUpdatesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val passphrase = "recent-live-passphrase".toByteArray()
    private lateinit var database: ActivityTraceDatabase
    private lateinit var dao: CaptureDao
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        val unique = System.nanoTime()
        dbFile = context.getDatabasePath("recent_live_$unique.db")
        dbFile.delete()
        database = ActivityTraceDatabase.buildDatabase(context, passphrase, dbFile.name)
        dao = database.captureDao()
    }

    @After
    fun tearDown() {
        runBlocking {
            database.close()
            dbFile.delete()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
        }
    }

    @Test
    fun recent_paged_flow_re_emits_on_insert_without_restart() = runBlocking {
        val channel = Channel<List<CapturedItem>>(Channel.BUFFERED)
        val collector = launch(Dispatchers.Default) {
            dao.recentPagedQuery(null, null, null, null, 50L, 0L)
                .collect { channel.trySend(it) }
        }

        try {
            // Let the cold flow deliver its initial snapshot before the write, so the
            // emission we wait for below can only be driven by Room invalidation.
            withTimeout(10_000) { channel.receive() }

            dao.insert(
                CapturedItem(
                    text = "live event inserted after subscription",
                    appPackage = "com.example.live",
                    contentType = "notification",
                    timestamp = System.currentTimeMillis(),
                    contentHash = ContentHasher.hash("com.example.live", "notification", "live"),
                ),
            )

            val reEmitted = withTimeout(15_000) {
                var last: List<CapturedItem> = emptyList()
                while (true) {
                    last = channel.receive()
                    if (last.any { it.text == "live event inserted after subscription" }) break
                }
                last
            }

            assertTrue(reEmitted.any { it.text == "live event inserted after subscription" })
        } finally {
            collector.cancel()
            channel.close()
        }
    }

    @Test
    fun recent_paged_query_orders_by_recency_and_applies_filters() = runBlocking {
        dao.insert(item("first", "com.example.a", "notification", 1000L))
        dao.insert(item("second", "com.example.b", "screen", 2000L))
        dao.insert(item("third", "com.example.a", "page", 3000L))

        val ordered = dao.recentPagedQuery(null, null, null, null, 50L, 0L).first()
        assertEquals(listOf("third", "second", "first"), ordered.map { it.text })

        val page = dao.recentPagedQuery(null, null, null, null, 2L, 1L).first()
        assertEquals(listOf("second", "first"), page.map { it.text })

        val notices = dao.recentPagedQuery("notification", null, null, null, 50L, 0L).first()
        assertEquals(listOf("first"), notices.map { it.text })

        val appA = dao.recentPagedQuery(null, "com.example.a", null, null, 50L, 0L).first()
        assertEquals(listOf("third", "first"), appA.map { it.text })

        val range = dao.recentPagedQuery(null, null, 1500L, 2500L, 50L, 0L).first()
        assertEquals(listOf("second"), range.map { it.text })
    }

    private suspend fun item(
        text: String,
        appPackage: String,
        contentType: String,
        timestamp: Long,
    ) = CapturedItem(
        text = text,
        appPackage = appPackage,
        contentType = contentType,
        timestamp = timestamp,
        contentHash = ContentHasher.hash(appPackage, contentType, text),
    )
}