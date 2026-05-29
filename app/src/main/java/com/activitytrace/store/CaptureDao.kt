package com.activitytrace.store

import androidx.room.Dao
import androidx.room.Delete
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

    @Delete
    suspend fun delete(item: CapturedItem)

    @Query("DELETE FROM captured_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM captured_items WHERE metadata = :metadata")
    suspend fun countByMetadata(metadata: String): Int

    @Query("SELECT * FROM captured_items ORDER BY timestamp DESC LIMIT 100")
    fun recentItems(): Flow<List<CapturedItem>>

    @Query("SELECT * FROM captured_items WHERE content_type = :contentType ORDER BY timestamp DESC LIMIT 100")
    fun recentItemsFiltered(contentType: String): Flow<List<CapturedItem>>

    @Query("SELECT * FROM captured_items WHERE timestamp > :since ORDER BY timestamp DESC")
    fun itemsSince(since: Long): Flow<List<CapturedItem>>

    @Query("DELETE FROM captured_items WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @RawQuery(observedEntities = [CapturedItem::class])
    fun searchFts(query: SupportSQLiteQuery): Flow<List<CapturedItem>>

    fun search(
        query: String,
        timeRange: Pair<Long, Long>? = null,
        contentType: String? = null,
    ): Flow<List<CapturedItem>> {
        val typeClause = if (contentType != null) "AND captured_items.content_type = ?" else ""
        val params = mutableListOf<Any>(query)
        if (timeRange != null) {
            params.add(timeRange.first)
            params.add(timeRange.second)
        }
        if (contentType != null) params.add(contentType)
        val sql = if (timeRange != null) {
            SimpleSQLiteQuery(
                """
                SELECT captured_items.* FROM captured_items
                JOIN captured_items_fts ON captured_items.rowid = captured_items_fts.rowid
                WHERE captured_items_fts MATCH ?
                AND captured_items.timestamp BETWEEN ? AND ?
                $typeClause
                ORDER BY captured_items.timestamp DESC LIMIT 100
                """.trimIndent(),
                params.toTypedArray(),
            )
        } else {
            SimpleSQLiteQuery(
                """
                SELECT captured_items.* FROM captured_items
                JOIN captured_items_fts ON captured_items.rowid = captured_items_fts.rowid
                WHERE captured_items_fts MATCH ?
                $typeClause
                ORDER BY captured_items.timestamp DESC LIMIT 100
                """.trimIndent(),
                params.toTypedArray(),
            )
        }
        return searchFts(sql)
    }

    fun searchLike(
        patterns: List<String>,
        timeRange: Pair<Long, Long>? = null,
        contentType: String? = null,
    ): Flow<List<CapturedItem>> {
        val conditions = patterns.joinToString(" AND ") { "text LIKE ?" }
        val params = mutableListOf<Any>()
        params.addAll(patterns)
        if (timeRange != null) {
            params.add(timeRange.first)
            params.add(timeRange.second)
        }
        val typeClause = if (contentType != null) {
            params.add(contentType)
            "AND content_type = ?"
        } else ""
        val sql = if (timeRange != null) {
            SimpleSQLiteQuery(
                """
                SELECT * FROM captured_items
                WHERE $conditions
                AND timestamp BETWEEN ? AND ?
                $typeClause
                ORDER BY timestamp DESC LIMIT 100
                """.trimIndent(),
                params.toTypedArray(),
            )
        } else {
            SimpleSQLiteQuery(
                """
                SELECT * FROM captured_items
                WHERE $conditions
                $typeClause
                ORDER BY timestamp DESC LIMIT 100
                """.trimIndent(),
                params.toTypedArray(),
            )
        }
        return searchFts(sql)
    }
}
