package com.activitytrace.store

import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Outcome of an encrypted restore attempt. Backups are an untrusted input
 * format: anything a malicious or corrupted file can cause surfaces as
 * [InvalidBackup], and the live database is never written on that path.
 */
sealed class RestoreResult {
    /** Restore merged [importedCount] new items into the existing database. */
    data class Success(val importedCount: Int) : RestoreResult()

    /** The selected file is not a valid/usable ActivityTrace backup. */
    data class InvalidBackup(val reason: String) : RestoreResult()

    /** The restore could not be attempted/completed for a non-format reason. */
    data class Failed(val reason: String, val cause: Throwable? = null) : RestoreResult()

    companion object {
        fun classify(reason: String, throwable: Throwable): RestoreResult =
            when (throwable) {
                is BackupTooLargeException,
                is GeneralSecurityException,
                is IllegalArgumentException,
                -> InvalidBackup(reason)

                is android.database.sqlite.SQLiteException ->
                    InvalidBackup("Backup contains an unreadable database")

                is IOException -> Failed(reason, throwable)

                else -> Failed(reason, throwable)
            }
    }
}