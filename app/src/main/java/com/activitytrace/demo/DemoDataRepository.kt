package com.activitytrace.demo

import android.content.Context
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.flow.Flow

/**
 * Facade between the demo UI and the demo lifecycle. Owns the generator and
 * the status queries, and records when a dataset was last generated in
 * [prefs] so the UI can show "Generated today".
 *
 * The interface exists so Compose UI tests can inject a fake repository and
 * exercise the full generate / regenerate / clear workflow without a database.
 */
interface DemoDataRepository {
    fun recordCount(scenario: DemoDataScenario): Flow<Int>
    suspend fun generate(scenario: DemoDataScenario): Int
    suspend fun regenerate(scenario: DemoDataScenario): Int
    suspend fun clear(scenario: DemoDataScenario): Int

    /**
     * Scoped deletion by canonical dataset id. Unknown ids are a no-op that
     * returns 0, which makes teardown safe even when the dataset was never
     * generated.
     */
    suspend fun clearDemoDataset(datasetId: String): Int

    fun lastGeneratedAt(scenario: DemoDataScenario): Long

    companion object {
        fun getInstance(context: Context): DemoDataRepository {
            val appContext = context.applicationContext
            val database = ActivityTraceDatabase.getInstance(appContext)
            return DemoDataRepositoryImpl(
                captureDao = database.captureDao(),
                database = database,
                prefs = appContext.getSharedPreferences("activity_trace", Context.MODE_PRIVATE),
            )
        }

        /** Test seam: build a repository bound to an isolated database. */
        internal fun create(
            captureDao: CaptureDao,
            database: ActivityTraceDatabase,
            prefs: android.content.SharedPreferences,
            config: DemoDataConfig = DemoDataConfig(),
        ): DemoDataRepository = DemoDataRepositoryImpl(captureDao, database, prefs, config)
    }
}

private class DemoDataRepositoryImpl(
    private val captureDao: CaptureDao,
    private val database: ActivityTraceDatabase,
    private val prefs: android.content.SharedPreferences,
    private val config: DemoDataConfig = DemoDataConfig(),
) : DemoDataRepository {
    private val generator = DemoDataGenerator(captureDao, database, config)

    override fun recordCount(scenario: DemoDataScenario): Flow<Int> =
        captureDao.demoCountFlow(scenario.datasetId)

    override suspend fun generate(scenario: DemoDataScenario): Int {
        val count = generator.generate(scenario)
        recordLastGenerated(scenario, config.referenceTime.toEpochMilli())
        return count
    }

    override suspend fun regenerate(scenario: DemoDataScenario): Int {
        val count = generator.regenerate(scenario)
        recordLastGenerated(scenario, config.referenceTime.toEpochMilli())
        return count
    }

    override suspend fun clear(scenario: DemoDataScenario): Int {
        val removed = generator.clear(scenario)
        prefs.edit().remove(lastGeneratedKey(scenario.datasetId)).apply()
        return removed
    }

    override suspend fun clearDemoDataset(datasetId: String): Int {
        val scenario = DemoDataScenario.forDatasetId(datasetId) ?: return 0
        return clear(scenario)
    }

    override fun lastGeneratedAt(scenario: DemoDataScenario): Long =
        prefs.getLong(lastGeneratedKey(scenario.datasetId), -1L)

    private fun recordLastGenerated(scenario: DemoDataScenario, epochMillis: Long) {
        prefs.edit().putLong(lastGeneratedKey(scenario.datasetId), epochMillis).apply()
    }

    private fun lastGeneratedKey(datasetId: String) = "$LAST_GENERATED_PREFIX$datasetId"

    private companion object {
        const val LAST_GENERATED_PREFIX = "demo_last_generated_"
    }
}