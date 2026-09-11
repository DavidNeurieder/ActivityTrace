package com.activitytrace.demo

import com.activitytrace.demo.DemoAppId.entries as ALL_APPS
import java.time.Instant

/**
 * Validates the showcase dataset against the plan's hard requirements.
 * Pure function on the in-memory [DemoDataset] — no database involved — so it
 * runs fast in unit tests and before every real insert.
 */
object DemoDatasetValidator {

    data class Result(
        val errors: List<String>,
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    /** Every app in the catalog must be represented, none other. */
    private val REQUIRED_CORPUS_TERMS = listOf(
        "aurora", "project", "invoice", "vienna", "parcel",
        "keyboard", "croissant", "maya", "libre", "curr", "chat", "meet",
    )

    /** Prefixes that ClassicSearch must resolve to the expected app. */
    private val REQUIRED_PREFIX_TERMS = listOf(
        "chat", "chatt", "parc", "parcel", "meet", "libre", "curr", "wand", "snac", "budget",
    )

    /** A term that must NOT exist — guards against cross-app leakage. */
    private val FORBIDDEN_TERMS = listOf("whatsapp")

    private const val MIN_EVENTS = 120
    private const val MAX_EVENTS = 150
    private const val LONG_DOC_WORD_MIN = 400

    fun validate(dataset: DemoDataset): Result {
        val errors = mutableListOf<String>()

        // 1. Exactly the ten catalog apps, no strays, no duplicates.
        val catalogKeys = DemoAppCatalog.all.map { it.id }.toSet()
        val datasetKeys = dataset.apps.map { it.id }.toSet()
        if (datasetKeys != catalogKeys) {
            errors.add("apps keys differ from catalog: missing=${(catalogKeys - datasetKeys).map { it.name }} extra=${(datasetKeys - catalogKeys).map { it.name }}")
        }
        val seenAppIds = mutableSetOf<DemoAppId>()
        for (app in dataset.apps) {
            if (!seenAppIds.add(app.id)) errors.add("duplicate app ${app.id.name}")
            if (app.packageName.isBlank()) errors.add("blank package for ${app.id.name}")
        }
        if (dataset.people.distinct().size != dataset.people.size) {
            errors.add("duplicate people entry")
        }
        val knownTypes = DemoEventType.entries.toSet()
        val appIdSet = ALL_APPS.toSet()

        // 2. Deterministic ids, valid apps/types, window bounds.
        val ids = mutableSetOf<String>()
        var minTs: Instant? = null
        var maxTs: Instant? = null
        val dayBuckets = mutableSetOf<Int>()
        for (event in dataset.events) {
            if (event.text.isBlank()) errors.add("blank text on ${event.id}")
            if (event.app !in appIdSet) errors.add("unknown app ${event.app} on ${event.id}")
            if (event.type !in knownTypes) errors.add("unknown type ${event.type} on ${event.id}")
            if (!ids.add(event.id)) errors.add("duplicate id ${event.id}")
            if (event.text.any { it == '*' }) errors.add("bare star in text on ${event.id}")

            minTs = if (minTs == null || event.timestamp < minTs) event.timestamp else minTs
            maxTs = if (maxTs == null || event.timestamp > maxTs) event.timestamp else maxTs
            dayBuckets += timestampDayIndex(event.timestamp)
        }

        // 3. Size + span.
        if (dataset.events.size !in MIN_EVENTS..MAX_EVENTS) {
            errors.add("expected $MIN_EVENTS..$MAX_EVENTS events, got ${dataset.events.size}")
        }
        if (minTs == null || maxTs == null) {
            errors.add("dataset is empty")
        } else {
            if (minTs < DemoClock.start) errors.add("events before DemoClock.start: $minTs")
            if (maxTs >= DemoClock.endExclusive) errors.add("events at or after window end: $maxTs")
        }
        val expectedDays = (0..2).toSet()
        if (dayBuckets != expectedDays) {
            errors.add("expected days ${expectedDays.map { "d${it + 1}" }} to be covered, got ${dayBuckets.sorted().map { "d${it + 1}" }}")
        }

        // 4. Every catalog app used at least once.
        for (app in ALL_APPS) {
            if (dataset.events.none { it.app == app }) {
                errors.add("app ${app.name} has no events")
            }
        }

        // 5. Corpus terms + prefixes must be searchable.
        val tokenBag = dataset.events.flatMap { tokenize(it.text).keys } +
            dataset.events.flatMap { tokenize(DemoAppCatalog.byId(it.app).name).keys }
        for (term in REQUIRED_CORPUS_TERMS) {
            if (tokenBag.none { it.startsWith(term) }) {
                errors.add("corpus term '$term' not found in any text or app name")
            }
        }
        for (prefix in REQUIRED_PREFIX_TERMS) {
            if (tokenBag.none { it.startsWith(prefix) }) {
                errors.add("no token starts with prefix '$prefix'")
            }
        }
        for (forbidden in FORBIDDEN_TERMS) {
            val hits = forbidden.split(' ').first()
            if (tokenBag.any { it.startsWith(hits) }) {
                errors.add("forbidden term '$forbidden' leaked into corpus")
            }
        }

        // 6. The four stories each leave a fingerprint.
        val allText = dataset.events.joinToString(" ") { it.text }
        mapOf(
            "story:aurora" to "FINAL FINAL FINAL",
            "story:vienna" to "Vienna",
            "story:parcel" to "which neighbor??",
            "story:spending" to "€89.00",
        ).forEach { (story, fingerprint) ->
            if (fingerprint !in allText) errors.add("$story fingerprint '$fingerprint' missing")
        }

        // 7. One document long enough to exercise BM25 doc-length normalization.
        val longestByWords = dataset.events.maxOfOrNull { tokenize(it.text).values.sum() } ?: 0
        if (longestByWords < LONG_DOC_WORD_MIN) {
            errors.add("no document long enough for BM25 ($longestByWords < $LONG_DOC_WORD_MIN words)")
        }

        return Result(errors)
    }

    /**
     * FTS5-unicode61-compatible tokenization: lowercase, strip punctuation,
     * drop 1- and 2-character tokens (including the accented single-letter
     * words the tokenizer would drop anyway).
     */
    private fun tokenize(text: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (token in text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))) {
            if (token.length < 3) continue
            if (token.all { it.isDigit() }) continue
            counts[token] = (counts[token] ?: 0) + 1
        }
        return counts
    }

    private fun timestampDayIndex(ts: Instant): Int =
        ((ts.toEpochMilli() - DemoClock.start.toEpochMilli()) / 86_400_000L).toInt()
}