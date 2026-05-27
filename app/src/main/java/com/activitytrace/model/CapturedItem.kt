package com.activitytrace.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_items")
data class CapturedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "app_package")
    val appPackage: String,
    @ColumnInfo(name = "content_type")
    val contentType: String,
    val timestamp: Long,
    val metadata: String? = null,
)
