package com.activitytrace.capture

enum class PrivacyDecision {
    ALLOW,
    BLOCK_APP,
    BLOCK_PASSWORD,
    BLOCK_EDITABLE,
    BLOCK_UNKNOWN,
}

internal fun isSensitiveEditableField(node: AccessibilityTextNode): Boolean =
    node.className == "android.widget.EditText" && node.isEditable

class PrivacyFilter(
    private val sensitiveAppPolicy: SensitiveAppPolicy,
) {
    fun evaluate(packageName: String, node: AccessibilityTextNode?): PrivacyDecision {
        if (sensitiveAppPolicy.isBlocked(packageName)) return PrivacyDecision.BLOCK_APP
        if (node == null) {
            return if (packageName == UNKNOWN_PACKAGE) {
                PrivacyDecision.BLOCK_UNKNOWN
            } else {
                PrivacyDecision.ALLOW
            }
        }
        if (node.isPassword) return PrivacyDecision.BLOCK_PASSWORD
        if (isSensitiveEditableField(node)) return PrivacyDecision.BLOCK_EDITABLE
        return PrivacyDecision.ALLOW
    }

    companion object {
        const val UNKNOWN_PACKAGE = "unknown"
    }
}