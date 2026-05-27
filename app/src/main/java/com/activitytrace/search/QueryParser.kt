package com.activitytrace.search

import java.util.Calendar
import java.util.Locale

data class ParsedQuery(
    val keywords: List<String>,
    val appFilter: String? = null,
    val typeFilter: String? = null,
    val timeRange: Pair<Long, Long>? = null,
)

object QueryParser {
    private val months = mapOf(
        "january" to 0, "jan" to 0,
        "february" to 1, "feb" to 1,
        "march" to 2, "mar" to 2,
        "april" to 3, "apr" to 3,
        "may" to 4,
        "june" to 5, "jun" to 5,
        "july" to 6, "jul" to 6,
        "august" to 7, "aug" to 7,
        "september" to 8, "sep" to 8, "sept" to 8,
        "october" to 9, "oct" to 9,
        "november" to 10, "nov" to 10,
        "december" to 11, "dec" to 11,
    )

    fun parse(input: String): ParsedQuery {
        if (input.isBlank()) return ParsedQuery(keywords = emptyList())
        val lower = input.lowercase(Locale.ROOT)
        val words = lower.split(Regex("\\s+")).filter { it.isNotEmpty() }

        val keywords = mutableListOf<String>()
        var appFilter: String? = null
        var typeFilter: String? = null
        var timeRange: Pair<Long, Long>? = null
        var skipNext = false

        for (i in words.indices) {
            if (skipNext) { skipNext = false; continue }
            val word = words[i]

            when {
                word.startsWith("in:") -> appFilter = word.removePrefix("in:")
                word.startsWith("type:") -> typeFilter = word.removePrefix("type:")
                word == "today" -> timeRange = dayRange(0)
                word == "yesterday" -> timeRange = dayRange(1)
                word == "week" && i > 0 && words[i - 1] == "last" -> {
                    timeRange = weekRange(1)
                    keywords.remove(words[i - 1])
                }
                word == "week" && i > 0 && words[i - 1] == "this" -> {
                    timeRange = weekRange(0)
                    keywords.remove(words[i - 1])
                }
                months.containsKey(word) -> {
                    val year = Calendar.getInstance().get(Calendar.YEAR)
                    timeRange = monthRange(year, months[word]!!)
                }
                word == "last" || word == "this" -> {
                    // part of a phrase, keep as keyword unless consumed above
                    keywords.add(word)
                }
                else -> keywords.add(word)
            }
        }

        return ParsedQuery(
            keywords = keywords,
            appFilter = appFilter,
            typeFilter = typeFilter,
            timeRange = timeRange,
        )
    }

    private fun dayRange(daysAgo: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }

    private fun weekRange(weeksAgo: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.WEEK_OF_YEAR, -weeksAgo)
        val start = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }

    private fun monthRange(year: Int, month: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }
}
