package com.activitytrace.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM captured_items WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 100")
    fun search(query: String): Flow<List<CapturedItem>>
}
