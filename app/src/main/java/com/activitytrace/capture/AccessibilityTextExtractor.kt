package com.activitytrace.capture

import android.view.accessibility.AccessibilityNodeInfo

interface AccessibilityTextNode {
    val isPassword: Boolean
    val isEditable: Boolean
    val className: String?
    val text: CharSequence?
    val contentDescription: CharSequence?
    val childCount: Int

    fun childAt(index: Int): AccessibilityTextNode?

    fun releaseChild(child: AccessibilityTextNode) {
    }
}

class AccessibilityNodeInfoNode(private val node: AccessibilityNodeInfo) : AccessibilityTextNode {
    override val isPassword: Boolean get() = node.isPassword
    override val isEditable: Boolean get() = node.isEditable
    override val className: String? get() = node.className?.toString()
    override val text: CharSequence? get() = node.text
    override val contentDescription: CharSequence? get() = node.contentDescription
    override val childCount: Int get() = node.childCount

    override fun childAt(index: Int): AccessibilityTextNode? {
        val child = node.getChild(index) ?: return null
        return AccessibilityNodeInfoNode(child)
    }

    override fun releaseChild(child: AccessibilityTextNode) {
        (child as? AccessibilityNodeInfoNode)?.node?.recycle()
    }

    fun release() {
        node.recycle()
    }
}

data class ExtractionLimits(
    val maxDepth: Int = 32,
    val maxNodes: Int = 1_000,
    val maxCharacters: Int = 100_000,
)

class AccessibilityTextExtractor(
    private val limits: ExtractionLimits = ExtractionLimits(),
) {
    fun extract(root: AccessibilityTextNode?): String {
        if (root == null) return ""
        val traversal = Traversal(limits)
        traversal.visit(root, 0)
        return traversal.parts.joinToString(" ")
    }

    private class Traversal(private val limits: ExtractionLimits) {
        val parts = mutableListOf<String>()
        private var visited = 0
        private var characters = 0

        fun visit(node: AccessibilityTextNode, depth: Int) {
            if (visited >= limits.maxNodes) return
            visited += 1

            if (node.isPassword) return
            if (isSensitiveEditableField(node)) return

            append(node.text)
            append(node.contentDescription)

            if (depth >= limits.maxDepth) return
            if (exhausted) return

            for (i in 0 until node.childCount) {
                if (exhausted) break
                val child = node.childAt(i) ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    node.releaseChild(child)
                }
            }
        }

        private fun append(value: CharSequence?) {
            val text = value?.toString()?.trim() ?: return
            if (text.isEmpty()) return
            val remaining = limits.maxCharacters - characters
            if (remaining <= 0) return
            val chunk = if (text.length <= remaining) text else text.substring(0, remaining)
            parts.add(chunk)
            characters += chunk.length
        }

        private val exhausted: Boolean
            get() = visited >= limits.maxNodes || characters >= limits.maxCharacters
    }
}