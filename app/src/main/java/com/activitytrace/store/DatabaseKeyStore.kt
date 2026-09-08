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
    private val storage: KeyStorage = SharedPreferencesKeyStorage(context),
) {

    fun interface WrappingKeyProvider {
        fun getWrappingKey(): SecretKey
    }

    interface KeyStorage {
        fun readWrappedEnvelope(): EncryptionEnvelope?
        fun saveWrappedEnvelope(envelope: EncryptionEnvelope)
        fun readLegacyKey(): String?
        fun deleteLegacyKey()
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

    // ── Key sources ───────────────────────────────────────────────────

    private fun readWrappedKey(): ByteArray? {
        val envelope = storage.readWrappedEnvelope() ?: return null
        return unwrap(envelope)
    }

    private fun migrateLegacyKey(): ByteArray? {
        val legacyB64 = storage.readLegacyKey() ?: return null
        val legacyKey = Base64.decode(legacyB64, Base64.DEFAULT)

        val wrapped = wrap(legacyKey)
        val restored = unwrap(wrapped)

        // Verify before deleting legacy material, so a mismatch never loses the key.
        check(restored.contentEquals(legacyKey)) {
            "Wrapped key verification failed during legacy migration"
        }

        storage.saveWrappedEnvelope(wrapped)
        storage.deleteLegacyKey()
        return legacyKey
    }

    private fun createNewDatabaseKey(): ByteArray {
        val key = ByteArray(DB_KEY_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        storage.saveWrappedEnvelope(wrap(key))
        return key
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val WRAPPER_ALIAS = "activity_trace_wrapping_key"
        private const val GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val DB_KEY_SIZE_BYTES = 32
        const val LEGACY_KEY_PREF = "fallback_key"
    }
}

/**
 * Default [DatabaseKeyStore.KeyStorage] backed by SharedPreferences.
 *
 * Persistent writes are checked: a failed `commit()` aborts the migration with
 * an exception instead of silently continuing without durable state.
 */
internal class SharedPreferencesKeyStorage(context: Context) : DatabaseKeyStore.KeyStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readWrappedEnvelope(): EncryptionEnvelope? {
        val b64 = prefs.getString(WRAPPED_KEY_PREF, null) ?: return null
        return EncryptionEnvelope.fromBytes(Base64.decode(b64, Base64.NO_WRAP))
    }

    override fun saveWrappedEnvelope(envelope: EncryptionEnvelope) {
        val committed = prefs.edit()
            .putString(WRAPPED_KEY_PREF, Base64.encodeToString(envelope.toBytes(), Base64.NO_WRAP))
            .commit()
        check(committed) { "Failed to persist the wrapped database key" }
    }

    override fun readLegacyKey(): String? = prefs.getString(DatabaseKeyStore.LEGACY_KEY_PREF, null)

    override fun deleteLegacyKey() {
        val committed = prefs.edit().remove(DatabaseKeyStore.LEGACY_KEY_PREF).commit()
        check(committed) { "Failed to delete the legacy database key" }
    }

    private companion object {
        const val PREFS_NAME = "activity_trace_encryption"
        const val WRAPPED_KEY_PREF = "wrapped_database_key"
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