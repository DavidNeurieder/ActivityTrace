package com.activitytrace.store

import com.activitytrace.store.BackupEnvelope.GCM_TAG_BITS
import com.activitytrace.store.BackupEnvelope.MAGIC
import com.activitytrace.store.BackupEnvelope.NONCE_LENGTH
import com.activitytrace.store.BackupEnvelope.SALT_LENGTH
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEnvelope {
    const val MAGIC = "ATBK"
    const val VERSION: Byte = 1

    const val SALT_LENGTH = 16
    const val NONCE_LENGTH = 12
    const val PBKDF2_ITERATIONS = 128_000
    const val KEY_LENGTH_BITS = 256
    const val GCM_TAG_BITS = 128

    const val DB_ENTRY = "database.sqlite"
    const val METADATA_ENTRY = "metadata.json"
}

object BackupCrypto {

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec: KeySpec = PBEKeySpec(
            password,
            salt,
            BackupEnvelope.PBKDF2_ITERATIONS,
            BackupEnvelope.KEY_LENGTH_BITS,
        )
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(derived.encoded, "AES")
    }

    fun encrypt(payload: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(BackupEnvelope.SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(BackupEnvelope.NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(BackupEnvelope.GCM_TAG_BITS, nonce),
        )
        val ciphertext = cipher.doFinal(payload)

        return ByteArrayOutputStream(MAGIC.length + 1 + salt.size + nonce.size + ciphertext.size).use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(BackupEnvelope.VERSION.toInt())
            out.write(salt)
            out.write(nonce)
            out.write(ciphertext)
            out.toByteArray()
        }
    }

    fun decrypt(backup: ByteArray, password: CharArray): ByteArray {
        require(backup.size > MAGIC.length + 1 + SALT_LENGTH + NONCE_LENGTH) {
            "Backup too short to be a valid encrypted backup"
        }
        val magic = backup.copyOfRange(0, MAGIC.length).toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "Not an ActivityTrace backup" }
        val version = backup[MAGIC.length]
        require(version == BackupEnvelope.VERSION) { "Unsupported backup version: $version" }

        val offset = MAGIC.length + 1
        val salt = backup.copyOfRange(offset, offset + SALT_LENGTH)
        val nonce = backup.copyOfRange(offset + SALT_LENGTH, offset + SALT_LENGTH + NONCE_LENGTH)
        val ciphertext = backup.copyOfRange(offset + SALT_LENGTH + NONCE_LENGTH, backup.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(BackupEnvelope.GCM_TAG_BITS, nonce),
        )
        return cipher.doFinal(ciphertext)
    }
}

object BackupPayload {

    fun zip(databaseBytes: ByteArray, metadata: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(BackupEnvelope.DB_ENTRY))
                zip.write(databaseBytes)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(BackupEnvelope.METADATA_ENTRY))
                zip.write(metadata.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            output.toByteArray()
        }

    fun unzip(payload: ByteArray): Pair<ByteArray, String> {
        var databaseBytes: ByteArray? = null
        var metadata: String? = null
        ZipInputStream(ByteArrayInputStream(payload)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    BackupEnvelope.DB_ENTRY -> databaseBytes = zip.readBytes()
                    BackupEnvelope.METADATA_ENTRY ->
                        metadata = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val db = databaseBytes ?: error("Backup is missing ${BackupEnvelope.DB_ENTRY}")
        val meta = metadata ?: error("Backup is missing ${BackupEnvelope.METADATA_ENTRY}")
        return db to meta
    }
}