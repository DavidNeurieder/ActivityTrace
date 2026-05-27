package com.activitytrace.search

data class ParsedQuery(
    val keywords: List<String>,
    val appFilter: String? = null,
    val typeFilter: String? = null,
    val timeRange: Pair<Long, Long>? = null,
)

object QueryParser {
    fun parse(input: String): ParsedQuery {
        val lower = input.lowercase()
        val words = lower.split(Regex("\\s+"))

        val keywords = mutableListOf<String>()
        var appFilter: String? = null
        var typeFilter: String? = null

        for (word in words) {
            when {
                word.startsWith("in:") -> appFilter = word.removePrefix("in:")
                word.startsWith("type:") -> typeFilter = word.removePrefix("type:")
                else -> keywords.add(word)
            }
        }

        return ParsedQuery(
            keywords = keywords,
            appFilter = appFilter,
            typeFilter = typeFilter,
        )
    }
}
