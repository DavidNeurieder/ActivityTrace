package com.activitytrace.store

import android.content.Context
import android.content.SharedPreferences

sealed interface DatabaseOpenResult {
    data class Opened(val database: ActivityTraceDatabase) : DatabaseOpenResult
    data class RecoveryRequired(val reason: RecoveryReason) : DatabaseOpenResult
    data class Failed(val error: Throwable) : DatabaseOpenResult
}

enum class RecoveryReason {
    INVALID_KEY,
    CORRUPTED_DATABASE,
    MIGRATION_FAILURE,
    UNKNOWN
}

class RecoveryStateStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(reason: RecoveryReason) {
        val committed = preferences.edit().putString(KEY_REASON, reason.name).commit()
        check(committed) { "Failed to persist recovery state" }
    }

    fun clear() {
        val committed = preferences.edit().clear().commit()
        check(committed) { "Failed to clear recovery state" }
    }

    fun current(): RecoveryReason? =
        preferences.getString(KEY_REASON, null)?.let { RecoveryReason.valueOf(it) }

    private companion object {
        const val PREFS_NAME = "activity_trace_recovery"
        const val KEY_REASON = "reason"
    }
}

object RecoveryClassifier {
    fun classify(error: Throwable): RecoveryReason? {
        val text = buildString {
            var current: Throwable? = error
            while (current != null) {
                append(current.message).append(' ')
                current = current.cause
            }
        }.lowercase()

        return when {
            "migration didn't properly handle" in text ||
                "cannot verify the data integrity" in text ||
                "expected version doesn't match" in text -> RecoveryReason.MIGRATION_FAILURE

            "file is not a database" in text ||
                "not a database" in text ||
                "file is encrypted" in text -> RecoveryReason.INVALID_KEY

            "database disk image is malformed" in text ||
                "disk image is malformed" in text -> RecoveryReason.CORRUPTED_DATABASE

            else -> null
        }
    }
}

class DatabaseOpener(
    private val database: ActivityTraceDatabase,
    private val stateStore: RecoveryStateStore,
) {
    fun open(): DatabaseOpenResult {
        return try {
            database.openHelper.writableDatabase
            stateStore.clear()
            DatabaseOpenResult.Opened(database)
        } catch (error: Throwable) {
            val reason = RecoveryClassifier.classify(error)
                ?: return DatabaseOpenResult.Failed(error)
            stateStore.record(reason)
            DatabaseOpenResult.RecoveryRequired(reason)
        }
    }
}