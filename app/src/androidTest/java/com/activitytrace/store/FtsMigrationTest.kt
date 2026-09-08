package com.activitytrace.store

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device validation for MIGRATION_8_9, which cannot run under Robolectric
 * because the emulated platform SQLite has no FTS5 module.
 */
@RunWith(AndroidJUnit4::class)
class FtsMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ActivityTraceDatabase::class.java,
    )

    @Test
    fun `MIGRATION_8_9_backfills_the_fts_index_and_keeps_it_in_sync`() {
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
                    "SELECT COUNT(*) FROM captured_items_fts WHERE captured_items_fts MATCH 'hello*'"
                )
                matchCursor.moveToFirst()
                assertEquals("existing rows must be backfilled into the FTS index", 1, matchCursor.getInt(0))
                matchCursor.close()

                db.execSQL(
                    "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked, content_hash) " +
                        "VALUES ('fresh entry', 'com.test', 'screen', 3000, 0, 'hash-3')"
                )

                val liveCursor = db.query(
                    "SELECT COUNT(*) FROM captured_items_fts WHERE captured_items_fts MATCH 'fresh*'"
                )
                liveCursor.moveToFirst()
                assertEquals("triggers must keep the FTS index in sync on insert", 1, liveCursor.getInt(0))
                liveCursor.close()

                db.execSQL("DELETE FROM captured_items WHERE text = 'hello world'")

                val deletedCursor = db.query(
                    "SELECT COUNT(*) FROM captured_items_fts WHERE captured_items_fts MATCH 'hello*'"
                )
                deletedCursor.moveToFirst()
                assertEquals("triggers must keep the FTS index in sync on delete", 0, deletedCursor.getInt(0))
                deletedCursor.close()
            }
    }

    companion object {
        private const val TEST_DB = "migration-test-8-9.db"
    }
}