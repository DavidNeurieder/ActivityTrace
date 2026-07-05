package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.activitytrace.model.CapturedItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BackupImporterTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        File(context.cacheDir, "import_temp").deleteRecursively()
    }

    @Test
    fun `readItemsFromSqlite returns all rows`() {
        val dbFile = createBackupDb(3)

        val items = BackupImporter.readItemsFromSqlite(dbFile)

        assertEquals(3, items.size)
        assertEquals("item_0", items[0].text)
        assertEquals("com.test", items[0].appPackage)
        assertEquals("TestApp", items[0].appName)
        assertEquals("notification", items[0].contentType)
        assertEquals(null, items[0].category)
        assertEquals(1000L, items[0].timestamp)
        assertEquals(null, items[0].metadata)
    }

    @Test
    fun `readItemsFromSqlite returns empty list for empty table`() {
        val dbFile = createBackupDb(0)

        val items = BackupImporter.readItemsFromSqlite(dbFile)

        assertEquals(0, items.size)
    }

    @Test
    fun `readItemsFromSqlite handles nullable fields`() {
        val dbFile = createBackupDbWithNullable()

        val items = BackupImporter.readItemsFromSqlite(dbFile)

        assertEquals(2, items.size)
        assertEquals(null, items[0].appName)
        assertEquals(null, items[0].category)
        assertEquals(null, items[0].metadata)
        assertEquals("NotNull", items[1].appName)
        assertEquals("cat", items[1].category)
        assertEquals("meta", items[1].metadata)
    }

    @Test
    fun `importFromBackup inserts only new items based on dedup keys`() = runTest {
        val dao = mockk<CaptureDao>()
        coEvery { dao.getAllItemKeys() } returns listOf(
            CaptureDao.ItemKey("item_0", 1000L, "com.test"),
            CaptureDao.ItemKey("item_1", 2000L, "com.test"),
        )
        coEvery { dao.insertAll(any()) } returns Unit

        val backupUri = createBackupUri(3)

        val count = BackupImporter.importFromBackup(context, backupUri, dao)

        assertEquals(1, count)
        coVerify(exactly = 1) { dao.insertAll(match { it.size == 1 && it[0].text == "item_2" }) }
    }

    @Test
    fun `importFromBackup returns 0 when no new items`() = runTest {
        val dao = mockk<CaptureDao>()
        coEvery { dao.getAllItemKeys() } returns listOf(
            CaptureDao.ItemKey("item_0", 1000L, "com.test"),
            CaptureDao.ItemKey("item_1", 2000L, "com.test"),
            CaptureDao.ItemKey("item_2", 3000L, "com.test"),
        )
        coEvery { dao.insertAll(any()) } returns Unit

        val backupUri = createBackupUri(3)

        val count = BackupImporter.importFromBackup(context, backupUri, dao)

        assertEquals(0, count)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    private fun createBackupDb(itemCount: Int): File {
        val file = File(context.cacheDir, "backup_test/test_backup.db")
        file.parentFile?.mkdirs()
        file.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """
            CREATE TABLE captured_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                app_package TEXT NOT NULL,
                app_name TEXT,
                content_type TEXT NOT NULL,
                category TEXT,
                timestamp INTEGER NOT NULL,
                metadata TEXT
            )
            """.trimIndent()
        )

        for (i in 0 until itemCount) {
            db.execSQL(
                "INSERT INTO captured_items (text, app_package, app_name, content_type, category, timestamp, metadata) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf("item_$i", "com.test", "TestApp", "notification", null, 1000L * (i + 1), null),
            )
        }

        db.close()
        return file
    }

    private fun createBackupDbWithNullable(): File {
        val file = File(context.cacheDir, "backup_test/test_nullable.db")
        file.parentFile?.mkdirs()
        file.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """
            CREATE TABLE captured_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                app_package TEXT NOT NULL,
                app_name TEXT,
                content_type TEXT NOT NULL,
                category TEXT,
                timestamp INTEGER NOT NULL,
                metadata TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            "INSERT INTO captured_items (text, app_package, app_name, content_type, category, timestamp, metadata) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf("all_null", "com.null", null, "text", null, 100L, null),
        )
        db.execSQL(
            "INSERT INTO captured_items (text, app_package, app_name, content_type, category, timestamp, metadata) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf("all_set", "com.notnull", "NotNull", "text", "cat", 200L, "meta"),
        )

        db.close()
        return file
    }

    private fun createBackupUri(itemCount: Int): android.net.Uri {
        val file = createBackupDb(itemCount)
        return android.net.Uri.fromFile(file)
    }
}
