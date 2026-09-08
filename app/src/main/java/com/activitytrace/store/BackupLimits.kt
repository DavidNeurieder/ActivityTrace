package com.activitytrace.store

import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Resource bounds for encrypted backup files, which must be treated as an
 * untrusted input format. Every allocation derived from the backup (raw file,
 * decrypted payload, unzipped database, metadata) is capped here so a
 * malicious or corrupt backup cannot exhaust memory or disk.
 */
object BackupLimits {
    /** Bounds the whole encrypted backup file copied from the source. */
    const val MAX_ENCRYPTED_BACKUP_BYTES: Long = 256L * 1024 * 1024

    /** Bounds the decrypted (GCM) ciphertext. */
    const val MAX_CIPHERTEXT_BYTES: Long = 256L * 1024 * 1024

    /** Bounds the unzipped `database.sqlite` entry. */
    const val MAX_DATABASE_ENTRY_BYTES: Long = 256L * 1024 * 1024

    /** Bounds the unzipped `metadata.json` entry. */
    const val MAX_METADATA_ENTRY_BYTES: Int = 16 * 1024

    /** Minimum PBKDF2 iterations accepted from a header (anti-weak-KDF). */
    const val MIN_KDF_ITERATIONS: Int = 10_000

    /** Maximum PBKDF2 iterations accepted from a header (anti-DoS). */
    const val MAX_KDF_ITERATIONS: Int = 1_000_000
}

/**
 * Raised whenever an input exceeds one of [BackupLimits].
 */
class BackupTooLargeException(message: String) : IOException(message)

/**
 * [InputStream] that aborts with [BackupTooLargeException] as soon as data
 * beyond [maxBytes] is encountered. An input that ends exactly at the limit
 * is allowed (the probe read returns -1).
 */
class BoundedInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long,
) : InputStream() {
    private var read: Long = 0

    override fun read(): Int {
        if (read >= maxBytes) {
            val b = delegate.read()
            if (b >= 0) {
                read++
                throw BackupTooLargeException("input exceeded limit of $maxBytes bytes")
            }
            return -1
        }
        val b = delegate.read()
        if (b >= 0) read++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (read >= maxBytes) return read()
        val n = delegate.read(b, off, len)
        if (n > 0) read += n
        return n
    }

    override fun close() = delegate.close()
}

/**
 * [OutputStream] that aborts once more than [maxBytes] have been written.
 */
class BoundedOutputStream(
    private val delegate: OutputStream,
    private val maxBytes: Long,
) : OutputStream() {
    private var written: Long = 0

    private fun reserve(count: Int) {
        val next = written + count
        if (next > maxBytes) throw BackupTooLargeException("output exceeded limit of $maxBytes bytes")
        written = next
    }

    override fun write(b: Int) {
        reserve(1)
        delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        reserve(len)
        delegate.write(b, off, len)
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()
}

/**
 * [OutputStream] wrapper that forwards bytes but never closes the underlying
 * stream. Used to finalize a GCM cipher (writing the authentication tag)
 * without taking ownership of a caller-provided destination.
 */
class NonClosingOutputStream(delegate: OutputStream) : FilterOutputStream(delegate) {
    override fun write(b: Int) = out.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
    override fun close() {
        flush()
    }
}