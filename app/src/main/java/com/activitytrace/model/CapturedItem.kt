package com.activitytrace.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "captured_items",
    indices = [Index(value = ["app_package", "content_type", "text", "timestamp"])],
)
data class CapturedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "app_package")
    val appPackage: String,
    @ColumnInfo(name = "app_name")
    val appName: String? = null,
    @ColumnInfo(name = "content_type")
    val contentType: String,
    val category: String? = null,
    val timestamp: Long,
    val metadata: String? = null,
    @ColumnInfo(name = "is_bookmarked")
    val isBookmarked: Boolean = false,
    @ColumnInfo(name = "image_blob")
    val imageBlob: ByteArray? = null,
)
