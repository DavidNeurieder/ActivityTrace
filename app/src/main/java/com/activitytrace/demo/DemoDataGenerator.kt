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

    private suspend fun replace(scenario: DemoDataScenario): Int {
        captureDao.deleteByDemoDatasetId(scenario.datasetId)
        val records = DemoRecordFactory.recordsFor(scenario, config)
        captureDao.insertAll(records.map { it.toCapturedItem(config.referenceTime, scenario.datasetId) })
        return records.size
    }
}