package com.activitytrace.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the database encryption key using a wrapping key for protection.
 *
 * The actual SQLCipher key is random 256-bit material generated via SecureRandom.
 * It is wrapped (encrypted with AES-GCM) using a wrapping key. In production the
 * wrapping key lives in Android Keystore and is never exported (`.encoded` is null).
 *
 * Legacy fallback keys from SharedPreferences are migrated on first access:
 * the legacy key is wrapped, the wrapped copy is verified, and only then is the
 * legacy material deleted.
 */
class DatabaseKeyStore(
    private val context: Context,
    private val wrappingKeyProvider: WrappingKeyProvider = AndroidKeystoreWrappingKeyProvider(),
) {

    fun interface WrappingKeyProvider {
        fun getWrappingKey(): SecretKey
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the 256-bit database key, transparently unwrapping if needed.
     *
     * Priority:
     * 1. Wrapped key already stored → unwrap and return
     * 2. Legacy fallback key in SharedPreferences → wrap it, verify, then delete legacy
     * 3. Fresh install → generate a new key, store it wrapped, return
     */
    fun getDatabaseKey(): ByteArray {
        readWrappedKey()?.let { return it }
        migrateLegacyKey()?.let { return it }
        return createNewDatabaseKey()
    }

    /**
     * Wraps [plaintext] using AES-GCM with the wrapping key.
     * The envelope stores a random 12-byte IV and the ciphertext (which includes
     * the 16-byte GCM authentication tag).
     */
    fun wrap(plaintext: ByteArray): EncryptionEnvelope {
        val cipher = Cipher.getInstance(GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKeyProvider.getWrappingKey())
        return EncryptionEnvelope(
            version = EncryptionEnvelope.CURRENT_VERSION,
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    /**
     * Unwraps [envelope] back to the original plaintext.
     *
     * Throws [javax.crypto.AEADBadTagException] if the ciphertext was tampered
     * with, the IV is wrong, or a different wrapping key is used.
     */
    fun unwrap(envelope: EncryptionEnvelope): ByteArray {
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, envelope.iv)
        val cipher = Cipher.getInstance(GCM_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKeyProvider.getWrappingKey(), spec)
        return cipher.doFinal(envelope.ciphertext)
    }

    /**
     * Returns the wrapping key's `.encoded` value, or null if the key is not
     * exportable. For Android Keystore production keys this must be null.
     */
    fun wrappingKeyEncoded(): ByteArray? = wrappingKeyProvider.getWrappingKey().encoded

    // ── Wrapped-key path ──────────────────────────────────────────────

    private fun readWrappedKey(): ByteArray? {
        val b64 = prefs.getString(WRAPPED_KEY_PREF, null) ?: return null
        return unwrap(parseEnvelope(b64))
    }

    private fun persistWrappedKey(key: ByteArray) {
        val b64 = Base64.encodeToString(wrap(key).toBytes(), Base64.NO_WRAP)
        prefs.edit().putString(WRAPPED_KEY_PREF, b64).commit()
    }

    // ── Legacy migration ──────────────────────────────────────────────

    private fun migrateLegacyKey(): ByteArray? {
        val legacyB64 = prefs.getString(LEGACY_KEY_PREF, null) ?: return null
        val legacyKey = Base64.decode(legacyB64, Base64.DEFAULT)

        val wrapped = wrap(legacyKey)
        val restored = unwrap(wrapped)

        // Verify before deleting legacy material, so a mismatch never loses the key.
        check(restored.contentEquals(legacyKey)) {
            "Wrapped key verification failed during legacy migration"
        }

        persistWrappedEnvelope(wrapped)
        prefs.edit().remove(LEGACY_KEY_PREF).commit()
        return legacyKey
    }

    // ── Fresh key creation ────────────────────────────────────────────

    private fun createNewDatabaseKey(): ByteArray {
        val key = ByteArray(DB_KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        persistWrappedKey(key)
        return key
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun parseEnvelope(b64: String): EncryptionEnvelope =
        EncryptionEnvelope.fromBytes(Base64.decode(b64, Base64.NO_WRAP))

    private fun persistWrappedEnvelope(envelope: EncryptionEnvelope) {
        val b64 = Base64.encodeToString(envelope.toBytes(), Base64.NO_WRAP)
        prefs.edit().putString(WRAPPED_KEY_PREF, b64).commit()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val WRAPPER_ALIAS = "activity_trace_wrapping_key"
        private const val GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val DB_KEY_SIZE_BYTES = 32
        private const val PREFS_NAME = "activity_trace_encryption"
        private const val WRAPPED_KEY_PREF = "wrapped_database_key"
        const val LEGACY_KEY_PREF = "fallback_key"
    }
}

/**
 * Production sourcing of the wrapping key from Android Keystore.
 * The key is created with AES/GCM/NoPadding, 256-bit, and is non-exportable.
 */
class AndroidKeystoreWrappingKeyProvider : DatabaseKeyStore.WrappingKeyProvider {

    override fun getWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        keyStore.getEntry(WRAPPER_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        val spec = KeyGenParameterSpec.Builder(
            WRAPPER_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val WRAPPER_ALIAS = "activity_trace_wrapping_key"
    }
}