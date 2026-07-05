package com.activitytrace.store

import androidx.room.ColumnInfo
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

    @Insert
    suspend fun insertAll(items: List<CapturedItem>)

    @Query("SELECT text, timestamp, app_package FROM captured_items")
    suspend fun getAllItemKeys(): List<ItemKey>

    data class ItemKey(
        val text: String,
        val timestamp: Long,
        @ColumnInfo(name = "app_package") val appPackage: String,
    )

    @Query("SELECT * FROM captured_items ORDER BY timestamp DESC")
    suspend fun getAllItems(): List<CapturedItem>

    @Query("DELETE FROM captured_items WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @RawQuery(observedEntities = [CapturedItem::class])
    fun searchLikeRaw(query: SupportSQLiteQuery): Flow<List<CapturedItem>>

    fun searchLike(
        patterns: List<String>,
        timeRange: Pair<Long, Long>? = null,
        contentType: String? = null,
        appPackage: String? = null,
    ): Flow<List<CapturedItem>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (patterns.isNotEmpty()) {
            val textConditions = patterns.joinToString(" AND ") { "(text LIKE ? OR app_name LIKE ?)" }
            conditions.add("($textConditions)")
            patterns.forEach { p ->
                params.add(p)
                params.add(p)
            }
        }

        if (timeRange != null) {
            conditions.add("timestamp BETWEEN ? AND ?")
            params.add(timeRange.first)
            params.add(timeRange.second)
        }
        if (contentType != null) {
            conditions.add("content_type LIKE ?")
            params.add("%$contentType%")
        }
        if (appPackage != null) {
            conditions.add("(app_package LIKE ? OR app_name LIKE ?)")
            params.add("%$appPackage%")
            params.add("%$appPackage%")
        }

        val whereClause = if (conditions.isNotEmpty()) " WHERE ${conditions.joinToString(" AND ")}" else ""

        val sql = """
            SELECT * FROM captured_items$whereClause
            ORDER BY timestamp DESC LIMIT 100
        """.trimIndent()

        return searchLikeRaw(SimpleSQLiteQuery(sql, params.toTypedArray()))
    }
}
