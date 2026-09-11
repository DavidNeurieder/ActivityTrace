package com.activitytrace.demo

import java.time.Duration
import java.time.Instant

/**
 * Deterministic, wall-clock-free clock for the demo universe. All event
 * timestamps are absolute instants relative to the fixed [start] anchor — never
 * to `Instant.now()` — so screenshots, ordering and search ranking are stable
 * across runs and devices.
 */
object DemoClock {

    /** Day 1 starts 06:30 local-ish (Z) on a Tuesday. */
    val start: Instant = Instant.parse("2026-09-08T06:30:00Z")

    /** The demo spans exactly three days. */
    val DAYS: Int = 3

    private val DAY = Duration.ofDays(1)

    /**
     * Absolute instant for [dayOffset] (0 = day 1) at [hour]:[minute].
     */
    fun at(dayOffset: Int, hour: Int, minute: Int): Instant =
        start.plus(DAY.multipliedBy(dayOffset.toLong()))
            .plusSeconds(hour * 3600L + minute * 60L)

    /** The latest acceptable timestamp inside the demo window (day 3, 23:59). */
    val endExclusive: Instant = at(DAYS, 0, 0)

    fun withinWindow(instant: Instant): Boolean =
        !instant.isBefore(start) && instant.isBefore(endExclusive)
}