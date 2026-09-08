package com.activitytrace.store

import android.content.ContentValues
import com.activitytrace.store.ContentHasher.hash
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DeduplicationTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `identical captures deduplicate to one row`() {
        val db = openDb()
        val h = hash("com.a", "screen", "hello")
        assertEquals(1L, db.insert("hello", "com.a", "screen", h, 1000))
        assertEquals(-1L, db.insert("hello", "com.a", "screen", h, 2000))
        assertEquals(1, db.totalCount())
        db.close()
    }

    @Test
    fun `different captures both insert`() {
        val db = openDb()
        db.insert("hello", "com.a", "screen", hash("com.a", "screen", "hello"), 1000)
        db.insert("world", "com.a", "screen", hash("com.a", "screen", "world"), 2000)
        assertEquals(2, db.totalCount())
        db.close()
    }

    @Test
    fun `same hash insert is rejected by the unique index`() {
        val db = openDb()
        val h = hash("com.a", "toast", "dup")
        db.insert("old", "com.a", "toast", h, 1000)
        assertEquals(-1L, db.insert("new", "com.a", "toast", h, 2000))
        val cursor = db.query("SELECT text FROM captured_items")
        cursor.moveToFirst()
        assertEquals("old", cursor.getString(0))
        cursor.close()
        db.close()
    }

    @Test
    fun `existing rows without a hash are untouched and do not collide with new unique hashes`() {
        val db = openDb()
        db.insert("old", "old_row", "notification", null, 1000)
        db.insert("legacy", "old_row", "notification", null, 1500)
        db.insert("new row", "old_row", "notification", hash("old_row", "notification", "new row"), 2000)
        assertEquals(3, db.totalCount())
        db.close()
    }

    @Test
    fun `concurrent identical inserts produce one row`() {
        val db = openDb()
        val h = hash("com.a", "screen", "race")
        val threads = (1..16).map { Thread { db.insert("race", "com.a", "screen", h, 1L) } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1, db.totalCount())
        db.close()
    }

    private fun openDb(): PlainDb {
        val file = File(context.cacheDir, "dedup_test/unique.db").also {
            it.parentFile?.mkdirs()
            it.delete()
        }
        return PlainDb(file.absolutePath)
    }

    private class PlainDb(path: String) {
        private val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)

        init {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS captured_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    text TEXT NOT NULL,
                    app_package TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    content_hash TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_captured_items_content_hash
                ON captured_items(content_hash)
                """.trimIndent()
            )
        }

        fun insert(text: String, appPackage: String, contentType: String, h: String?, timestamp: Long): Long {
            return db.insert("captured_items", null, ContentValues().apply {
                put("text", text)
                put("app_package", appPackage)
                put("content_type", contentType)
                put("timestamp", timestamp)
                if (h != null) put("content_hash", h)
            })
        }

        fun totalCount(): Int {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM captured_items", null)
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()
            return count
        }

        fun query(sql: String): android.database.Cursor = db.rawQuery(sql, null)

        fun close() = db.close()
    }
}