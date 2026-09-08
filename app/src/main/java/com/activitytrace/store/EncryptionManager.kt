package com.activitytrace.store

import android.content.Context

/**
 * Provides the database encryption key for SQLCipher.
 *
 * Delegates to [DatabaseKeyStore] which manages a random 256-bit key
 * wrapped by an Android Keystore AES-GCM key. The Keystore key itself
 * is never exported (`.encoded` returns null — this is correct and expected).
 *
 * Legacy fallback keys stored in SharedPreferences are automatically
 * migrated on first access.
 */
object EncryptionManager {

    @Volatile
    private var keyStore: DatabaseKeyStore? = null

    fun getOrCreateKey(context: Context): ByteArray {
        return getKeyStore(context).getDatabaseKey()
    }

    private fun getKeyStore(context: Context): DatabaseKeyStore {
        val existing = keyStore
        if (existing != null) return existing
        return synchronized(this) {
            keyStore ?: DatabaseKeyStore(context.applicationContext).also { keyStore = it }
        }
    }
}
