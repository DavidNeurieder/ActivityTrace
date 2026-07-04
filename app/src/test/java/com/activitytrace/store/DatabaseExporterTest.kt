package com.activitytrace.store

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseExporterTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        File(context.cacheDir, "export_temp").deleteRecursively()
    }

    @Test
    fun `exportToPlainSqlite creates plain sqlite file and invokes export sql`() {
        val database = mockk<SupportSQLiteDatabase>()
        val cursor = mockk<Cursor>()

        every { cursor.moveToFirst() } returns true
        every { cursor.close() } just runs
        every { database.query(any<String>()) } returns cursor
        every { database.execSQL(any<String>()) } just runs

        val outputFile = File(context.cacheDir, "test_export/test.sqlite")
        outputFile.parentFile?.mkdirs()

        DatabaseExporter.exportToPlainSqlite(database, outputFile)

        verify(exactly = 2) { database.execSQL(any<String>()) }
        verify(exactly = 1) { database.query("SELECT sqlcipher_export('plain')") }

        assertTrue("Output file should exist", outputFile.exists())
        val magic = outputFile.readBytes().take(16).toByteArray()
        assertEquals("SQLite format 3\u0000", String(magic))

        outputFile.parentFile?.deleteRecursively()
    }
}
