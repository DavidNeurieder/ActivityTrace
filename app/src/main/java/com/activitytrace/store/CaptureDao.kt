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
        appPackage: String? = null,
    ): Flow<List<CapturedItem>> {
        val clauses = mutableListOf("captured_items_fts MATCH ?")
        val params = mutableListOf<Any>(query)

        if (timeRange != null) {
            clauses.add("captured_items.timestamp BETWEEN ? AND ?")
            params.add(timeRange.first)
            params.add(timeRange.second)
        }
        if (contentType != null) {
            clauses.add("captured_items.content_type = ?")
            params.add(contentType)
        }
        if (appPackage != null) {
            clauses.add("captured_items.app_package = ?")
            params.add(appPackage)
        }

        val sql = """
            SELECT captured_items.* FROM captured_items
            JOIN captured_items_fts ON captured_items.rowid = captured_items_fts.rowid
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY captured_items.timestamp DESC LIMIT 100
        """.trimIndent()

        return searchFts(SimpleSQLiteQuery(sql, params.toTypedArray()))
    }

    fun searchLike(
        patterns: List<String>,
        timeRange: Pair<Long, Long>? = null,
        contentType: String? = null,
        appPackage: String? = null,
    ): Flow<List<CapturedItem>> {
        val conditions = patterns.joinToString(" AND ") { "text LIKE ?" }
        val params = mutableListOf<Any>()
        params.addAll(patterns)

        val clauses = mutableListOf<String>()
        if (timeRange != null) {
            clauses.add("timestamp BETWEEN ? AND ?")
            params.add(timeRange.first)
            params.add(timeRange.second)
        }
        if (contentType != null) {
            clauses.add("content_type = ?")
            params.add(contentType)
        }
        if (appPackage != null) {
            clauses.add("app_package = ?")
            params.add(appPackage)
        }

        val whereExtra = if (clauses.isNotEmpty()) " AND ${clauses.joinToString(" AND ")}" else ""

        val sql = """
            SELECT * FROM captured_items
            WHERE $conditions$whereExtra
            ORDER BY timestamp DESC LIMIT 100
        """.trimIndent()

        return searchFts(SimpleSQLiteQuery(sql, params.toTypedArray()))
    }
}
