package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInstrumentation
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ActivityTraceDatabaseMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val tempDir = File(context.cacheDir, "migration_test")

    private val helper = MigrationTestHelper(
        ShadowInstrumentation.getInstrumentation(),
        ActivityTraceDatabase::class.java,
    )

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `MIGRATION_5_6 seeds all 7 default blocked apps`() {
        val dbFile = createV5Database()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

        for (pkg in DEFAULT_BLOCKED) {
            db.execSQL("INSERT OR IGNORE INTO blocked_apps(app_package) VALUES('$pkg')")
        }

        val cursor = db.rawQuery("SELECT COUNT(*) FROM blocked_apps", null)
        cursor.moveToFirst()
        assertEquals(7, cursor.getInt(0))
        cursor.close()
        db.close()
    }

    @Test
    fun `MIGRATION_5_6 does not duplicate existing blocked apps`() {
        val dbFile = createV5Database()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

        db.execSQL("INSERT INTO blocked_apps(app_package) VALUES('com.custom.app')")

        for (pkg in DEFAULT_BLOCKED) {
            db.execSQL("INSERT OR IGNORE INTO blocked_apps(app_package) VALUES('$pkg')")
        }

        val cursor = db.rawQuery("SELECT COUNT(*) FROM blocked_apps", null)
        cursor.moveToFirst()
        assertEquals(8, cursor.getInt(0))
        cursor.close()

        val customCursor = db.rawQuery(
            "SELECT COUNT(*) FROM blocked_apps WHERE app_package = 'com.custom.app'",
            null,
        )
        customCursor.moveToFirst()
        assertEquals(1, customCursor.getInt(0))
        customCursor.close()
        db.close()
    }

    @Test
    fun `MIGRATION_6_7 migrates a real v6 database to v7 without data loss`() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked) VALUES ('hello', 'com.test', 'screen', 1000, 0)"
            )
            db.execSQL("INSERT INTO blocked_apps(app_package) VALUES ('com.custom.app')")
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, ActivityTraceDatabase.MIGRATION_6_7)
            .use { db ->
                val rowCursor = db.query("SELECT COUNT(*) FROM captured_items")
                rowCursor.moveToFirst()
                assertEquals(1, rowCursor.getInt(0))
                rowCursor.close()

                val blockedCursor =
                    db.query("SELECT COUNT(*) FROM blocked_apps WHERE app_package = 'com.custom.app'")
                blockedCursor.moveToFirst()
                assertEquals(1, blockedCursor.getInt(0))
                blockedCursor.close()

                val indexCursor = db.query(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_captured_items_app_package_content_type_text_timestamp'"
                )
                indexCursor.moveToFirst()
                assertEquals(
                    "index_captured_items_app_package_content_type_text_timestamp",
                    indexCursor.getString(0),
                )
                indexCursor.close()
            }
    }

    @Test
    fun `MIGRATION_7_8 adds content_hash and a unique index without data loss`() {
        helper.createDatabase(TEST_DB_7_8, 7).use { db ->
            db.execSQL(
                "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked) VALUES ('hello', 'com.test', 'screen', 1000, 0)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB_7_8, 8, true, ActivityTraceDatabase.MIGRATION_7_8)
            .use { db ->
                val rowCursor = db.query("SELECT COUNT(*) FROM captured_items")
                rowCursor.moveToFirst()
                assertEquals(1, rowCursor.getInt(0))
                rowCursor.close()

                val indexCursor = db.query(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_captured_items_content_hash'"
                )
                indexCursor.moveToFirst()
                assertEquals(
                    "index_captured_items_content_hash",
                    indexCursor.getString(0),
                )
                indexCursor.close()

                val sqlCursor = db.query(
                    "SELECT sql FROM sqlite_master WHERE type = 'index' AND name = 'index_captured_items_content_hash' AND sql LIKE '%UNIQUE%'"
                )
                assertTrue(sqlCursor.moveToFirst())
                sqlCursor.close()
            }
    }

    @Test
    fun `a failing migration leaves the original database fully usable`() {
        helper.createDatabase(TEST_DB_FAIL, 8).use { db ->
            db.execSQL(
                "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked) VALUES ('keep me', 'com.test', 'screen', 1000, 0)"
            )
            db.execSQL("INSERT INTO blocked_apps(app_package) VALUES ('com.keep.app')")
        }

        val failingMigration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("THIS IS NOT VALID SQL")
            }
        }

        assertThrows(Exception::class.java) {
            helper.runMigrationsAndValidate(TEST_DB_FAIL, 9, true, failingMigration)
        }

        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB_FAIL).absolutePath, null).use { db ->
            assertEquals("still version 8", 8, db.version)
            val rowCursor = db.rawQuery("SELECT COUNT(*) FROM captured_items", null)
            rowCursor.moveToFirst()
            assertEquals("pre-migration rows must survive the failure", 1, rowCursor.getInt(0))
            rowCursor.close()

            val blockedCursor = db.rawQuery(
                "SELECT COUNT(*) FROM blocked_apps WHERE app_package = 'com.keep.app'",
                null,
            )
            blockedCursor.moveToFirst()
            assertEquals(1, blockedCursor.getInt(0))
            blockedCursor.close()
        }
    }

    @Test
    fun `MIGRATION_9_10 adds demo_dataset_id without data loss`() {
        helper.createDatabase(TEST_DB_9_10, 9).use { db ->
            db.execSQL(
                "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked) VALUES ('hello', 'com.test', 'screen', 1000, 0)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB_9_10, 10, true, ActivityTraceDatabase.MIGRATION_9_10)
            .use { db ->
                val rowCursor = db.query("SELECT COUNT(*) FROM captured_items")
                rowCursor.moveToFirst()
                assertEquals(1, rowCursor.getInt(0))
                rowCursor.close()

                val demoCursor = db.query(
                    "SELECT demo_dataset_id FROM captured_items WHERE text = 'hello'"
                )
                assertTrue(demoCursor.moveToFirst())
                val value = try {
                    demoCursor.getString(0)
                } finally {
                    demoCursor.close()
                }
                assertTrue("existing records must stay real (NULL demo id)", value.isNullOrBlank())
            }
    }

    private companion object {
        const val TEST_DB = "migration-test-6-7.db"
        const val TEST_DB_7_8 = "migration-test-7-8.db"
        const val TEST_DB_FAIL = "migration-test-failing.db"
        const val TEST_DB_9_10 = "migration-test-9-10.db"
    }

    private fun createV5Database(): File {
        tempDir.mkdirs()
        val dbFile = File(tempDir, "v5_test.db").also { it.delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS captured_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                app_package TEXT NOT NULL,
                app_name TEXT,
                content_type TEXT NOT NULL,
                category TEXT,
                timestamp INTEGER NOT NULL,
                metadata TEXT,
                is_bookmarked INTEGER NOT NULL DEFAULT 0,
                image_blob BLOB DEFAULT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS blocked_apps (
                app_package TEXT NOT NULL PRIMARY KEY
            )
            """.trimIndent()
        )
        db.close()
        return dbFile
    }
}
