package com.activitytrace.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CapturedItem)

    @Query("SELECT * FROM captured_items ORDER BY timestamp DESC LIMIT 100")
    fun recentItems(): Flow<List<CapturedItem>>

    @Query("SELECT * FROM captured_items WHERE timestamp > :since ORDER BY timestamp DESC")
    fun itemsSince(since: Long): Flow<List<CapturedItem>>

    @Query("DELETE FROM captured_items WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @RawQuery(observedEntities = [CapturedItem::class])
    fun searchFts(query: SupportSQLiteQuery): Flow<List<CapturedItem>>

    fun search(
        query: String,
        timeRange: Pair<Long, Long>? = null,
    ): Flow<List<CapturedItem>> {
        val sql = if (timeRange != null) {
            SimpleSQLiteQuery(
                """
                SELECT captured_items.* FROM captured_items
                JOIN captured_items_fts ON captured_items.rowid = captured_items_fts.rowid
                WHERE captured_items_fts MATCH ?
                AND captured_items.timestamp BETWEEN ? AND ?
                ORDER BY captured_items.timestamp DESC LIMIT 100
                """.trimIndent(),
                arrayOf(query, timeRange.first, timeRange.second)
            )
        } else {
            SimpleSQLiteQuery(
                """
                SELECT captured_items.* FROM captured_items
                JOIN captured_items_fts ON captured_items.rowid = captured_items_fts.rowid
                WHERE captured_items_fts MATCH ?
                ORDER BY captured_items.timestamp DESC LIMIT 100
                """.trimIndent(),
                arrayOf(query)
            )
        }
        return searchFts(sql)
    }

    fun searchLike(
        patterns: List<String>,
        timeRange: Pair<Long, Long>? = null,
    ): Flow<List<CapturedItem>> {
        val conditions = patterns.joinToString(" AND ") { "text LIKE ?" }
        val params: Array<Any?> = patterns.toTypedArray()
        val args: Array<Any?> = if (timeRange != null) {
            params + timeRange.first + timeRange.second
        } else {
            params
        }
        val sql = if (timeRange != null) {
            SimpleSQLiteQuery(
                """
                SELECT * FROM captured_items
                WHERE $conditions
                AND timestamp BETWEEN ? AND ?
                ORDER BY timestamp DESC LIMIT 100
                """.trimIndent(),
                args,
            )
        } else {
            SimpleSQLiteQuery(
                """
                SELECT * FROM captured_items
                WHERE $conditions
                ORDER BY timestamp DESC LIMIT 100
                """.trimIndent(),
                args,
            )
        }
        return searchFts(sql)
    }
}
