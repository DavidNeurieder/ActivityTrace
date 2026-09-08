package com.activitytrace.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentHasherTest {

    @Test
    fun `hash calculation is deterministic`() {
        val first = ContentHasher.hash("com.a", "screen", "Hello world")
        val second = ContentHasher.hash("com.a", "screen", "Hello world")
        assertEquals(first, second)
    }

    @Test
    fun `changing package changes hash`() {
        assertNotEquals(
            ContentHasher.hash("com.a", "screen", "hello"),
            ContentHasher.hash("com.b", "screen", "hello"),
        )
    }

    @Test
    fun `changing capture type changes hash`() {
        assertNotEquals(
            ContentHasher.hash("com.a", "screen", "hello"),
            ContentHasher.hash("com.a", "toast", "hello"),
        )
    }

    @Test
    fun `changing text changes hash`() {
        assertNotEquals(
            ContentHasher.hash("com.a", "screen", "hello"),
            ContentHasher.hash("com.a", "screen", "world"),
        )
    }

    @Test
    fun `is a lowercase 64 character hex sha256`() {
        val hash = ContentHasher.hash("com.a", "screen", "hello")
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `normalization trims surrounding whitespace`() {
        assertEquals(
            ContentHasher.hash("com.a", "screen", "  hello  "),
            ContentHasher.hash("com.a", "screen", "hello"),
        )
    }

    @Test
    fun `normalization collapses internal whitespace`() {
        assertEquals(
            ContentHasher.hash("com.a", "screen", "hello\n\n   world"),
            ContentHasher.hash("com.a", "screen", "hello world"),
        )
    }

    @Test
    fun `normalization lowercases text`() {
        assertEquals(
            ContentHasher.hash("com.a", "screen", "Hello WORLD"),
            ContentHasher.hash("com.a", "screen", "hello world"),
        )
    }

    @Test
    fun `normalization is consistent across calls`() {
        assertEquals("a b c", ContentHasher.normalizeText("  a\nb\t c  "))
    }
}