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
import com.activitytrace.search.SearchCandidate
import com.activitytrace.search.SearchConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CapturedItem): Long

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CapturedItem>): List<Long>

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

    @Query("DELETE FROM captured_items WHERE app_package = :appPackage")
    suspend fun deleteByAppPackage(appPackage: String)

    @RawQuery(observedEntities = [CapturedItem::class])
    fun searchLikeRaw(query: SupportSQLiteQuery): Flow<List<CapturedItem>>

    @RawQuery(observedEntities = [CapturedItem::class])
    fun searchCandidatesRaw(query: SupportSQLiteQuery): Flow<List<SearchCandidate>>

    /**
     * Retrieves the best [limit] BM25 candidates for an FTS5 [matchQuery].
     *
     * Candidates come back ordered by the weighted BM25 score
     * (`bm25(text=1.0, app_name=2.0)`), lowest first — that is the raw
     * relevance signal for [SearchRanker]. Recency based re-ranking happens
     * after this DAO call. The weights live in [SearchConfig], which must stay
     * consistent between this query and the rest of the pipeline.
     */
    fun searchFtsCandidates(
        matchQuery: String,
        timeRange: Pair<Long, Long>? = null,
        contentType: String? = null,
        appPackage: String? = null,
        limit: Long = SearchConfig.BM25_CANDIDATE_LIMIT.toLong(),
    ): Flow<List<SearchCandidate>> {
        val (whereClause, params) = ftsWhere(matchQuery, timeRange, contentType, appPackage)
        params.add(limit)

        val sql = """
            $FTS_CANDIDATE_SELECT
            $whereClause
            ORDER BY bm25_score
            LIMIT ?
        """.trimIndent()

        return searchCandidatesRaw(SimpleSQLiteQuery(sql, params.toTypedArray()))
    }

    /**
     * Retrieves the [limit] most recent records matching the same FTS5
     * [matchQuery] and filters as the BM25 pool. This second pool guarantees a
     * freshly captured item that ranks outside the BM25 top-N can still
     * participate in ranking.
     */
    fun searchFtsRecentCandidates(
        matchQuery: String,
        timeRange: Pair<Long, Long>? = null,
        contentType: String? = null,
        appPackage: String? = null,
        limit: Long = SearchConfig.RECENT_CANDIDATE_LIMIT.toLong(),
    ): Flow<List<SearchCandidate>> {
        val (whereClause, params) = ftsWhere(matchQuery, timeRange, contentType, appPackage)
        params.add(limit)

        val sql = """
            $FTS_CANDIDATE_SELECT
            $whereClause
            ORDER BY captured_items.timestamp DESC
            LIMIT ?
        """.trimIndent()

        return searchCandidatesRaw(SimpleSQLiteQuery(sql, params.toTypedArray()))
    }

    private fun ftsWhere(
        matchQuery: String,
        timeRange: Pair<Long, Long>?,
        contentType: String?,
        appPackage: String?,
    ): Pair<String, MutableList<Any>> {
        val conditions = mutableListOf<String>()
        conditions.add("captured_items_fts MATCH ?")
        val params = mutableListOf<Any>(matchQuery)

        if (timeRange != null) {
            conditions.add("captured_items.timestamp BETWEEN ? AND ?")
            params.add(timeRange.first)
            params.add(timeRange.second)
        }
        if (contentType != null) {
            conditions.add("captured_items.content_type LIKE ?")
            params.add("%$contentType%")
        }
        if (appPackage != null) {
            conditions.add("(captured_items.app_package LIKE ? OR captured_items.app_name LIKE ?)")
            params.add("%$appPackage%")
            params.add("%$appPackage%")
        }

        return " WHERE ${conditions.joinToString(" AND ")}" to params
    }

    companion object {
        private val FTS_CANDIDATE_SELECT =
            """
            SELECT captured_items.*, bm25(captured_items_fts, ${SearchConfig.FTS_TEXT_WEIGHT}, ${SearchConfig.FTS_APP_NAME_WEIGHT}) AS bm25_score
            FROM captured_items
            JOIN captured_items_fts ON captured_items.id = captured_items_fts.rowid
            """.trimIndent()
    }

    fun searchLike(
        patterns: List<String>,
        timeRange: Pair<Long, Long>? = null,
        contentType: String? = null,
        appPackage: String? = null,
    ): Flow<List<CapturedItem>> {
        return searchLikePaged(patterns, timeRange, contentType, appPackage, limit = Long.MAX_VALUE, offset = 0)
    }

    fun searchLikePaged(
        patterns: List<String>,
        timeRange: Pair<Long, Long>? = null,
        contentType: String? = null,
        appPackage: String? = null,
        limit: Long,
        offset: Long,
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
        params.add(limit)
        params.add(offset)

        val sql = """
            SELECT * FROM captured_items$whereClause
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
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

    @Query("SELECT id, text, app_package, content_type FROM captured_items WHERE content_hash IS NULL ORDER BY id LIMIT :limit")
    suspend fun getNullHashBatch(limit: Int): List<HashBatchRow>

    @Query("UPDATE captured_items SET content_hash = :hash WHERE id = :id AND content_hash IS NULL")
    suspend fun setContentHash(id: Long, hash: String): Int

    data class HashBatchRow(
        val id: Long,
        val text: String,
        @ColumnInfo(name = "app_package") val appPackage: String,
        @ColumnInfo(name = "content_type") val contentType: String,
    )

    @Query("SELECT app_package, COUNT(*) as count FROM captured_items WHERE (:contentType IS NULL OR content_type = :contentType) GROUP BY app_package ORDER BY count DESC LIMIT 15")
    suspend fun topApps(contentType: String?): List<AppCount>

    @Query("SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as date, COUNT(*) as count FROM captured_items WHERE (:contentType IS NULL OR content_type = :contentType) GROUP BY date ORDER BY date DESC LIMIT :limit")
    suspend fun dailyCounts(contentType: String?, limit: Int = 7): List<DateCount>

    @Query("SELECT COUNT(*) FROM captured_items WHERE (:contentType IS NULL OR content_type = :contentType)")
    suspend fun totalCount(contentType: String?): Int

    @Query("SELECT COUNT(*) FROM captured_items WHERE timestamp > :since AND (:contentType IS NULL OR content_type = :contentType)")
    suspend fun countSince(since: Long, contentType: String?): Int

    @Query("SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as date, COUNT(*) as count FROM captured_items WHERE app_package = :appPackage AND (:contentType IS NULL OR content_type = :contentType) GROUP BY date ORDER BY date DESC LIMIT :limit")
    suspend fun dailyCountsByApp(appPackage: String, contentType: String?, limit: Int = 7): List<DateCount>

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

    @Query("SELECT CAST(strftime('%w', timestamp/1000, 'unixepoch') AS INTEGER) as dow, COUNT(*) as count FROM captured_items WHERE (:contentType IS NULL OR content_type = :contentType) AND (:appPackage IS NULL OR app_package = :appPackage) GROUP BY dow ORDER BY dow")
    suspend fun dayOfWeekCounts(contentType: String?, appPackage: String?): List<DayOfWeekCount>

    @Query("SELECT COUNT(*) FROM captured_items WHERE timestamp BETWEEN :start AND :end AND (:contentType IS NULL OR content_type = :contentType)")
    suspend fun countBetween(start: Long, end: Long, contentType: String?): Int

    @Query("SELECT COUNT(*) FROM captured_items WHERE app_package = :appPackage AND timestamp BETWEEN :start AND :end AND (:contentType IS NULL OR content_type = :contentType)")
    suspend fun countBetweenByApp(appPackage: String, start: Long, end: Long, contentType: String?): Int

    @Query("SELECT COUNT(*) FROM captured_items WHERE is_bookmarked = 1")
    suspend fun bookmarkedCount(): Int

    @Query("SELECT COUNT(*) FROM captured_items WHERE is_bookmarked = 1 AND timestamp > :since")
    suspend fun bookmarkedCountSince(since: Long): Int

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

    data class DayOfWeekCount(
        val dow: Int,
        val count: Int,
    )
}
