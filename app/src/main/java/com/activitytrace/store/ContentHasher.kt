package com.activitytrace.store

import java.security.MessageDigest

object ContentHasher {
    private val WHITESPACE = Regex("\\s+")

    fun normalizeText(text: String): String =
        text.trim().replace(WHITESPACE, " ").lowercase()

    fun hash(packageName: String, captureType: String, rawText: String): String {
        val normalized = normalizeText(rawText)
        val input = "$packageName|$captureType|$normalized"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}