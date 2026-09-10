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
    fun `fts_matches_full_words_but_not_prefixes_or_substrings`() = runBlocking {
        insertAll(
            item("kitchen baking adventure", "com.food", "screen", 1),
            item("bake a cake", "com.food", "screen", 2),
            item("shaking a cocktail", "com.bar", "screen", 3),
        )

        val results = dao.searchFtsCandidates(matchQuery = "bake").first()
        val texts = results.map { it.item.text }

        assertTrue("expected exact bake docs to match", texts.contains("bake a cake"))
        assertTrue("expected prefix baking not to match", !texts.contains("kitchen baking adventure"))
        assertTrue("expected shaking not to match as substring", !texts.contains("shaking a cocktail"))
    }

    @Test
    fun `fts_applies_type_filter`() = runBlocking {
        insertAll(
            item("important meeting", "com.test", "screen", 1),
            item("important meeting", "com.test", "notification", 2),
        )

        val results = dao.searchFtsCandidates(matchQuery = "meeting", contentType = "notification").first()

        assertEquals(listOf("important meeting"), results.map { it.item.text })
        assertEquals(listOf("notification"), results.map { it.item.contentType })
    }

    @Test
    fun `fts_applies_app_package_filter`() = runBlocking {
        insertAll(
            item("quarterly report", "com.acme", "screen", 1),
            item("quarterly report", "com.competitor", "screen", 2),
        )

        val results = dao.searchFtsCandidates(matchQuery = "report", appPackage = "com.acme").first()

        assertEquals(listOf("com.acme"), results.map { it.item.appPackage })
    }

    @Test
    fun `fts_orders_candidates_by_bm25_relevance`() = runBlocking {
        insertAll(
            item("pizza restaurant in Munich", "com.food", "screen", 1000),
            item("something unrelated", "com.other", "screen", 1001),
            item("pizza", "com.food", "screen", 1002),
            item("pizza pizza pizza", "com.food", "screen", 1003),
        )

        val candidates = dao.searchFtsCandidates(matchQuery = "pizza").first()

        val texts = candidates.map { it.item.text }
        assertEquals(
            "repeated-term doc should rank first, then singleton matches",
            listOf("pizza pizza pizza", "pizza", "pizza restaurant in Munich"),
            texts,
        )
        assertTrue("unrelated doc must not match", !texts.contains("something unrelated"))
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
