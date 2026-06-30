package com.activitytrace.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryParserTest {

    @Test
    fun `empty input returns empty keywords and no filters`() {
        val result = QueryParser.parse("")
        assertTrue(result.keywords.isEmpty())
        assertNull(result.appFilter)
        assertNull(result.typeFilter)
        assertNull(result.timeRange)
    }

    @Test
    fun `single keyword`() {
        val result = QueryParser.parse("hello")
        assertEquals(listOf("hello"), result.keywords)
    }

    @Test
    fun `multiple keywords`() {
        val result = QueryParser.parse("hello world test")
        assertEquals(listOf("hello", "world", "test"), result.keywords)
    }

    @Test
    fun `today sets timeRange`() {
        val result = QueryParser.parse("hello today")
        assertEquals(listOf("hello"), result.keywords)
        assertNotNull(result.timeRange)
        val (start, end) = result.timeRange!!
        assertTrue(start < end)
    }

    @Test
    fun `yesterday sets timeRange`() {
        val result = QueryParser.parse("yesterday")
        assertTrue(result.keywords.isEmpty())
        assertNotNull(result.timeRange)
        val (start, end) = result.timeRange!!
        assertTrue(start < end)
    }

    @Test
    fun `last week sets timeRange`() {
        val result = QueryParser.parse("last week")
        assertTrue(result.keywords.isEmpty())
        assertNotNull(result.timeRange)
        val (start, end) = result.timeRange!!
        assertTrue(start < end)
    }

    @Test
    fun `this week sets timeRange`() {
        val result = QueryParser.parse("this week")
        assertTrue(result.keywords.isEmpty())
        assertNotNull(result.timeRange)
        val (start, end) = result.timeRange!!
        assertTrue(start < end)
    }

    @Test
    fun `bare month name is keyword not timeRange`() {
        val result = QueryParser.parse("january")
        assertEquals(listOf("january"), result.keywords)
        assertNull(result.timeRange)
    }

    @Test
    fun `bare month abbreviation is keyword not timeRange`() {
        val result = QueryParser.parse("jan")
        assertEquals(listOf("jan"), result.keywords)
        assertNull(result.timeRange)
    }

    @Test
    fun `month name with day sets timeRange`() {
        val result = QueryParser.parse("january 15")
        assertTrue(result.keywords.isEmpty())
        assertNotNull(result.timeRange)
        val (start, end) = result.timeRange!!
        assertTrue(start < end)
    }

    @Test
    fun `month name with day and year sets timeRange`() {
        val result = QueryParser.parse("january 15, 2025")
        assertTrue(result.keywords.isEmpty())
        assertNotNull(result.timeRange)
        val (start, end) = result.timeRange!!
        assertTrue(start < end)
    }

    @Test
    fun `all month names are keywords`() {
        val months = listOf("january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december")
        for (month in months) {
            val result = QueryParser.parse(month)
            assertEquals("$month should be a keyword", listOf(month), result.keywords)
            assertNull("$month should not set timeRange", result.timeRange)
        }
    }

    @Test
    fun `all month abbreviations are keywords`() {
        val abbrs = listOf("jan", "feb", "mar", "apr", "jun", "jul",
            "aug", "sep", "sept", "oct", "nov", "dec")
        for (abbr in abbrs) {
            val result = QueryParser.parse(abbr)
            assertEquals("$abbr should be a keyword", listOf(abbr), result.keywords)
            assertNull("$abbr should not set timeRange", result.timeRange)
        }
    }

    @Test
    fun `in filter extracts appPackage`() {
        val result = QueryParser.parse("in:com.example.app")
        assertEquals("com.example.app", result.appFilter)
        assertTrue(result.keywords.isEmpty())
    }

    @Test
    fun `in filter with keywords`() {
        val result = QueryParser.parse("in:com.example.app hello")
        assertEquals("com.example.app", result.appFilter)
        assertEquals(listOf("hello"), result.keywords)
    }

    @Test
    fun `type filter extracts contentType`() {
        val result = QueryParser.parse("type:notification")
        assertEquals("notification", result.typeFilter)
    }

    @Test
    fun `type filter with keywords`() {
        val result = QueryParser.parse("type:screenshot test")
        assertEquals("screenshot", result.typeFilter)
        assertEquals(listOf("test"), result.keywords)
    }

    @Test
    fun `mixed filters and keywords`() {
        val result = QueryParser.parse("in:com.test type:alert urgent today")
        assertEquals("com.test", result.appFilter)
        assertEquals("alert", result.typeFilter)
        assertEquals(listOf("urgent"), result.keywords)
        assertNotNull(result.timeRange)
    }

    @Test
    fun `last standalone is kept as keyword`() {
        val result = QueryParser.parse("last")
        assertEquals(listOf("last"), result.keywords)
        assertNull(result.timeRange)
    }

    @Test
    fun `this standalone is kept as keyword`() {
        val result = QueryParser.parse("this")
        assertEquals(listOf("this"), result.keywords)
        assertNull(result.timeRange)
    }

    @Test
    fun `case insensitive parsing`() {
        val result = QueryParser.parse("Today In:App Type:Alert")
        assertEquals("app", result.appFilter)
        assertEquals("alert", result.typeFilter)
        assertTrue(result.keywords.isEmpty())
        assertNotNull(result.timeRange)
    }

    @Test
    fun `multiple words with today`() {
        val result = QueryParser.parse("meeting notes today")
        assertEquals(listOf("meeting", "notes"), result.keywords)
        assertNotNull(result.timeRange)
    }

    @Test
    fun `last week with keyword`() {
        val result = QueryParser.parse("project last week")
        assertEquals(listOf("project"), result.keywords)
        assertNotNull(result.timeRange)
    }

    @Test
    fun `whitespace handling`() {
        val result = QueryParser.parse("  hello   world  ")
        assertEquals(listOf("hello", "world"), result.keywords)
    }

    @Test
    fun `timeRange start is before end`() {
        val result = QueryParser.parse("today")
        val (start, end) = result.timeRange!!
        assertTrue("start $start should be < end $end", start < end)
    }
}
