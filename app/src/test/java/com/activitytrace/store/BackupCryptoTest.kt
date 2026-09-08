package com.activitytrace.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException

class BackupCryptoTest {

    private val password = "correct horse battery staple".toCharArray()

    @Test
    fun `encrypted backup starts with the magic header`() {
        val backup = BackupCrypto.encrypt(ByteArray(0), password)
        assertEquals("ATBK", backup.copyOfRange(0, 4).toString(Charsets.US_ASCII))
    }

    @Test
    fun `encrypted backup is larger than plaintext payload`() {
        val payload = ByteArray(100) { 0x55 }
        val backup = BackupCrypto.encrypt(payload, password)
        assertTrue(backup.size > payload.size)
    }

    @Test
    fun `encrypted backup contains no plaintext`() {
        val secret = "SECRET_DB_CONTENT".toByteArray(Charsets.UTF_8)
        val backup = BackupCrypto.encrypt(secret, password)
        assertFalse(String(backup, Charsets.ISO_8859_1).contains("SECRET_DB_CONTENT"))
    }

    @Test
    fun `encrypted backup cannot be opened without password`() {
        val payload = "payload".toByteArray(Charsets.UTF_8)
        val backup = BackupCrypto.encrypt(payload, password)
        assertThrows(GeneralSecurityException::class.java) {
            BackupCrypto.decrypt(backup, "wrong password".toCharArray())
        }
    }

    @Test
    fun `encrypted backup restores correctly with the right password`() {
        val payload = "encrypted round trip works".toByteArray(Charsets.UTF_8)
        val backup = BackupCrypto.encrypt(payload, password)
        assertArrayEquals(payload, BackupCrypto.decrypt(backup, password))
    }

    @Test
    fun `corrupted ciphertext is rejected`() {
        val payload = "payload".toByteArray(Charsets.UTF_8)
        val backup = BackupCrypto.encrypt(payload, password)
        val tampered = backup.copyOf()
        tampered[tampered.size - 1] = (tampered.last().toInt() xor 0x01).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            BackupCrypto.decrypt(tampered, password)
        }
    }

    @Test
    fun `modified nonce is rejected`() {
        val payload = "payload".toByteArray(Charsets.UTF_8)
        val backup = BackupCrypto.encrypt(payload, password)
        val tampered = backup.copyOf()
        val nonceIndex = BackupEnvelope.HEADER_BYTES - BackupEnvelope.NONCE_LENGTH
        tampered[nonceIndex] = (tampered[nonceIndex].toInt() xor 0x40).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            BackupCrypto.decrypt(tampered, password)
        }
    }

    @Test
    fun `modified salt is rejected`() {
        val payload = "payload".toByteArray(Charsets.UTF_8)
        val backup = BackupCrypto.encrypt(payload, password)
        val tampered = backup.copyOf()
        val saltIndex = BackupEnvelope.MAGIC.length + 2
        tampered[saltIndex] = (tampered[saltIndex].toInt() xor 0x20).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            BackupCrypto.decrypt(tampered, password)
        }
    }

    @Test
    fun `garbage bytes are rejected as not a backup`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt("not a backup at all".toByteArray(), password)
        }
    }

    @Test
    fun `unsupported version is rejected`() {
        val payload = "payload".toByteArray(Charsets.UTF_8)
        val backup = BackupCrypto.encrypt(payload, password)
        val tampered = backup.copyOf()
        tampered[BackupEnvelope.MAGIC.length] = 99

        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(tampered, password)
        }
    }

    @Test
    fun `each encryption uses a fresh random salt and nonce`() {
        val payload = "payload".toByteArray(Charsets.UTF_8)
        val a = BackupCrypto.encrypt(payload, password)
        val b = BackupCrypto.encrypt(payload, password)
        assertFalse(String(a, Charsets.ISO_8859_1) == String(b, Charsets.ISO_8859_1))
    }

    @Test
    fun `header records kdf parameters for future decoding`() {
        val backup = BackupCrypto.encrypt(ByteArray(0), password)
        val header = BackupHeader.readFrom(ByteArrayInputStream(backup))
        assertEquals(BackupEnvelope.VERSION.toInt(), header.version.toInt())
        assertEquals(BackupEnvelope.KDF_PBKDF2_SHA256_ID.toInt(), header.kdfId.toInt())
        assertEquals(BackupEnvelope.SALT_LENGTH, header.salt.size)
        assertEquals(BackupEnvelope.NONCE_LENGTH, header.nonce.size)
        assertEquals(BackupEnvelope.PBKDF2_ITERATIONS, header.iterations)
    }

    @Test
    fun `custom kdf iterations are encrypted and decoded back`() {
        val payload = "with custom iterations".toByteArray(Charsets.UTF_8)
        val iterations = 32_000
        val backup = BackupCrypto.encrypt(payload, password, iterations)
        val header = BackupHeader.readFrom(ByteArrayInputStream(backup))
        assertEquals(iterations, header.iterations)
        assertArrayEquals(payload, BackupCrypto.decrypt(backup, password))
    }

    @Test
    fun `weak kdf iterations are rejected from the header`() {
        val header = craftedHeader(iterations = BackupLimits.MIN_KDF_ITERATIONS - 1)
        assertThrows(IllegalArgumentException::class.java) {
            BackupHeader.readFrom(ByteArrayInputStream(header))
        }
    }

    @Test
    fun `excessive kdf iterations are rejected from the header`() {
        val header = craftedHeader(iterations = BackupLimits.MAX_KDF_ITERATIONS + 1)
        assertThrows(IllegalArgumentException::class.java) {
            BackupHeader.readFrom(ByteArrayInputStream(header))
        }
    }

    @Test
    fun `unknown kdf is rejected from the header`() {
        val header = craftedHeader(kdfId = 77)
        assertThrows(IllegalArgumentException::class.java) {
            BackupHeader.readFrom(ByteArrayInputStream(header))
        }
    }

    @Test
    fun `streaming encryption round-trips a large payload without buffering`() {
        val payload = ByteArray(1 shl 20) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        BackupCrypto.encryptTo(ByteArrayInputStream(payload), encrypted, password)
        assertArrayEquals(payload, BackupCrypto.decrypt(encrypted.toByteArray(), password))
    }

    private fun craftedHeader(iterations: Int = BackupEnvelope.PBKDF2_ITERATIONS, kdfId: Byte = BackupEnvelope.KDF_PBKDF2_SHA256_ID): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(BackupEnvelope.MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(BackupEnvelope.VERSION.toInt())
        out.write(kdfId.toInt())
        out.write(ByteArray(BackupEnvelope.SALT_LENGTH))
        out.write(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.BIG_ENDIAN).putInt(iterations).array())
        out.write(ByteArray(BackupEnvelope.NONCE_LENGTH))
        return out.toByteArray()
    }
}

