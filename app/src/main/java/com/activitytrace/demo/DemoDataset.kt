package com.activitytrace.demo

/**
 * A complete, validated demo universe: the ten apps, the recurring people, and
 * every event produced by the stories and background activity. Built once by
 * [DemoDatasetBuilder] in a fully deterministic way and validated by
 * [DemoDatasetValidator] before insertion.
 */
data class DemoDataset(
    val apps: List<DemoApp>,
    val people: List<DemoPerson>,
    val events: List<DemoEvent>,
)