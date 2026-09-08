package com.activitytrace.store

import com.activitytrace.store.BackupEnvelope.DB_ENTRY
import com.activitytrace.store.BackupEnvelope.GCM_TAG_BITS
import com.activitytrace.store.BackupEnvelope.HEADER_BYTES
import com.activitytrace.store.BackupEnvelope.KDF_PBKDF2_SHA256_ID
import com.activitytrace.store.BackupEnvelope.MAGIC
import com.activitytrace.store.BackupEnvelope.METADATA_ENTRY
import com.activitytrace.store.BackupEnvelope.NONCE_LENGTH
import com.activitytrace.store.BackupEnvelope.SALT_LENGTH
import com.activitytrace.store.BackupEnvelope.VERSION
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEnvelope {
    const val MAGIC = "ATBK"
    const val VERSION: Byte = 2

    const val SALT_LENGTH = 16
    const val NONCE_LENGTH = 12
    const val PBKDF2_ITERATIONS = 128_000
    const val KEY_LENGTH_BITS = 256
    const val GCM_TAG_BITS = 128

    /**
     * `magic(4) + version(1) + kdfId(1) + salt(16) + iterations(4) + nonce(12)`.
     * KDF parameters are stored in the format so future iterations can be
     * changed without losing the ability to read older backups.
     */
    const val HEADER_BYTES = 4 + 1 + 1 + SALT_LENGTH + 4 + NONCE_LENGTH

    const val DB_ENTRY = "database.sqlite"
    const val METADATA_ENTRY = "metadata.json"

    const val KDF_PBKDF2_SHA256_ID: Byte = 1
    const val KDF_TRANSFORM = "PBKDF2WithHmacSHA256"
    const val CIPHER_TRANSFORM = "AES/GCM/NoPadding"

    const val IO_CHUNK = 64 * 1024
}

/**
 * The fixed plaintext prefix of every backup. Contains all parameters needed
 * to derive the key and decrypt the payload; validated strictly on read.
 */
data class BackupHeader(
    val version: Byte,
    val kdfId: Byte,
    val salt: ByteArray,
    val iterations: Int,
    val nonce: ByteArray,
) {
    fun writeTo(destination: OutputStream) {
        destination.write(MAGIC.toByteArray(Charsets.US_ASCII))
        destination.write(version.toInt())
        destination.write(kdfId.toInt())
        destination.write(salt)
        destination.write(
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(iterations).array(),
        )
        destination.write(nonce)
    }

    companion object {
        fun readFrom(source: InputStream): BackupHeader {
            val bytes = source.readFully(HEADER_BYTES)
            require(bytes.size == HEADER_BYTES) {
                "Backup too short to be a valid encrypted backup"
            }
            require(
                bytes.copyOfRange(0, MAGIC.length).toString(Charsets.US_ASCII) == MAGIC,
            ) { "Not an ActivityTrace backup" }

            val version = bytes[MAGIC.length]
            require(version == VERSION) { "Unsupported backup version: $version" }

            val kdfId = bytes[MAGIC.length + 1]
            require(kdfId == KDF_PBKDF2_SHA256_ID) { "Unsupported KDF: $kdfId" }

            val iterations = ByteBuffer.wrap(bytes, MAGIC.length + 2 + SALT_LENGTH, 4)
                .order(ByteOrder.BIG_ENDIAN).int
            require(iterations in BackupLimits.MIN_KDF_ITERATIONS..BackupLimits.MAX_KDF_ITERATIONS) {
                "Rejected KDF iterations: $iterations"
            }

            return BackupHeader(
                version = version,
                kdfId = kdfId,
                salt = bytes.copyOfRange(MAGIC.length + 2, MAGIC.length + 2 + SALT_LENGTH),
                iterations = iterations,
                nonce = bytes.copyOfRange(HEADER_BYTES - NONCE_LENGTH, HEADER_BYTES),
            )
        }
    }
}

object BackupCrypto {

    private fun randomBytes(length: Int): ByteArray =
        ByteArray(length).also { SecureRandom().nextBytes(it) }

