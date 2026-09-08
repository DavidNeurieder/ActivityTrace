package com.activitytrace.store

import android.content.Context
import android.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseKeyStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    // ── Key lifecycle ─────────────────────────────────────────────────

    @Test
    fun `creates a 256-bit database key`() {
        val key = store().getDatabaseKey()
        assertEquals(32, key.size)
    }

    @Test
    fun `returns the same key on second call`() {
        val s = store()
        assertArrayEquals(s.getDatabaseKey(), s.getDatabaseKey())
    }

    @Test
    fun `creates a new store instance that still returns the same persisted key`() {
        val provider = FakeWrappingKeyProvider()
        val first = DatabaseKeyStore(context, provider).getDatabaseKey()
        val second = DatabaseKeyStore(context, provider).getDatabaseKey()
        assertArrayEquals(first, second)
    }

    @Test
    fun `missing legacy key creates a new fresh key`() {
        val key = store().getDatabaseKey()
        assertEquals(32, key.size)
    }

    // ── Wrap / unwrap ─────────────────────────────────────────────────

    @Test
    fun `wrapping then unwrapping returns identical bytes`() {
        val s = store()
        val plaintext = ByteArray(32) { it.toByte() }
        assertArrayEquals(plaintext, s.unwrap(s.wrap(plaintext)))
    }

    @Test
    fun `wrapped key is not equal to plaintext (it is actually encrypted)`() {
        val s = store()
        val plaintext = ByteArray(32) { 0x42 }
        val envelope = s.wrap(plaintext)
        var equal = true
        if (envelope.ciphertext.size == plaintext.size) {
            for (i in plaintext.indices) {
                if (envelope.ciphertext[i] != plaintext[i]) equal = false
            }
        } else {
            equal = false
        }
        assertEquals(false, equal)
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val provider = FakeWrappingKeyProvider()
        val s = DatabaseKeyStore(context, provider)

        val storedB64 = context.getSharedPreferences("activity_trace_encryption", Context.MODE_PRIVATE)
            .getString(DatabaseKeyStore.LEGACY_KEY_PREF, null)
        assertNull("precondition: no legacy key", storedB64)

        s.getDatabaseKey()

        val prefs = context.getSharedPreferences("activity_trace_encryption", Context.MODE_PRIVATE)
        val wrappedB64 = prefs.getString("wrapped_database_key", null)
            ?: error("expected a wrapped database key")
        val tampered = Base64.decode(wrappedB64, Base64.NO_WRAP).also { payload ->
            payload[15] = (payload[15].toInt() xor 0x01).toByte() // flip a ciphertext byte
        }
        prefs.edit().putString(
            "wrapped_database_key",
            Base64.encodeToString(tampered, Base64.NO_WRAP),
        ).commit()

        assertThrows(AEADBadTagException::class.java) {
            DatabaseKeyStore(context, provider).getDatabaseKey()
        }
    }

    @Test
    fun `tampered IV fails authentication`() {
        val provider = FakeWrappingKeyProvider()
        val s = DatabaseKeyStore(context, provider)

        s.getDatabaseKey()

        val prefs = context.getSharedPreferences("activity_trace_encryption", Context.MODE_PRIVATE)
        val wrappedB64 = prefs.getString("wrapped_database_key", null)
            ?: error("expected a wrapped database key")
        val tampered = Base64.decode(wrappedB64, Base64.NO_WRAP).also { payload ->
            payload[3] = (payload[3].toInt() xor 0x80).toByte() // first IV byte
        }
        prefs.edit().putString(
            "wrapped_database_key",
            Base64.encodeToString(tampered, Base64.NO_WRAP),
        ).commit()

        assertThrows(AEADBadTagException::class.java) {
            DatabaseKeyStore(context, provider).getDatabaseKey()
        }
    }

    @Test
    fun `malformed stored envelope fails`() {
        val provider = FakeWrappingKeyProvider()
        val s = DatabaseKeyStore(context, provider)
        s.getDatabaseKey()

        val prefs = context.getSharedPreferences("activity_trace_encryption", Context.MODE_PRIVATE)
        prefs.edit().putString("wrapped_database_key", "not-base64!" ).commit()

        assertThrows(IllegalArgumentException::class.java) {
            DatabaseKeyStore(context, provider).getDatabaseKey()
        }
    }

    // ── Legacy migration ──────────────────────────────────────────────

    @Test
    fun `legacy key migrates to a wrapped key`() {
        val legacyKey = ByteArray(32) { (it + 1).toByte() }
        val prefs = context.getSharedPreferences("activity_trace_encryption", Context.MODE_PRIVATE)
        prefs.edit().putString(
            DatabaseKeyStore.LEGACY_KEY_PREF,
            Base64.encodeToString(legacyKey, Base64.DEFAULT),
        ).commit()

        val migrated = store().getDatabaseKey()

        assertArrayEquals(legacyKey, migrated)
        assertNull("legacy key removed after migration", prefs.getString(DatabaseKeyStore.LEGACY_KEY_PREF, null))
        assertEquals(
            "wrapped key persisted",
            true,
            prefs.getString("wrapped_database_key", null) != null,
        )
    }

    @Test
    fun `legacy key is preserved on a fresh store instance until it is reused`() {
        val legacyKey = ByteArray(32) { 0x33 }
        val prefs = context.getSharedPreferences("activity_trace_encryption", Context.MODE_PRIVATE)
        prefs.edit().putString(
            DatabaseKeyStore.LEGACY_KEY_PREF,
            Base64.encodeToString(legacyKey, Base64.DEFAULT),
        ).commit()

        val provider = FakeWrappingKeyProvider()

        // First access migrates and returns the same bytes.
        assertArrayEquals(legacyKey, DatabaseKeyStore(context, provider).getDatabaseKey())

        // Legacy material is gone; wrapped copy remains usable.
        assertNull(prefs.getString(DatabaseKeyStore.LEGACY_KEY_PREF, null))
        assertArrayEquals(legacyKey, DatabaseKeyStore(context, provider).getDatabaseKey())
    }

    // ── Wrap / unwrap stability across instances ──────────────────────

    @Test
    fun `unwrapping with a different wrapping key fails`() {
        val providerA = FakeWrappingKeyProvider()
        val providerB = FakeWrappingKeyProvider()
        val s = DatabaseKeyStore(context, providerA)

        val envelope = s.wrap(ByteArray(32) { 0x11 })

        assertThrows(AEADBadTagException::class.java) {
            DatabaseKeyStore(context, providerB).unwrap(envelope)
        }
    }

    private fun store(): DatabaseKeyStore = DatabaseKeyStore(context, FakeWrappingKeyProvider())

    private class FakeWrappingKeyProvider : DatabaseKeyStore.WrappingKeyProvider {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun getWrappingKey(): SecretKey = key
    }
}