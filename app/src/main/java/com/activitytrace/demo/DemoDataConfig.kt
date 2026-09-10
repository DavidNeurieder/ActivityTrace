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
         * The pinned "now" used by the deterministic screenshot environment.
         * Screenshots always generate `showcase-v1` against this instant — the
         * showcase "today" is relative to it and never to `Instant.now()`.
         */
        val SCREENSHOT_REFERENCE_TIME: Instant = Instant.parse("2026-09-01T12:00:00Z")
    }
}