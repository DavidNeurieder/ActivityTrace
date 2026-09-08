package com.activitytrace.store

import androidx.test.core.app.ApplicationProvider
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FtsSearchTest {

    private val dao = ActivityTraceDatabase.getInstance(ApplicationProvider.getApplicationContext()).captureDao()

    @After
    fun tearDown() = runBlocking {
        dao.getAllItems().forEach { dao.delete(it) }
    }

    @Test
    fun `fts matches whole words and prefixes but not substrings`() = runBlocking {
        insertAll(
            item("kitchen baking adventure", "com.food", "screen", 1),
            item("bake a cake", "com.food", "screen", 2),
            item("shaking a cocktail", "com.bar", "screen", 3),
        )

        val results = dao.searchFts(matchQuery = "bake*").first()
        val texts = results.map { it.text }

        assertTrue("expected bake docs to match", texts.contains("bake a cake"))
        assertTrue("expected prefix baking to match", texts.contains("kitchen baking adventure"))
        assertTrue("expected shaking not to match as substring", !texts.contains("shaking a cocktail"))
    }

    @Test
    fun `fts applies type filter`() = runBlocking {
        insertAll(
            item("important meeting", "com.test", "screen", 1),
            item("important meeting", "com.test", "notification", 2),
        )

        val results = dao.searchFts(matchQuery = "meeting*", contentType = "notification").first()

        assertEquals(listOf("important meeting"), results.map { it.text })
        assertEquals(listOf("notification"), results.map { it.contentType })
    }

    @Test
    fun `fts applies app package filter`() = runBlocking {
        insertAll(
            item("quarterly report", "com.acme", "screen", 1),
            item("quarterly report", "com.competitor", "screen", 2),
        )

        val results = dao.searchFts(matchQuery = "report*", appPackage = "com.acme").first()

        assertEquals(listOf("com.acme"), results.map { it.appPackage })
    }

    private suspend fun insertAll(vararg items: CapturedItem) {
        items.forEach { dao.insert(it) }
    }

    private fun item(
        text: String,
        appPackage: String,
        contentType: String,
        timestamp: Long,
    ) = CapturedItem(
        text = text,
        appPackage = appPackage,
        contentType = contentType,
        timestamp = timestamp,
        contentHash = ContentHasher.hash(appPackage, contentType, text),
    )
}
