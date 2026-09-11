package com.activitytrace.demo

/**
 * A versioned demo dataset. Datasets are never modified in place: when the
 * showcase content changes, a new id (e.g. `activitytrace_showcase_v2`) is
 * introduced so old screenshots and tests stay reproducible, and so a reset can
 * clean up after itself by `demoDatasetId` without touching real data.
 */
enum class DemoDataScenario(
    val datasetId: String,
    val displayName: String,
) {
    SHOWCASE("activitytrace_showcase_v1", "Showcase"),
    SEARCH_BENCHMARK("benchmark-v1", "Search benchmark");

    companion object {
        const val SHOWCASE_DATASET_ID = "activitytrace_showcase_v1"
        const val BENCHMARK_DATASET_ID = "benchmark-v1"

        const val DEMO_DATASET_VERSION = 1

        fun forDatasetId(datasetId: String): DemoDataScenario? =
            entries.firstOrNull { it.datasetId == datasetId }

        /** "v1" from "…_v1"; null when the id has no version suffix. */
        fun datasetVersion(datasetId: String): Int? =
            datasetId.removeSuffix(".json")
                .splitToSequence('_', '-')
                .lastOrNull()
                ?.takeIf { it.startsWith('v') }
                ?.removePrefix("v")
                ?.toIntOrNull()
    }
}