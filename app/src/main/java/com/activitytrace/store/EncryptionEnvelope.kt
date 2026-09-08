package com.activitytrace.store

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Binary envelope for a wrapped database key.
 *
 * Format: [version:1][ivLen:2][iv:ivLen][ciphertext+tag]
 *
 * The ciphertext includes the 16-byte GCM authentication tag appended by the cipher.
 */
data class EncryptionEnvelope(
    val version: Int,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.writeByte(version)
        dos.writeShort(iv.size)
        dos.write(iv)
        dos.write(ciphertext)
        dos.flush()
        return out.toByteArray()
    }

    companion object {
        const val CURRENT_VERSION = 1

        fun fromBytes(data: ByteArray): EncryptionEnvelope {
            val input = ByteArrayInputStream(data)
            val dis = DataInputStream(input)
            val version = dis.readUnsignedByte()
            val ivLen = dis.readUnsignedShort()
            val iv = ByteArray(ivLen)
            dis.readFully(iv)
            val remaining = data.size - (1 + 2 + ivLen)
            val ciphertext = ByteArray(remaining)
            dis.readFully(ciphertext)
            return EncryptionEnvelope(version, iv, ciphertext)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptionEnvelope) return false
        return version == other.version &&
            iv.contentEquals(other.iv) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}
