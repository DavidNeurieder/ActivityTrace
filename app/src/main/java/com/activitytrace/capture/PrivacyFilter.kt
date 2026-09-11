package com.activitytrace.capture

enum class PrivacyDecision {
    ALLOW,
    BLOCK_APP,
    BLOCK_PASSWORD,
}

class PrivacyFilter(
    private val sensitiveAppPolicy: SensitiveAppPolicy,
) {
    fun evaluate(packageName: String, node: AccessibilityTextNode?): PrivacyDecision {
        if (sensitiveAppPolicy.isBlocked(packageName)) return PrivacyDecision.BLOCK_APP
        if (node?.isPassword == true) return PrivacyDecision.BLOCK_PASSWORD
        return PrivacyDecision.ALLOW
    }

    companion object {
        const val UNKNOWN_PACKAGE = "unknown"
    }
}