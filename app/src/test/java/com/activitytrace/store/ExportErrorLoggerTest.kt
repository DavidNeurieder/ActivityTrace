package com.activitytrace.store

import android.content.Context
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
        get() = File(context.cacheDir, "export_errors")

    @After
    fun tearDown() {
        ExportErrorLogger.clearLogs(context)
    }

    @Test
    fun `saveErrorLog writes log file with operation and exception info`() {
        val exception = RuntimeException("test error msg")
        ExportErrorLogger.saveErrorLog(context, "export_test", exception)

        val files = ExportErrorLogger.getLogFiles(context)
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

        val files = ExportErrorLogger.getLogFiles(context)
        assertEquals(1, files.size)
        val content = files[0].readText()

        assertTrue(content.contains("Caused by: java.lang.IllegalStateException: root failure"))
    }

    @Test
    fun `saveErrorLog does not crash on exception with empty message`() {
        val exception = NullPointerException()
        ExportErrorLogger.saveErrorLog(context, "null_msg", exception)

        val files = ExportErrorLogger.getLogFiles(context)
        assertTrue("Should create log file even with null message", files.size >= 1)
    }

    @Test
    fun `clearLogs removes all log files`() {
        ExportErrorLogger.saveErrorLog(context, "clear_a", RuntimeException("a"))
        ExportErrorLogger.saveErrorLog(context, "clear_b", IllegalStateException("b"))
        assertTrue("Should have at least 2 log files", ExportErrorLogger.getLogFiles(context).size >= 2)

        ExportErrorLogger.clearLogs(context)

        assertEquals(0, ExportErrorLogger.getLogFiles(context).size)
    }
}
