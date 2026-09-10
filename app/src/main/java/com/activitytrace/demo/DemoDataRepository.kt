package com.activitytrace.demo

import android.content.Context
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow

/**
 * Facade between the demo UI and the demo lifecycle. Owns the generator and
 * the status queries, and records when a dataset was last generated in
 * [prefs] so the UI can show "Generated today".
 */
class DemoDataRepository(
    private val captureDao: CaptureDao,
    private val database: ActivityTraceDatabase,
    private val prefs: android.content.SharedPreferences,
    private val config: DemoDataConfig = DemoDataConfig(),
) {
    private val generator = DemoDataGenerator(captureDao, database, config)

    fun recordCount(scenario: DemoDataScenario): Flow<Int> =
        captureDao.demoCountFlow(scenario.datasetId)

    suspend fun generate(scenario: DemoDataScenario): Int {
        val count = generator.generate(scenario)
        prefs.edit().putLong(LAST_GENERATED_PREFIX + scenario.datasetId, config.referenceTime.toEpochMilli())
            .apply()
        return count
    }

    suspend fun regenerate(scenario: DemoDataScenario): Int {
        val count = generator.regenerate(scenario)
        prefs.edit().putLong(LAST_GENERATED_PREFIX + scenario.datasetId, config.referenceTime.toEpochMilli())
            .apply()
        return count
    }

    suspend fun clear(scenario: DemoDataScenario): Int {
        val removed = generator.clear(scenario)
        prefs.edit().remove(LAST_GENERATED_PREFIX + scenario.datasetId).apply()
        return removed
    }

    fun lastGeneratedAt(scenario: DemoDataScenario): Long =
        prefs.getLong(LAST_GENERATED_PREFIX + scenario.datasetId, -1L)

    companion object {
        private const val LAST_GENERATED_PREFIX = "demo_last_generated_"

        fun getInstance(context: Context): DemoDataRepository {
            val appContext = context.applicationContext
            val database = ActivityTraceDatabase.getInstance(appContext)
            return DemoDataRepository(
                captureDao = database.captureDao(),
                database = database,
                prefs = appContext.getSharedPreferences("activity_trace", Context.MODE_PRIVATE),
            )
        }
    }
}