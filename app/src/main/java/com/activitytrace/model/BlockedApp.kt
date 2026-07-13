package com.activitytrace.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey
    @ColumnInfo(name = "app_package")
    val appPackage: String,
)