    /**
     * Derives an AES key from [password]. The [PBEKeySpec] is
     * [PBEKeySpec.clearPassword]'d on every path so the password characters
     * are wiped from the spec's internal buffer.
     */
    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, BackupEnvelope.KEY_LENGTH_BITS)
        return try {
            val derived = SecretKeyFactory.getInstance(BackupEnvelope.KDF_TRANSFORM).generateSecret(spec)
            SecretKeySpec(derived.encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Writes a fresh backup header to [destination], then returns a cipher
     * output stream that GCM-encrypts everything written to it. Closing the
     * returned stream writes the authentication tag but does NOT close
     * [destination].
     */
    fun openEncryptStream(
        destination: OutputStream,
        password: CharArray,
        iterations: Int = BackupEnvelope.PBKDF2_ITERATIONS,
    ): CipherOutputStream {
        require(iterations in BackupLimits.MIN_KDF_ITERATIONS..BackupLimits.MAX_KDF_ITERATIONS) {
            "Iterations out of range: $iterations"
        }
        val salt = randomBytes(SALT_LENGTH)
        val nonce = randomBytes(NONCE_LENGTH)
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance(BackupEnvelope.CIPHER_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        BackupHeader(BackupEnvelope.VERSION, KDF_PBKDF2_SHA256_ID, salt, iterations, nonce)
            .writeTo(destination)
        return CipherOutputStream(NonClosingOutputStream(destination), cipher)
    }

    /**
     * Stream-encrypts [source] into [destination] without ever holding the
     * whole payload (or the whole ciphertext) in memory.
     */
    fun encryptTo(source: InputStream, destination: OutputStream, password: CharArray) {
        val cipherOut = openEncryptStream(destination, password)
        try {
            source.copyTo(cipherOut, BackupEnvelope.IO_CHUNK)
        } finally {
            cipherOut.flush()
            cipherOut.close()
        }
    }

    fun encrypt(payload: ByteArray, password: CharArray): ByteArray =
        ByteArrayOutputStream().use { out ->
            encryptTo(ByteArrayInputStream(payload), out, password)
            out.toByteArray()
        }

    fun encrypt(payload: ByteArray, password: CharArray, iterations: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            val cipherOut = openEncryptStream(out, password, iterations)
            try {
                ByteArrayInputStream(payload).copyTo(cipherOut, BackupEnvelope.IO_CHUNK)
            } finally {
                cipherOut.close()
            }
            out.toByteArray()
        }

    /**
     * Stream-decrypts [source] into [destination] without ever holding the
     * whole payload in memory. Uses a manual GCM update/doFinal loop (an
     * AEAD ciphertext cannot be safely streamed through [CipherInputStream]
     * on every provider), holding back the last tag-sized block until EOF is
     * reached. Wrong passwords and tampered data surface as
     * [GeneralSecurityException]; ciphertext beyond [maxCiphertextBytes]
     * aborts via [BackupTooLargeException].
     */
    fun decryptTo(
        source: InputStream,
        destination: OutputStream,
        password: CharArray,
        maxCiphertextBytes: Long,
    ) {
        val header = BackupHeader.readFrom(source)
        val cipher = Cipher.getInstance(BackupEnvelope.CIPHER_TRANSFORM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, header.salt, header.iterations),
            GCMParameterSpec(GCM_TAG_BITS, header.nonce),
        )
        val limited = BoundedInputStream(source, maxCiphertextBytes)

        val blockSize = 16
        val tagLength = GCM_TAG_BITS / 8
        val chunk = ByteArray(BackupEnvelope.IO_CHUNK * 2)

        // Only c-> doFinal(block-aligned feed) and a single doFinal over the
        // remaining partial block plus the tag is portable across providers
        // (SunJCE and Conscrypt/OpenSSL disagree on unaligned update calls).
        var pending = ByteArray(0)
        while (true) {
            val n = limited.read(chunk)
            if (n < 0) {
                val plaintext = cipher.doFinal(pending)
                destination.write(plaintext)
                destination.flush()
                return
            }
            val combined = ByteArray(pending.size + n)
            System.arraycopy(pending, 0, combined, 0, pending.size)
            System.arraycopy(chunk, 0, combined, pending.size, n)

            var feedLength = maxOf(combined.size - tagLength, 0)
            feedLength -= feedLength % blockSize
            if (feedLength > 0) {
                val plaintext = cipher.update(combined, 0, feedLength)
                if (plaintext != null) destination.write(plaintext)
            }
            pending = combined.copyOfRange(feedLength, combined.size)
        }
    }

    fun decrypt(backup: ByteArray, password: CharArray): ByteArray =
        ByteArrayOutputStream().use { out ->
            decryptTo(
                ByteArrayInputStream(backup),
                out,
                password,
                BackupLimits.MAX_CIPHERTEXT_BYTES,
            )
            out.toByteArray()
        }
}

object BackupPayload {

    fun zip(databaseBytes: ByteArray, metadata: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                writeEntries(ByteArrayInputStream(databaseBytes), metadata, zip)
            }
            output.toByteArray()
        }

    fun unzip(payload: ByteArray): Pair<ByteArray, String> =
        ByteArrayOutputStream().use { databaseOut ->
            val metadata = unzipDatabase(
                ByteArrayInputStream(payload),
                BackupLimits.MAX_DATABASE_ENTRY_BYTES,
                BackupLimits.MAX_METADATA_ENTRY_BYTES,
                databaseOut,
            )
            databaseOut.toByteArray() to metadata
        }

    /**
     * Writes the `database.sqlite` and `metadata.json` entries into [zip]
     * without buffering the database entry in memory.
     */
    fun writeEntries(databaseInput: InputStream, metadata: String, zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry(DB_ENTRY))
        databaseInput.copyTo(zip, BackupEnvelope.IO_CHUNK)
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(METADATA_ENTRY))
        zip.write(metadata.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
    }

    /**
     * Streams the `database.sqlite` entry into [databaseOut] (bounded by
     * [maxDatabaseBytes]) and returns the `metadata.json` entry (bounded by
     * [maxMetadataBytes]). Unknown zip entries are ignored. Fails fast on
     * missing required entries or over-limit sizes.
     */
    fun unzipDatabase(
        input: InputStream,
        maxDatabaseBytes: Long,
        maxMetadataBytes: Int,
        databaseOut: OutputStream,
    ): String {
        var sawDatabase = false
        var metadata: String? = null
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    DB_ENTRY -> {
                        sawDatabase = true
                        zip.copyTo(BoundedOutputStream(databaseOut, maxDatabaseBytes), BackupEnvelope.IO_CHUNK)
                    }

                    METADATA_ENTRY -> {
                        val bytes = ByteArrayOutputStream(maxMetadataBytes).use { buffer ->
                            zip.copyTo(BoundedOutputStream(buffer, maxMetadataBytes.toLong()), BackupEnvelope.IO_CHUNK)
                            buffer.toByteArray()
                        }
                        metadata = bytes.toString(Charsets.UTF_8)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(sawDatabase) { "Backup is missing $DB_ENTRY" }
        return metadata ?: error("Backup is missing $METADATA_ENTRY")
    }
}

internal fun InputStream.readFully(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val n = read(buffer, offset, count - offset)
        if (n < 0) break
        offset += n
    }
    return buffer.copyOf(offset)
}