package com.activitytrace.store

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
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

    @Update
    suspend fun update(item: CapturedItem)

    @Query("SELECT COUNT(*) FROM captured_items WHERE metadata = :metadata")
    suspend fun countByMetadata(metadata: String): Int

    @Query("SELECT * FROM captured_items ORDER BY timestamp DESC LIMIT 100")
    fun recentItems(): Flow<List<CapturedItem>>

    @Query("SELECT * FROM captured_items WHERE content_type = :contentType ORDER BY timestamp DESC LIMIT 100")
    fun recentItemsFiltered(contentType: String): Flow<List<CapturedItem>>

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

    @Query("DELETE FROM captured_items WHERE timestamp < :cutoff AND is_bookmarked = 0")
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

    @Query("UPDATE captured_items SET is_bookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    @Query("SELECT * FROM captured_items WHERE is_bookmarked = 1 ORDER BY timestamp DESC LIMIT 100")
    fun bookmarkedItems(): Flow<List<CapturedItem>>

    @Query("SELECT app_package, COUNT(*) as count FROM captured_items GROUP BY app_package ORDER BY count DESC LIMIT 15")
    suspend fun topApps(): List<AppCount>

    @Query("SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as date, COUNT(*) as count FROM captured_items GROUP BY date ORDER BY date DESC LIMIT 30")
    suspend fun dailyCounts(): List<DateCount>

    @Query("SELECT COUNT(*) FROM captured_items")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM captured_items WHERE timestamp > :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT app_package, COUNT(*) as count FROM captured_items WHERE (:contentType IS NULL OR content_type = :contentType) GROUP BY app_package ORDER BY count DESC LIMIT 15")
    suspend fun topApps(contentType: String?): List<AppCount>

    @Query("SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as date, COUNT(*) as count FROM captured_items WHERE (:contentType IS NULL OR content_type = :contentType) GROUP BY date ORDER BY date DESC LIMIT 7")
    suspend fun dailyCounts(contentType: String?): List<DateCount>

    @Query("SELECT COUNT(*) FROM captured_items WHERE (:contentType IS NULL OR content_type = :contentType)")
    suspend fun totalCount(contentType: String?): Int

    @Query("SELECT COUNT(*) FROM captured_items WHERE timestamp > :since AND (:contentType IS NULL OR content_type = :contentType)")
    suspend fun countSince(since: Long, contentType: String?): Int

    @Query("SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as date, COUNT(*) as count FROM captured_items WHERE app_package = :appPackage AND (:contentType IS NULL OR content_type = :contentType) GROUP BY date ORDER BY date DESC LIMIT 7")
    suspend fun dailyCountsByApp(appPackage: String, contentType: String?): List<DateCount>

    @Query("SELECT COUNT(*) FROM captured_items WHERE app_package = :appPackage AND (:contentType IS NULL OR content_type = :contentType)")
    suspend fun totalCountByApp(appPackage: String, contentType: String?): Int

    @Query("SELECT COUNT(*) FROM captured_items WHERE app_package = :appPackage AND timestamp > :since AND (:contentType IS NULL OR content_type = :contentType)")
    suspend fun countSinceByApp(appPackage: String, since: Long, contentType: String?): Int

    @Query("SELECT content_type, COUNT(*) as count FROM captured_items GROUP BY content_type")
    suspend fun contentTypeBreakdown(): List<ContentTypeCount>

    @Query("SELECT content_type, COUNT(*) as count FROM captured_items WHERE app_package = :appPackage GROUP BY content_type")
    suspend fun contentTypeBreakdown(appPackage: String): List<ContentTypeCount>

    @Query("SELECT CAST(strftime('%H', timestamp/1000, 'unixepoch') AS INTEGER) as hour, COUNT(*) as count FROM captured_items WHERE (:contentType IS NULL OR content_type = :contentType) AND (:appPackage IS NULL OR app_package = :appPackage) GROUP BY hour ORDER BY hour")
    suspend fun hourlyCounts(contentType: String?, appPackage: String?): List<HourCount>

    data class AppCount(
        @ColumnInfo(name = "app_package") val appPackage: String,
        val count: Int,
    )

    data class DateCount(
        val date: String,
        val count: Int,
    )

    data class ContentTypeCount(
        @ColumnInfo(name = "content_type") val contentType: String,
        val count: Int,
    )

    data class HourCount(
        val hour: Int,
        val count: Int,
    )
}
