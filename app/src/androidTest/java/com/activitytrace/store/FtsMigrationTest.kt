package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device validation for MIGRATION_8_9, which cannot run under Robolectric
 * because the emulated platform SQLite has no FTS5 module.
 *
 * MigrationTestHelper operates on the framework SQLite, and several stock
 * AOSP emulator images compile SQLite without FTS5 (`no such module: fts5`).
 * Such images skip the test; images with FTS5 validate the real migration.
 */
@RunWith(AndroidJUnit4::class)
class FtsMigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ActivityTraceDatabase::class.java,
    )

    @Test
    fun `MIGRATION_8_9_backfills_the_fts_index_and_keeps_it_in_sync`() {
        assumeTrue(
            "platform SQLite is missing the FTS5 module on this emulator",
            frameworkFts5Available(),
        )

        helper.createDatabase(TEST_DB, 8).use { db ->
            db.execSQL(
                "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked, content_hash) " +
                    "VALUES ('hello world', 'com.test', 'screen', 1000, 0, 'hash-1')"
            )
            db.execSQL(
                "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked, content_hash) " +
                    "VALUES ('goodbye cruel world', 'com.test', 'screen', 2000, 0, 'hash-2')"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, ActivityTraceDatabase.MIGRATION_8_9)
            .use { db ->
                val matchCursor = db.query(
                    "SELECT COUNT(*) FROM captured_items_fts WHERE captured_items_fts MATCH 'hello'"
                )
                matchCursor.moveToFirst()
                assertEquals("existing rows must be backfilled into the FTS index", 1, matchCursor.getInt(0))
                matchCursor.close()

                db.execSQL(
                    "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked, content_hash) " +
                        "VALUES ('fresh entry', 'com.test', 'screen', 3000, 0, 'hash-3')"
                )

                val liveCursor = db.query(
                    "SELECT COUNT(*) FROM captured_items_fts WHERE captured_items_fts MATCH 'fresh'"
                )
                liveCursor.moveToFirst()
                assertEquals("triggers must keep the FTS index in sync on insert", 1, liveCursor.getInt(0))
                liveCursor.close()

                db.execSQL("DELETE FROM captured_items WHERE text = 'hello world'")

                val deletedCursor = db.query(
                    "SELECT COUNT(*) FROM captured_items_fts WHERE captured_items_fts MATCH 'hello'"
                )
                deletedCursor.moveToFirst()
                assertEquals("triggers must keep the FTS index in sync on delete", 0, deletedCursor.getInt(0))
                deletedCursor.close()
            }
    }

    private fun frameworkFts5Available(): Boolean {
        val probe = context.getDatabasePath("fts5_probe.db")
        return try {
            SQLiteDatabase.openOrCreateDatabase(probe.absolutePath, null).use { db ->
                db.execSQL("CREATE VIRTUAL TABLE probe USING fts5(x)")
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            probe.delete()
        }
    }

    companion object {
        private const val TEST_DB = "migration-test-8-9.db"
    }
}