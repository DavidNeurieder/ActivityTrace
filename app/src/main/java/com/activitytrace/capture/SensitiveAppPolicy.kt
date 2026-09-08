package com.activitytrace.capture

class SensitiveAppPolicy(
    private val isBlockedFn: (String) -> Boolean,
) {
    fun isBlocked(packageName: String): Boolean = isBlockedFn(packageName)
}