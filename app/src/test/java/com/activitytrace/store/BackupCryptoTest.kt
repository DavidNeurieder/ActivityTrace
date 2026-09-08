package com.activitytrace.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
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
        tampered[BackupEnvelope.MAGIC.length + 1 + 8] =
            (tampered[BackupEnvelope.MAGIC.length + 1 + 8].toInt() xor 0x40).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            BackupCrypto.decrypt(tampered, password)
        }
    }

    @Test
    fun `modified salt is rejected`() {
        val payload = "payload".toByteArray(Charsets.UTF_8)
        val backup = BackupCrypto.encrypt(payload, password)
        val tampered = backup.copyOf()
        tampered[BackupEnvelope.MAGIC.length + 1] =
            (tampered[BackupEnvelope.MAGIC.length + 1].toInt() xor 0x20).toByte()

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

        val error = assertThrows(IllegalStateException::class.java) {
            BackupPayload.unzip(emptyZip)
        }
        assertTrue(error.message!!.contains("database.sqlite"))
    }
}