package com.activitytrace.demo

/**
 * A versioned demo dataset. Datasets are never modified in place: when the
 * showcase content changes, a new id (e.g. `showcase-v2`) is introduced so old
 * screenshots and tests stay reproducible.
 */
enum class DemoDataScenario(
    val datasetId: String,
    val displayName: String,
) {
    SHOWCASE("showcase-v1", "Showcase"),
    SEARCH_BENCHMARK("benchmark-v1", "Search benchmark");

    companion object {
        const val SHOWCASE_DATASET_ID = "showcase-v1"
        const val BENCHMARK_DATASET_ID = "benchmark-v1"
    }
}