package com.activitytrace.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator

object EncryptionManager {
    private const val KEY_ALIAS = "activity_trace_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "activity_trace_encryption"
    private const val FALLBACK_KEY_KEY = "fallback_key"

    fun getOrCreateKey(context: Context): ByteArray {
        val keystoreBytes = try {
            getKeystoreKey()
        } catch (_: Exception) {
            null
        }
        if (keystoreBytes != null) return keystoreBytes
        return getFallbackKey(context)
    }

    private fun getKeystoreKey(): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val secretKey = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
                val encoded = secretKey.secretKey.encoded
                if (encoded != null) return encoded
            }
        } catch (e: Exception) {
            keyStore.deleteEntry(KEY_ALIAS)
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        val encoded = keyGenerator.generateKey().encoded
        if (encoded != null) return encoded
        throw IllegalStateException("Keystore key encoded is null")
    }

    private fun getFallbackKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(FALLBACK_KEY_KEY, null)
        if (existing != null) {
            return Base64.decode(existing, Base64.DEFAULT)
        }
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        prefs.edit().putString(
            FALLBACK_KEY_KEY,
            Base64.encodeToString(key, Base64.DEFAULT)
        ).apply()
        return key
    }
}
