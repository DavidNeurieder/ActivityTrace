package com.activitytrace.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityTextExtractorTest {

    private class FakeNode(
        override val text: CharSequence? = null,
        override val contentDescription: CharSequence? = null,
        override val isPassword: Boolean = false,
        override val isEditable: Boolean = false,
        override val className: String? = "android.widget.TextView",
        private val children: List<AccessibilityTextNode?> = emptyList(),
    ) : AccessibilityTextNode {
        override val childCount: Int get() = children.size
        override fun childAt(index: Int): AccessibilityTextNode? = children.getOrNull(index)
    }

    private val extractor = AccessibilityTextExtractor()

    @Test
    fun `extracts nested text`() {
        val tree = FakeNode(
            text = "Root",
            children = listOf(
                FakeNode(
                    text = "Child",
                    children = listOf(FakeNode(contentDescription = "Deep")),
                )
            ),
        )

        assertEquals("Root Child Deep", extractor.extract(tree))
    }

    @Test
    fun `respects max depth`() {
        val tree = FakeNode(
            text = "Root",
            children = listOf(
                FakeNode(text = "Mid", children = listOf(FakeNode(text = "Deep"))),
            ),
        )
        val extractor = AccessibilityTextExtractor(ExtractionLimits(maxDepth = 1))

        assertEquals("Root Mid", extractor.extract(tree))
    }

    @Test
    fun `respects max nodes`() {
        val tree = FakeNode(
            text = "Root",
            children = List(5) { FakeNode(text = "Leaf$it") },
        )
        val extractor = AccessibilityTextExtractor(ExtractionLimits(maxNodes = 2))

        assertEquals("Root Leaf0", extractor.extract(tree))
    }

    @Test
    fun `respects max characters`() {
        val tree = FakeNode(
            text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            children = listOf(FakeNode(text = "never-reached")),
        )
        val extractor = AccessibilityTextExtractor(ExtractionLimits(maxCharacters = 10))

        assertEquals("ABCDEFGHIJ", extractor.extract(tree))
    }

    @Test
    fun `does not recurse indefinitely`() {
        var child: FakeNode = FakeNode(text = "Deep")
        repeat(200) { i ->
            child = FakeNode(text = "N$i", children = listOf(child))
        }
        val tree = FakeNode(text = "Root", children = listOf(child))
        val extractor = AccessibilityTextExtractor(ExtractionLimits(maxDepth = 32))

        val result = extractor.extract(tree)
        assertEquals(33, result.split(" ").size)
    }

    @Test
    fun `ignores password nodes including subtree`() {
        val tree = FakeNode(
            text = "Root",
            children = listOf(
                FakeNode(
                    text = "PasswordLeak",
                    isPassword = true,
                    children = listOf(FakeNode(text = "SubLeak")),
                )
            ),
        )

        assertEquals("Root", extractor.extract(tree))
    }

    @Test
    fun `ignores sensitive editable fields`() {
        val tree = FakeNode(
            text = "Root",
            children = listOf(
                FakeNode(
                    className = "android.widget.EditText",
                    isEditable = true,
                    text = "typed-secret",
                )
            ),
        )

        assertEquals("Root", extractor.extract(tree))
    }

    @Test
    fun `ignores null children`() {
        var tree: FakeNode? = FakeNode(text = "A")
        tree = FakeNode(text = "B", children = listOf(tree!!, FakeNode(text = "C")))
        val root = FakeNode(text = "Root", children = listOf(null, tree))

        assertEquals("Root B A C", extractor.extract(root))
    }

    @Test
    fun `handles empty tree`() {
        assertEquals("", extractor.extract(null))
    }

    @Test
    fun `handles blank tree`() {
        assertEquals("", extractor.extract(FakeNode(text = "   ", contentDescription = null)))
    }

    @Test
    fun `ten thousand node tree completes within the node limit`() {
        val started = System.nanoTime()
        val tree = FakeNode(
            text = "Root",
            children = List(10_000) { FakeNode(text = "Leaf$it") },
        )

        val result = extractor.extract(tree)

        val durationMs = (System.nanoTime() - started) / 1_000_000
        val words = result.split(" ").count { it.isNotBlank() }
        assertEquals(1_000, words)
        assertEquals("Root", result.substringBefore(" "))
        assertTrue("extraction took $durationMs ms", durationMs < 3_000)
    }

    @Test
    fun `deep tree does not overflow the stack`() {
        var child: FakeNode = FakeNode(text = "Bottom")
        repeat(2_000) { i ->
            child = FakeNode(text = "N$i", children = listOf(child))
        }
        val tree = FakeNode(text = "Root", children = listOf(child))

        val result = extractor.extract(tree)

        val words = result.split(" ").filter { it.isNotBlank() }
        assertEquals("Root", words.first())
        assertTrue(words.contains("N1968"))
        assertTrue("N1967 must not be visited at depth limit", !words.contains("N1967"))
    }
}