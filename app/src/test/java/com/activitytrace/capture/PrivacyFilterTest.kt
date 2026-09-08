package com.activitytrace.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyFilterTest {

    private class FakeNode(
        override val isPassword: Boolean = false,
        override val isEditable: Boolean = false,
        override val className: String? = null,
        override val text: CharSequence? = null,
        override val contentDescription: CharSequence? = null,
        override val childCount: Int = 0,
    ) : AccessibilityTextNode {
        override fun childAt(index: Int): AccessibilityTextNode? = null
    }

    private fun filter(blocked: Set<String> = emptySet()) =
        PrivacyFilter(SensitiveAppPolicy { blocked.contains(it) })

    @Test
    fun `password node rejected`() {
        assertFalse(filter().shouldCapture("com.example", FakeNode(isPassword = true)))
    }

    @Test
    fun `password node from non-sensitive app rejected`() {
        assertFalse(filter().shouldCapture("com.allowed", FakeNode(isPassword = true)))
    }

    @Test
    fun `normal text view accepted`() {
        assertTrue(
            filter().shouldCapture(
                "com.example",
                FakeNode(className = "android.widget.TextView", text = "hello"),
            )
        )
    }

    @Test
    fun `editable text rejected`() {
        assertFalse(
            filter().shouldCapture(
                "com.example",
                FakeNode(className = "android.widget.EditText", isEditable = true),
            )
        )
    }

    @Test
    fun `read-only edit text accepted`() {
        assertTrue(
            filter().shouldCapture(
                "com.example",
                FakeNode(className = "android.widget.EditText", isEditable = false),
            )
        )
    }

    @Test
    fun `blocked package rejected`() {
        assertFalse(
            filter(setOf("com.blocked")).shouldCapture(
                "com.blocked",
                FakeNode(text = "secret"),
            )
        )
    }

    @Test
    fun `allowed package accepted`() {
        assertTrue(
            filter(setOf("com.blocked")).shouldCapture(
                "com.allowed",
                FakeNode(text = "public"),
            )
        )
    }

    @Test
    fun `node-less event accepted for allowed package`() {
        assertTrue(filter().shouldCapture("com.allowed", null))
    }

    @Test
    fun `node-less event rejected for blocked package`() {
        assertFalse(filter(setOf("com.blocked")).shouldCapture("com.blocked", null))
    }
}