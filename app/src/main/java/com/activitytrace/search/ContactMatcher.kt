package com.activitytrace.search

object ContactMatcher {
    private val contactNames = setOf<String>() // TODO: load from ContactsContract

    fun findContactNames(query: String): List<String> {
        val lower = query.lowercase()
        return contactNames.filter { it.lowercase().contains(lower) }
    }
}
