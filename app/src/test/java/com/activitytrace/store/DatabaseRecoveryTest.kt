package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteOpenHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseRecoveryTest {

    private lateinit var context: Context
    private lateinit var stateStore: RecoveryStateStore
    private lateinit var dbFile: File

    private val helper = mockk<SupportSQLiteOpenHelper>()
    private val database = mockk<ActivityTraceDatabase>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        stateStore = RecoveryStateStore(context)
        stateStore.clear()
        dbFile = File(context.filesDir, "recovery_${System.nanoTime()}.db")
        every { database.openHelper } returns helper
    }

    @After
    fun tearDown() {
        dbFile.delete()
        stateStore.clear()
    }

    // ── non-destruction invariants ────────────────────────────────────

    @Test
    fun `wrong key does not delete database`() {
        dbFile.writeBytes(ByteArray(4096) { 0x01 })
        val before = dbFile.readBytes()

        val result = openerThatThrows({ SQLiteException("file is not a database") }).open()

        assertTrue(result is DatabaseOpenResult.RecoveryRequired)
        assertArrayEquals(before, dbFile.readBytes())
    }

    @Test
    fun `malformed database does not delete database`() {
        dbFile.writeBytes(byteArrayOf(0x00, 0x11, 0x22))
        val before = dbFile.readBytes()

        val result = openerThatThrows({ SQLiteException("database disk image is malformed") }).open()

        assertEquals(DatabaseOpenResult.RecoveryRequired(RecoveryReason.CORRUPTED_DATABASE), result)
        assertArrayEquals(before, dbFile.readBytes())
    }

    @Test
    fun `migration failure does not delete database`() {
        dbFile.writeBytes(ByteArray(2048) { 0x07 })
        val before = dbFile.readBytes()

        val result = openerThatThrows({ SQLiteException("Migration didn't properly handle: captured_items") }).open()

        assertEquals(DatabaseOpenResult.RecoveryRequired(RecoveryReason.MIGRATION_FAILURE), result)
        assertArrayEquals(before, dbFile.readBytes())
    }

    @Test
    fun `database file still exists after open failure`() {
        dbFile.writeBytes(ByteArray(512) { 0x42 })
        openerThatThrows({ SQLiteException("file is not a database") }).open()
        assertTrue("database file must still exist", dbFile.exists())
    }

    @Test
    fun `database key still exists after open failure`() {
        val keyPrefs = context.getSharedPreferences("activity_trace_encryption", Context.MODE_PRIVATE)
        keyPrefs.edit().clear().commit()
        keyPrefs.edit().putString("wrapped_database_key", "SOME_WRAPPED_KEY").commit()

        openerThatThrows({ SQLiteException("file is not a database") }).open()

        assertEquals(
            "wrapped key must remain untouched after a failed open",
            "SOME_WRAPPED_KEY",
            keyPrefs.getString("wrapped_database_key", null),
        )
    }

    // ── recovery state ────────────────────────────────────────────────

    @Test
    fun `recovery state is persisted after an open failure`() {
        openerThatThrows({ SQLiteException("file is not a database") }).open()
        assertEquals(RecoveryReason.INVALID_KEY, stateStore.current())
    }

    @Test
    fun `successful recovery clears recovery state`() {
        stateStore.record(RecoveryReason.CORRUPTED_DATABASE)

        val result = openerThatSucceeds().open()

        assertTrue(result is DatabaseOpenResult.Opened)
        assertNull(stateStore.current())
    }

    @Test
    fun `successful open returns the opened database`() {
        val result = openerThatSucceeds().open()
        assertEquals(database, (result as DatabaseOpenResult.Opened).database)
    }

    @Test
    fun `unclassified failure is reported as failed without wiping recovery state`() {
        stateStore.record(RecoveryReason.CORRUPTED_DATABASE)
        val error = IllegalStateException("unexpected")

        val result = openerThatThrows({ error }).open()

        assertEquals(DatabaseOpenResult.Failed(error), result)
        assertEquals(
            "unclassified failure must not overwrite existing recovery state",
            RecoveryReason.CORRUPTED_DATABASE,
            stateStore.current(),
        )
    }

    // ── classification ────────────────────────────────────────────────

    @Test
    fun `wrong-key classifies as INVALID_KEY`() {
        assertEquals(
            RecoveryReason.INVALID_KEY,
            RecoveryClassifier.classify(SQLiteException("file is encrypted or is not a database")),
        )
    }

    @Test
    fun `corrupted file classifies as CORRUPTED_DATABASE`() {
        assertEquals(
            RecoveryReason.CORRUPTED_DATABASE,
            RecoveryClassifier.classify(SQLiteException("database disk image is malformed")),
        )
    }

    @Test
    fun `version mismatch classifies as MIGRATION_FAILURE`() {
        assertEquals(
            RecoveryReason.MIGRATION_FAILURE,
            RecoveryClassifier.classify(
                IllegalStateException("Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the version number."),
            ),
        )
    }

    private fun openerThatThrows(error: () -> Throwable): DatabaseOpener {
        every { helper.writableDatabase } throws error()
        return DatabaseOpener(database, stateStore)
    }

    private fun openerThatSucceeds(): DatabaseOpener {
        every { helper.writableDatabase } returns mockk()
        return DatabaseOpener(database, stateStore)
    }
}