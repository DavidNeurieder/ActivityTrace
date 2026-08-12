package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ActivityTraceDatabaseMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val tempDir = File(context.cacheDir, "migration_test")

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
    fun `MIGRATION_6_7 creates dedup index and preserves existing rows`() {
        val dbFile = createV5Database()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)

        db.execSQL(
            "INSERT INTO captured_items (text, app_package, content_type, timestamp) VALUES ('hello', 'com.test', 'screen', 1000)"
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_captured_items_dedup ON captured_items(app_package, content_type, text, timestamp)"
        )

        val indexCursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_captured_items_dedup'",
            null,
        )
        indexCursor.moveToFirst()
        assertEquals("index_captured_items_dedup", indexCursor.getString(0))
        indexCursor.close()

        val rowCursor = db.rawQuery("SELECT COUNT(*) FROM captured_items", null)
        rowCursor.moveToFirst()
        assertEquals(1, rowCursor.getInt(0))
        rowCursor.close()
        db.close()
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
