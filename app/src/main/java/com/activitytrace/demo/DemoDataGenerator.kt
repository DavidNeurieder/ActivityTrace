package com.activitytrace.demo

import androidx.room.withTransaction
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.CaptureDao

/**
 * Transactional lifecycle for demo datasets.
 *
 * `generate` is a single database transaction: existing records of the dataset
 * are removed first, then every fixture is inserted, including its FTS5 entry
 * (via the `captured_items_fts_ai` trigger). If anything fails the whole
 * dataset rolls back — the user never sees half a dataset.
 *
 * The showcase is content from [DemoDocuments], [DemoStories] and
 * [DemoBackgroundActivity], validated by [DemoDatasetValidator] before insert.
 * The benchmark is generated ad hoc from [DemoRecordFactory].
 */
class DemoDataGenerator(
    private val captureDao: CaptureDao,
    private val database: ActivityTraceDatabase,
    private val config: DemoDataConfig = DemoDataConfig(),
) {

    /**
     * Replaces the dataset with a fresh deterministic copy and returns the
     * number of inserted records. Idempotent: generating twice never doubles
     * the dataset because the dataset is cleared inside the transaction.
     */
    suspend fun generate(scenario: DemoDataScenario): Int = database.withTransaction {
        replace(scenario)
    }

    /** Removes every record of [scenario]; returns the number removed. */
    suspend fun clear(scenario: DemoDataScenario): Int =
        captureDao.deleteByDemoDatasetId(scenario.datasetId)

    /** Clear-then-generate in one transaction (== [generate]). */
    suspend fun regenerate(scenario: DemoDataScenario): Int = database.withTransaction {
        replace(scenario)
    }

    /**
     * The deterministic showcase content. Pure function, no I/O — unit-testable
     * and shared with the validator so generation and its guarantee never drift.
     */
    fun showcaseDataset(): DemoDataset {
        val events = (
            DemoDocuments.events() +
                DemoStories.projectAurora() +
                DemoStories.viennaTrip() +
                DemoStories.parcelIncident() +
                DemoStories.questionableSpending() +
                DemoBackgroundActivity.events()
            ).sortedWith(compareBy({ it.timestamp }, { it.id }))
        return DemoDataset(
            apps = DemoAppCatalog.all,
            people = DemoPerson.entries,
            events = events,
        )
    }

    private suspend fun replace(scenario: DemoDataScenario): Int {
        captureDao.deleteByDemoDatasetId(scenario.datasetId)
        when (scenario) {
            DemoDataScenario.SHOWCASE -> {
                val dataset = showcaseDataset()
                val validation = DemoDatasetValidator.validate(dataset)
                check(validation.isValid) {
                    "Showcase dataset invalid: ${validation.errors.joinToString("; ")}"
                }
                captureDao.insertAll(dataset.events.map { it.toCapturedItem(scenario.datasetId) })
                return dataset.events.size
            }

            DemoDataScenario.SEARCH_BENCHMARK -> {
                val records = DemoRecordFactory.recordsFor(scenario, config)
                captureDao.insertAll(records.map { it.toCapturedItem(config.referenceTime, scenario.datasetId) })
                return records.size
            }
        }
    }
}