class BackupPayloadTest {

    private val databaseBytes = "fake sqlite bytes".toByteArray(Charsets.UTF_8)
    private val metadata = """{"schema_version":7,"exported_at":123}"""

    @Test
    fun `zip and unzip round-trip the database and metadata`() {
        val payload = BackupPayload.zip(databaseBytes, metadata)
        val (restoredDb, restoredMetadata) = BackupPayload.unzip(payload)

        assertArrayEquals(databaseBytes, restoredDb)
        assertEquals(metadata, restoredMetadata)
    }

    @Test
    fun `database entry is present after round-trip`() {
        val payload = BackupPayload.zip(databaseBytes, metadata)
        assertTrue(String(BackupPayload.unzip(payload).first, Charsets.UTF_8).contains("sqlite"))
    }

    @Test
    fun `unzip rejects a payload without the database entry`() {
        val emptyZip = java.io.ByteArrayOutputStream().use { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry(BackupEnvelope.METADATA_ENTRY))
                zip.write(metadata.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            out.toByteArray()
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupPayload.unzip(emptyZip)
        }
        assertTrue(error.message!!.contains("database.sqlite"))
    }

    @Test
    fun `unzipDatabase enforces database size limit`() {
        val payload = BackupPayload.zip(ByteArray(1000), """{"a":1}""")
        val out = java.io.ByteArrayOutputStream()
        assertThrows(BackupTooLargeException::class.java) {
            BackupPayload.unzipDatabase(
                ByteArrayInputStream(payload),
                100,
                BackupLimits.MAX_METADATA_ENTRY_BYTES,
                out,
            )
        }
    }

    @Test
    fun `unzipDatabase enforces metadata size limit`() {
        val longMetadata = "x".repeat(1024)
        val payload = BackupPayload.zip(ByteArray(10), longMetadata)
        val out = java.io.ByteArrayOutputStream()
        assertThrows(BackupTooLargeException::class.java) {
            BackupPayload.unzipDatabase(
                ByteArrayInputStream(payload),
                BackupLimits.MAX_DATABASE_ENTRY_BYTES,
                16,
                out,
            )
        }
    }
}