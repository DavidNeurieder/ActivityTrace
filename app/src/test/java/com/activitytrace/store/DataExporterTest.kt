package com.activitytrace.store

import android.content.Context
import android.os.Environment
import com.activitytrace.model.CapturedItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
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
class DataExporterTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val legacyExportFile: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace/activity_trace.json",
        )

    private val legacyCsvExportFile: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ActivityTrace/activity_trace.csv",
        )

    @After
    fun tearDown() {
        legacyExportFile.delete()
        legacyCsvExportFile.delete()
        legacyExportFile.parentFile?.delete()
    }

    @Test
    fun `buildJson returns valid json with all fields`() {
        val items = listOf(
            CapturedItem(
                id = 1,
                text = "Hello world",
                appPackage = "com.example",
                appName = "Example",
                contentType = "text",
                category = "test",
                timestamp = 1000L,
                metadata = "{\"key\":\"value\"}",
            ),
        )

        val json = DataExporter.buildJson(items)
        val root = JSONObject(json)

        assertEquals(1, root.getInt("version"))
        assertTrue(root.getLong("exportedAt") > 0)
        assertEquals(1, root.getInt("itemCount"))

        val itemsArray = root.getJSONArray("items")
        assertEquals(1, itemsArray.length())

        val item = itemsArray.getJSONObject(0)
        assertEquals(1, item.getLong("id"))
        assertEquals("Hello world", item.getString("text"))
        assertEquals("com.example", item.getString("appPackage"))
        assertEquals("Example", item.getString("appName"))
        assertEquals("text", item.getString("contentType"))
        assertEquals("test", item.getString("category"))
        assertEquals(1000L, item.getLong("timestamp"))
        assertEquals("{\"key\":\"value\"}", item.getString("metadata"))
    }

    @Test
    fun `buildJson handles null appName category and metadata`() {
        val items = listOf(
            CapturedItem(
                id = 1,
                text = "text",
                appPackage = "pkg",
                appName = null,
                contentType = "plain",
                category = null,
                timestamp = 500L,
                metadata = null,
            ),
        )

        val json = DataExporter.buildJson(items)
        val root = JSONObject(json)
        val item = root.getJSONArray("items").getJSONObject(0)

        assertEquals(JSONObject.NULL, item.get("appName"))
        assertEquals(JSONObject.NULL, item.get("category"))
        assertEquals(JSONObject.NULL, item.get("metadata"))
    }

    @Test
    fun `buildJson returns empty items array for empty list`() {
        val json = DataExporter.buildJson(emptyList())
        val root = JSONObject(json)

        assertEquals(0, root.getInt("itemCount"))
        val itemsArray = root.getJSONArray("items")
        assertEquals(0, itemsArray.length())
    }

    @Test
    fun `exportToJson writes file via dao when dao returns items`() = runTest {
        val dao = mockk<CaptureDao>()
        coEvery { dao.getAllItems() } returns listOf(
            CapturedItem(
                id = 1,
                text = "test item",
                appPackage = "com.test",
                appName = "Test",
                contentType = "text",
                category = null,
                timestamp = 42L,
                metadata = null,
            ),
        )

        val result = DataExporter.exportToJson(context, dao)

        assertTrue("Expected export to succeed, got: ${(result as? ExportStatus.Error)?.message}", result is ExportStatus.Success)
        assertTrue("Exported file should exist", legacyExportFile.exists())

        val content = legacyExportFile.readText()
        val root = JSONObject(content)
        assertEquals(1, root.getInt("itemCount"))
        assertEquals(
            "test item",
            root.getJSONArray("items").getJSONObject(0).getString("text"),
        )
    }

    @Test
    fun `exportToJson succeeds when dao returns empty list`() = runTest {
        val dao = mockk<CaptureDao>()
        coEvery { dao.getAllItems() } returns emptyList()

        val result = DataExporter.exportToJson(context, dao)

        assertTrue("Expected success, got: ${(result as? ExportStatus.Error)?.message}", result is ExportStatus.Success)
        assertTrue(legacyExportFile.exists())

        val root = JSONObject(legacyExportFile.readText())
        assertEquals(0, root.getInt("itemCount"))
    }

    @Test
    fun `exportToJson returns error when dao throws`() = runTest {
        val dao = mockk<CaptureDao>()
        coEvery { dao.getAllItems() } throws RuntimeException("db error")

        val result = DataExporter.exportToJson(context, dao)

        assertTrue("Expected error, got success", result is ExportStatus.Error)
    }

    @Test
    fun `buildCsv produces header and data rows`() {
        val items = listOf(
            CapturedItem(id = 1, text = "hello", appPackage = "com.a", appName = "A", contentType = "text", category = null, timestamp = 100L, metadata = null),
            CapturedItem(id = 2, text = "world", appPackage = "com.b", appName = null, contentType = "image", category = "cat", timestamp = 200L, metadata = "meta"),
        )

        val csv = DataExporter.buildCsv(items)
        val lines = csv.lines()

        assertTrue(lines[0].startsWith('\uFEFF' + "id,text,appPackage,appName,contentType,category,timestamp,metadata"))
        assertEquals(4, lines.size)
        assertTrue(lines[1].contains("hello"))
        assertTrue(lines[2].contains("world"))
    }

    @Test
    fun `buildCsv escapes commas quotes and newlines`() {
        val items = listOf(
            CapturedItem(id = 1, text = "has, comma", appPackage = "has \"quote\"", appName = null, contentType = "plain", category = null, timestamp = 1L, metadata = "line\nbreak"),
        )

        val csv = DataExporter.buildCsv(items)
        val lines = csv.lines()

        assertTrue(lines[1].contains("\"has, comma\""))
        assertTrue(lines[1].contains("\"has \"\"quote\"\"\""))
        assertTrue(lines[1].startsWith("1,"))
        assertTrue(lines[2].startsWith("break"))
    }

    @Test
    fun `buildCsv returns only header for empty list`() {
        val csv = DataExporter.buildCsv(emptyList())
        val lines = csv.lines()

        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith('\uFEFF' + "id"))
    }

    @Test
    fun `exportToCsv writes file via dao`() = runTest {
        val dao = mockk<CaptureDao>()
        coEvery { dao.getAllItems() } returns listOf(
            CapturedItem(id = 1, text = "csv item", appPackage = "com.csv", appName = "CSV", contentType = "text", category = null, timestamp = 42L, metadata = null),
        )

        val result = DataExporter.exportToCsv(context, dao)

        assertTrue("Expected success, got: ${(result as? ExportStatus.Error)?.message}", result is ExportStatus.Success)
        assertTrue(legacyCsvExportFile.exists())
        val content = legacyCsvExportFile.readText()
        assertTrue(content.contains("csv item"))
        assertTrue(content.contains("com.csv"))
    }
}
