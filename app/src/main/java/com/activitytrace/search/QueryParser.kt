package com.activitytrace.search

import java.time.Month
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

data class ParsedQuery(
    val keywords: List<String>,
    val appFilter: String? = null,
    val typeFilter: String? = null,
    val timeRange: Pair<Long, Long>? = null,
)

object QueryParser {
    private val contentTypeSynonyms = mapOf(
        "notification" to "notification", "notif" to "notification",
        "notifications" to "notification",
        "screen" to "screen", "screens" to "screen",
        "accessibility" to "screen", "access" to "screen",
        "page" to "page", "pages" to "page",
        "folder" to "page", "folders" to "page",
        "document" to "page", "documents" to "page", "file" to "page", "files" to "page",
    )

    private val months: Map<String, Int> = run {
        val map = mutableMapOf<String, Int>()
        val locale = Locale.getDefault()
        for (m in Month.entries) {
            map[m.getDisplayName(TextStyle.FULL, locale).lowercase(Locale.ROOT)] = m.value - 1
            map[m.getDisplayName(TextStyle.SHORT, locale).lowercase(Locale.ROOT)] = m.value - 1
        }
        for (m in Month.entries) {
            map[m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT)] = m.value - 1
            map[m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase(Locale.ROOT)] = m.value - 1
        }
        map.toMap()
    }

    fun parse(input: String): ParsedQuery {
        if (input.isBlank()) return ParsedQuery(keywords = emptyList())
        val lower = input.lowercase(Locale.ROOT)
        val words = lower.split(Regex("\\s+")).filter { it.isNotEmpty() }

        val keywords = mutableListOf<String>()
        var appFilter: String? = null
        var typeFilter: String? = null
        var timeRange: Pair<Long, Long>? = null
        val skipIndices = mutableSetOf<Int>()

        for (i in words.indices) {
            if (i in skipIndices) continue
            val word = words[i]

            when {
                word.startsWith("in:") -> {
                    val value = word.removePrefix("in:")
                    if (value.isNotBlank()) appFilter = value
                }
                word.startsWith("type:") -> {
                    var value = word.removePrefix("type:")
                    if (value.isNotBlank()) {
                        value = contentTypeSynonyms[value] ?: value
                        typeFilter = value
                    }
                }
                word == "today" -> timeRange = dayRange(0)
                word == "yesterday" -> timeRange = dayRange(1)
                word == "last" && i + 1 < words.size && words[i + 1] == "week" -> {
                    timeRange = weekRange(1)
                    skipIndices.add(i + 1)
                }
                word == "this" && i + 1 < words.size && words[i + 1] == "week" -> {
                    timeRange = weekRange(0)
                    skipIndices.add(i + 1)
                }
                months.containsKey(word) -> {
                    if (i + 1 < words.size) {
                        val candidate = words[i + 1].removeSuffix(",")
                        val d = candidate.toIntOrNull()
                        if (d != null && d in 1..31) {
                            var year = Calendar.getInstance().get(Calendar.YEAR)
                            skipIndices.add(i + 1)
                            if (i + 2 < words.size) {
                                val y = words[i + 2].toIntOrNull()
                                if (y != null && y in 2000..2100) {
                                    year = y
                                    skipIndices.add(i + 2)
                                }
                            }
                            timeRange = singleDayRange(year, months[word]!!, d)
                        } else {
                            keywords.add(word)
                        }
                    } else {
                        keywords.add(word)
                    }
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

    private fun singleDayRange(year: Int, month: Int, day: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }
}
