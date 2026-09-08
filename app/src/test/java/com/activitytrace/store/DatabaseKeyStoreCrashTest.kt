package com.activitytrace.store

import android.content.Context
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseKeyStoreCrashTest {

    private lateinit var context: Context

    private val provider = FakeWrappingKeyProvider()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    private val legacyBytes = ByteArray(32) { (it * 3 + 1).toByte() }

    /** Invariant: at least one of {legacy key, wrapped key} must always exist. */
    private class FakeKeyStorage : DatabaseKeyStore.KeyStorage {
        var failReadWrapped = false
        var failSaveWrapped = false
        var failDeleteLegacy = false
        var wrapped: EncryptionEnvelope? = null
        var legacy: String? = null

        override fun readWrappedEnvelope(): EncryptionEnvelope? {
            if (failReadWrapped) throw IllegalStateException("simulated read failure")
            return wrapped
        }

        override fun saveWrappedEnvelope(envelope: EncryptionEnvelope) {
            if (failSaveWrapped) throw IllegalStateException("simulated write failure")
            wrapped = envelope
        }

        override fun readLegacyKey(): String? = legacy

        override fun deleteLegacyKey() {
            if (failDeleteLegacy) throw IllegalStateException("simulated delete failure")
            legacy = null
        }
    }

    @Test
    fun `successful migration replaces legacy with a verified wrapped key`() {
        val storage = FakeKeyStorage().apply { legacy = encode(legacyBytes) }

        val key = store(storage).getDatabaseKey()

        assertArrayEquals(legacyBytes, key)
        assertNull("legacy removed after a successful migration", storage.legacy)
        assertNotNull("wrapped key persisted after migration", storage.wrapped)
        assertArrayEquals(legacyBytes, store(storage).getDatabaseKey())
    }

    @Test
    fun `legacy key survives a wrapped-key write failure`() {
        val storage = FakeKeyStorage().apply {
            legacy = encode(legacyBytes)
            failSaveWrapped = true
        }

        assertThrows(IllegalStateException::class.java) {
            store(storage).getDatabaseKey()
        }

        assertNotNull("legacy key must remain when its wrapped copy cannot be stored", storage.legacy)
        assertNull("no partial wrapped key may exist", storage.wrapped)
    }

    @Test
    fun `fresh key creation failure leaves no partial state`() {
        val storage = FakeKeyStorage().apply { failSaveWrapped = true }

        assertThrows(IllegalStateException::class.java) {
            store(storage).getDatabaseKey()
        }

        assertNull(storage.legacy)
        assertNull("no wrapped key may be recorded on a failed fresh install", storage.wrapped)
    }

    @Test
    fun `wrapped key persists even when legacy deletion fails`() {
        val storage = FakeKeyStorage().apply {
            legacy = encode(legacyBytes)
            failDeleteLegacy = true
        }

        assertThrows(IllegalStateException::class.java) {
            store(storage).getDatabaseKey()
        }

        assertNotNull("legacy key may remain on delete failure", storage.legacy)
        val wrapped = storage.wrapped
        assertNotNull("a verified wrapped key must exist", wrapped)
        assertArrayEquals(legacyBytes, store(storage).unwrap(wrapped!!))
    }

    @Test
    fun `completely unreadable wrapped key propagates and preserves legacy`() {
        val storage = FakeKeyStorage().apply {
            legacy = encode(legacyBytes)
            failReadWrapped = true
        }

        assertThrows(IllegalStateException::class.java) {
            store(storage).getDatabaseKey()
        }

        assertNotNull("legacy must be untouched when the wrapped read fails", storage.legacy)
        assertNull(storage.wrapped)
    }

    @Test
    fun `corrupt legacy key fails decoding without deleting anything`() {
        val storage = FakeKeyStorage().apply { legacy = "not-base64!" }

        assertThrows(IllegalArgumentException::class.java) {
            store(storage).getDatabaseKey()
        }

        assertEquals("corrupt legacy material must not be deleted", "not-base64!", storage.legacy)
        assertNull(storage.wrapped)
    }

    private fun encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)

    private fun store(storage: FakeKeyStorage): DatabaseKeyStore =
        DatabaseKeyStore(context, provider, storage)

    private class FakeWrappingKeyProvider : DatabaseKeyStore.WrappingKeyProvider {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getWrappingKey(): SecretKey = key
    }
}