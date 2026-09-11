package com.activitytrace.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyFilterTest {

    private class FakeNode(
        override val isPassword: Boolean = false,
        override val text: CharSequence? = null,
        override val contentDescription: CharSequence? = null,
        private val children: List<AccessibilityTextNode?> = emptyList(),
    ) : AccessibilityTextNode {
        override val childCount: Int get() = children.size
        override fun childAt(index: Int): AccessibilityTextNode? = children.getOrNull(index)
    }

    private fun filter(blocked: Set<String> = emptySet()) =
        PrivacyFilter(SensitiveAppPolicy { blocked.contains(it) })

    @Test
    fun `password node blocked`() {
        assertEquals(
            PrivacyDecision.BLOCK_PASSWORD,
            filter().evaluate("com.example", FakeNode(isPassword = true)),
        )
    }

    @Test
    fun `password node from non-sensitive app blocked`() {
        assertEquals(
            PrivacyDecision.BLOCK_PASSWORD,
            filter().evaluate("com.allowed", FakeNode(isPassword = true)),
        )
    }

    @Test
    fun `password wins over editable`() {
        assertEquals(
            PrivacyDecision.BLOCK_PASSWORD,
            filter().evaluate(
                "com.example",
                FakeNode(isPassword = true),
            ),
        )
    }

    @Test
    fun `normal text view allowed`() {
        assertEquals(
            PrivacyDecision.ALLOW,
            filter().evaluate(
                "com.example",
                FakeNode(text = "hello"),
            ),
        )
    }

    @Test
    fun `editable non-password field allowed`() {
        assertEquals(
            PrivacyDecision.ALLOW,
            filter().evaluate(
                "com.example",
                FakeNode(text = "typed value"),
            ),
        )
    }

    @Test
    fun `read-only text allowed`() {
        assertEquals(
            PrivacyDecision.ALLOW,
            filter().evaluate(
                "com.example",
                FakeNode(text = "read-only"),
            ),
        )
    }

    @Test
    fun `blocked package blocked regardless of node`() {
        assertEquals(
            PrivacyDecision.BLOCK_APP,
            filter(setOf("com.blocked")).evaluate("com.blocked", FakeNode(text = "secret")),
        )
    }

    @Test
    fun `blocked package with password node still BLOCK_APP`() {
        assertEquals(
            PrivacyDecision.BLOCK_APP,
            filter(setOf("com.blocked")).evaluate("com.blocked", FakeNode(isPassword = true)),
        )
    }

    @Test
    fun `blocked package with null node BLOCK_APP`() {
        assertEquals(
            PrivacyDecision.BLOCK_APP,
            filter(setOf("com.blocked")).evaluate("com.blocked", null),
        )
    }

    @Test
    fun `allowed package accepted`() {
        assertEquals(
            PrivacyDecision.ALLOW,
            filter(setOf("com.blocked")).evaluate("com.allowed", FakeNode(text = "public")),
        )
    }

    @Test
    fun `node-less event allowed for normal package`() {
        assertEquals(PrivacyDecision.ALLOW, filter().evaluate("com.allowed", null))
    }

    @Test
    fun `node-less event with unknown package allowed`() {
        assertEquals(
            PrivacyDecision.ALLOW,
            filter().evaluate(PrivacyFilter.UNKNOWN_PACKAGE, null),
        )
    }

    @Test
    fun `unknown package with a real node follows node rules`() {
        assertEquals(
            PrivacyDecision.ALLOW,
            filter().evaluate(PrivacyFilter.UNKNOWN_PACKAGE, FakeNode(text = "hi")),
        )
        assertEquals(
            PrivacyDecision.BLOCK_PASSWORD,
            filter().evaluate(PrivacyFilter.UNKNOWN_PACKAGE, FakeNode(isPassword = true)),
        )
    }

    @Test
    fun `banking app login screen captures nothing when blocked`() {
        val screen = FakeNode(
            text = "Balance: 12,345",
            children = listOf(
                FakeNode(text = "Account details"),
            ),
        )
        val filter = filter(setOf("com.banking"))

        assertEquals(PrivacyDecision.BLOCK_APP, filter.evaluate("com.banking", screen))
    }

    @Test
    fun `browser login form on allowed app captures only non-sensitive text`() {
        val username = FakeNode(text = "typed-username")
        val password = FakeNode(
            isPassword = true,
            text = "typed-password",
        )
        val form = FakeNode(
            text = "Sign in",
            children = listOf(username, password),
        )

        assertEquals(PrivacyDecision.ALLOW, filter().evaluate("com.browser", form))
        assertEquals("Sign in typed-username", AccessibilityTextExtractor().extract(form))
    }

    @Test
    fun `chat application captures public text including the composer input`() {
        val composer = FakeNode(text = "private draft message")
        val chat = FakeNode(
            text = "Hello there",
            children = listOf(FakeNode(text = "Received message"), composer),
        )

        assertEquals(PrivacyDecision.ALLOW, filter().evaluate("com.chat", chat))
        assertEquals("Hello there Received message private draft message", AccessibilityTextExtractor().extract(chat))
    }
}