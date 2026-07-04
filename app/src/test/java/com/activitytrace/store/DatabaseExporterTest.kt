package com.activitytrace.store

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val legacyExportFile: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace/activity_trace.db",
        )

    @After
    fun tearDown() {
        context.getDatabasePath("activity_trace.db").delete()
        legacyExportFile.delete()
        legacyExportFile.parentFile?.delete()
    }

    @Test
    fun `export copies db file when source exists`() = runTest {
        val sourceFile = context.getDatabasePath("activity_trace.db")
        sourceFile.parentFile?.mkdirs()
        sourceFile.writeText("dummy database content")

        val result = DatabaseExporter.export(context)

        assertTrue(result)
        assertTrue("Exported file should exist", legacyExportFile.exists())
        assertEquals("dummy database content", legacyExportFile.readText())
    }

    @Test
    fun `export returns false when source db missing`() = runTest {
        val sourceFile = context.getDatabasePath("activity_trace.db")
        sourceFile.delete()

        val result = DatabaseExporter.export(context)

        assertFalse(result)
    }

}
