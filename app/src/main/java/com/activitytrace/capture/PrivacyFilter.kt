package com.activitytrace.capture

internal fun isSensitiveEditableField(node: AccessibilityTextNode): Boolean =
    node.className == "android.widget.EditText" && node.isEditable

class PrivacyFilter(
    private val sensitiveAppPolicy: SensitiveAppPolicy,
) {
    fun shouldCapture(packageName: String, node: AccessibilityTextNode?): Boolean {
        if (sensitiveAppPolicy.isBlocked(packageName)) return false
        if (node == null) return true
        return shouldCaptureNode(node)
    }

    private fun shouldCaptureNode(node: AccessibilityTextNode): Boolean {
        if (node.isPassword) return false
        if (isSensitiveEditableField(node)) return false
        return true
    }
}