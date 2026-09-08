package com.activitytrace.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.EOFException

class EncryptionEnvelopeTest {

    @Test
    fun `envelope round-trips through bytes`() {
        val original = EncryptionEnvelope(
            version = 1,
            iv = ByteArray(12) { it.toByte() },
            ciphertext = ByteArray(32) { (it * 2).toByte() },
        )

        val restored = EncryptionEnvelope.fromBytes(original.toBytes())

        assertEquals(original, restored)
        assertArrayEquals(original.iv, restored.iv)
        assertArrayEquals(original.ciphertext, restored.ciphertext)
    }

    @Test
    fun `envelope with empty ciphertext round-trips`() {
        val original = EncryptionEnvelope(1, ByteArray(12), ByteArray(0))
        assertEquals(original, EncryptionEnvelope.fromBytes(original.toBytes()))
    }

    @Test
    fun `malformed envelope shorter than header throws`() {
        assertThrows(EOFException::class.java) {
            EncryptionEnvelope.fromBytes(ByteArray(2))
        }
    }

    @Test
    fun `malformed envelope without payload throws`() {
        // header claims a 12-byte IV but nothing follows
        val data = byteArrayOf(1, 0, 12)
        assertThrows(EOFException::class.java) {
            EncryptionEnvelope.fromBytes(data)
        }
    }
}