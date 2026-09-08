package com.activitytrace.model

import androidx.room.ColumnInfo

/**
 * Row shape of the external-content FTS5 index `captured_items_fts`.
 *
 * The virtual table is created and kept in sync via raw SQL in
 * `MIGRATION_8_9` (triggers on `captured_items`) rather than a Room
 * `@Fts5` entity, because Room's KSP processor cannot resolve the
 * `contentEntity` reference for FTS5 under the pinned Room version
 * (see AGENTS.md: "FTS5 table created via Room callback, not Room
 * annotation, due to KSP resolution order").
 */
@Suppress("unused")
data class CapturedItemFts(
    val text: String,
    @ColumnInfo(name = "app_name") val appName: String?,
)
