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

    private val keyStores = mutableMapOf<String, DatabaseKeyStore>()

    fun getOrCreateKey(context: Context): ByteArray {
        return getKeyStore(context).getDatabaseKey()
    }

    private fun getKeyStore(context: Context): DatabaseKeyStore {
        val appContext = context.applicationContext
        val key = appContext.packageName
        return keyStores.getOrPut(key) { DatabaseKeyStore(appContext) }
    }
}
