package com.activitytrace.demo

import java.time.Instant

/**
 * Configuration for demo data generation. The generator must produce identical
 * content and timestamps for the same [seed] and [referenceTime]; both are
 * pinned by the caller (the UI uses `Instant.now()`, screenshots use a fixed
 * instant).
 */
data class DemoDataConfig(
    val seed: Long = 12345L,
    val referenceTime: Instant = Instant.now(),
) {
    companion object {
        /**
         * A pinned reference time for fully reproducible screenshots and
         * regression fixtures. "Today" in the showcase is relative to this
         * instant.
         */
        val SCREENSHOT_REFERENCE_TIME: Instant = Instant.parse("2026-03-10T18:00:00Z")
    }
}