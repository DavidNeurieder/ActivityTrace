package com.activitytrace.demo

import java.time.Duration
import java.util.Random

/**
 * Deterministic source of demo fixtures for the dev-only search benchmark.
 * `SHOWCASE` content lives in [DemoStories], [DemoDocuments] and
 * [DemoBackgroundActivity] (see [DemoDataGenerator.showcaseDataset]); this
 * factory only produces the large synthetic corpus that is used to exercise
 * ranking and performance.
 *
 * The benchmark is generated from a fixed seed so both content and ordering are
 * reproducible. Timestamps are always expressed relative to the reference time —
 * nothing in this file consults the wall clock.
 */
object DemoRecordFactory {

    private fun minutes(m: Int) = Duration.ofMinutes(m.toLong())
    private fun hours(h: Int) = Duration.ofHours(h.toLong())
    private fun days(d: Int) = Duration.ofDays(d.toLong())

    fun recordsFor(
        scenario: DemoDataScenario,
        config: DemoDataConfig,
    ): List<DemoRecord> {
        require(scenario == DemoDataScenario.SEARCH_BENCHMARK) {
            "DemoRecordFactory only produces the search benchmark; " +
                "the showcase is assembled by DemoDataGenerator.showcaseDataset()"
        }
        return benchmarkRecords(
            count = 1_000,
            seed = config.seed,
        )
    }

    /** Large deterministic dataset for ranking/performance testing. */
    fun benchmarkRecords(
        count: Int = 1_000,
        seed: Long = 12345L,
    ): List<DemoRecord> {
        require(count >= 2) { "benchmark count must be at least 2" }
        val random = Random(seed)
        // The word banks are small so terms recur and produce a realistic BM25
        // distribution instead of a grep of unique tokens.
        val pronouns = listOf("my", "the", "a", "our", "their", "your")
        val subjects = listOf("kite", "lighthouse", "runnel", "cedar", "comet", "breeze", "quartz", "meadow")
        val verbs = listOf("flies", "stands", "reflects", "grows", "trails", "warms", "rings", "folds")
        val objects = listOf("lantern", "harbor", "stone wall", "meadow path", "northern sky", "orchard fence", "station roof", "river bank")
        val times = listOf("at dawn", "before noon", "after drizzle", "each evening", "by the weekend", "under a clear sky")

        val apps = listOf(
            Triple("gmail", "gmail", "notification"),
            Triple("signal", "signal", "notification"),
            Triple("whatsapp", "whatsapp", "notification"),
            Triple("chrome", "chrome", "screen"),
            Triple("calendar", "calendar", "notification"),
            Triple("maps", "maps", "screen"),
            Triple("files", "files", "page"),
        )

        val results = ArrayList<DemoRecord>(count)

        // A dedicated cluster that saturates the 200-row BM25 candidate pool:
        // every record repeats the engineered term three times, so searching
        // for it returns 200+ strong candidates from "today" through old age.
        // One weak but fresh match at the end must still surface through the
        // recent-candidate pool (candidate-cutoff protection, demo §25).
        var clusterIndex = 0
        val clusterSize = if (count >= 400) 210 else (count / 2).coerceAtLeast(2)
        while (clusterIndex < clusterSize && results.size < count - 1) {
            val isWeakFreshMatch = clusterIndex == clusterSize - 1
            val age = if (isWeakFreshMatch) {
                Duration.ZERO // the "moderately weaker match from today"
            } else {
                days(clusterIndex % 89)
            }
            // Strong candidates repeat the engineered term twice; the weak but
            // fresh match only once, so it loses the BM25 top-200 race and must
            // be rescued by the recent-candidate pool (demo §25).
            val text = if (isWeakFreshMatch) {
                "zenith sonic blueprint morning note — cluster status update (record ${clusterIndex + 1})"
            } else {
                "zenith sonic blueprint repeated twice zenith sonic blueprint cluster record number ${clusterIndex + 1}"
            }
            results += DemoRecord(
                appName = "gmail",
                appPackage = "gmail",
                contentType = "notification",
                offset = age,
                text = text,
            )
            clusterIndex++
        }

        while (results.size < count) {
            val (displayName, pkg, type) = apps[random.nextInt(apps.size)]
            val text = buildString {
                append(pronouns[random.nextInt(pronouns.size)])
                append(' ')
                append(subjects[random.nextInt(subjects.size)])
                append(' ')
                append(verbs[random.nextInt(verbs.size)])
                append(" past the ")
                append(objects[random.nextInt(objects.size)])
                append(' ')
                append(times[random.nextInt(times.size)])
                append(" (fixture #")
                append(results.size + 1)
                append(')')
            }
            val age = when (random.nextInt(10)) {
                0 -> Duration.ZERO
                1 -> hours(1 + random.nextInt(23))
                2 -> days(1)
                3, 4 -> days(2 + random.nextInt(6))      // 2-7 days
                5 -> days(8 + random.nextInt(7))          // 8-14 days
                6 -> days(15 + random.nextInt(16))        // 15-30 days
                7 -> days(31 + random.nextInt(30))        // 31-60 days
                else -> days(61 + random.nextInt(29))     // 61-89 days
            }
            results += DemoRecord(
                appName = displayName,
                appPackage = pkg,
                contentType = type,
                offset = age,
                text = text,
            )
        }
        return results
    }
}