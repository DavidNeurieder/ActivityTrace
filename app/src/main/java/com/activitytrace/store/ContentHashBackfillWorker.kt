package com.activitytrace.store

import android.content.Context
import android.util.Log
import android.database.sqlite.SQLiteConstraintException
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class ContentHashBackfillWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dao = ActivityTraceDatabase.getInstance(applicationContext).captureDao()
            backfillContentHashes(dao)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Content hash backfill failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ContentHashBackfill"
        private const val WORK_NAME = "content_hash_backfill"
        private const val BATCH_SIZE = 200
        private const val MAX_BATCHES_PER_RUN = 500

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ContentHashBackfillWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        internal suspend fun backfillContentHashes(dao: CaptureDao): Int {
            var backfilled = 0
            var batches = 0
            while (batches < MAX_BATCHES_PER_RUN) {
                val batch = dao.getNullHashBatch(BATCH_SIZE)
                if (batch.isEmpty()) break
                var updatedInBatch = 0
                for (row in batch) {
                    val hash = ContentHasher.hash(row.appPackage, row.contentType, row.text)
                    updatedInBatch += try {
                        dao.setContentHash(row.id, hash)
                    } catch (_: SQLiteConstraintException) {
                        0
                    }
                }
                backfilled += updatedInBatch
                if (updatedInBatch == 0) break
                batches++
            }
            return backfilled
        }
    }
}