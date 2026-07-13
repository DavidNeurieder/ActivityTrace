package com.activitytrace.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.activitytrace.model.BlockedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: BlockedApp)

    @Query("DELETE FROM blocked_apps WHERE app_package = :appPackage")
    suspend fun delete(appPackage: String)

    @Query("SELECT app_package FROM blocked_apps")
    suspend fun getAllBlocked(): List<String>

    @Query("SELECT COUNT(*) FROM blocked_apps WHERE app_package = :appPackage")
    suspend fun isBlocked(appPackage: String): Int

    @Query("SELECT app_package FROM blocked_apps ORDER BY app_package")
    fun blockedAppsFlow(): Flow<List<BlockedApp>>
}
