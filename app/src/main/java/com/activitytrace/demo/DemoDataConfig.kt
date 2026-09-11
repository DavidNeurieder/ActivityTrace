package com.activitytrace.demo

import java.time.Instant

/**
 * Configuration for demo data generation. The showcase occupies a fully
 * deterministic window anchored at [DemoClock.start] and never consults the
 * wall clock. [seed] and [referenceTime] only affect the dev-only search
 * benchmark, which is expressed as offsets from a reference instant.
 */
data class DemoDataConfig(
    val seed: Long = 12345L,
    val referenceTime: Instant = Instant.now(),
)