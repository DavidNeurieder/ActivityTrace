package com.activitytrace.store

import android.content.Context
import android.os.Environment
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
class ExportErrorLoggerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val logDir: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace",
        )

    @After
    fun tearDown() {
        logDir.deleteRecursively()
    }

    @Test
    fun `saveErrorLog writes log file with operation and exception info`() {
        val exception = RuntimeException("test error msg")
        ExportErrorLogger.saveErrorLog(context, "export_test", exception)

        val files = logDir.listFiles { f -> f.name.endsWith(".log") } ?: emptyArray()
        assertEquals(1, files.size)
        val content = files[0].readText()

        assertTrue(content.contains("=== Activity Trace Export Error ==="))
        assertTrue(content.contains("Operation: export_test"))
        assertTrue(content.contains("java.lang.RuntimeException: test error msg"))
        assertTrue(content.contains("Stack trace:"))
    }

    @Test
    fun `saveErrorLog includes cause chain when exception has cause`() {
        val cause = IllegalStateException("root failure")
        val exception = RuntimeException("wrapper", cause)
        ExportErrorLogger.saveErrorLog(context, "chain", exception)

        val files = logDir.listFiles { f -> f.name.endsWith(".log") } ?: emptyArray()
        assertEquals(1, files.size)
        val content = files[0].readText()

        assertTrue(content.contains("Caused by: java.lang.IllegalStateException: root failure"))
    }

    @Test
    fun `saveErrorLog does not crash on exception with empty message`() {
        val exception = NullPointerException()
        ExportErrorLogger.saveErrorLog(context, "null_msg", exception)

        val files = logDir.listFiles { f -> f.name.endsWith(".log") } ?: emptyArray()
        assertTrue("Should create log file even with null message", files.size >= 1)
    }
}
