package com.activitytrace.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator

object EncryptionManager {
    private const val KEY_ALIAS = "activity_trace_master_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun getOrCreateKey(context: Context): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val secretKey = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
                return secretKey.secretKey.encoded
            }
        } catch (e: Exception) {
            keyStore.deleteEntry(KEY_ALIAS)
            context.deleteDatabase("activity_trace.db")
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
        return keyGenerator.generateKey().encoded
    }
}
