package com.activitytrace.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupLimitsTest {

    @Test
    fun `bounded input aborts once the limit is exceeded`() {
        val source = BoundedInputStream(ByteArrayInputStream(ByteArray(8) { 1 }), 4)

        assertEquals(4, source.read(ByteArray(4)))
        assertThrows(BackupTooLargeException::class.java) {
            source.read(ByteArray(4))
        }
        assertThrows(BackupTooLargeException::class.java) {
            source.read()
        }
    }

    @Test
    fun `bounded output aborts once the limit is exceeded`() {
        val bounded = BoundedOutputStream(ByteArrayOutputStream(), 10)
        bounded.write(ByteArray(10))

        assertThrows(BackupTooLargeException::class.java) {
            bounded.write(1)
        }
    }

    @Test
    fun `bounded streams pass through data within the limit`() {
        val bytes = ByteArray(100) { (it % 7).toByte() }
        val bounded = BoundedInputStream(ByteArrayInputStream(bytes), bytes.size.toLong())
        val out = ByteArrayOutputStream()
        bounded.copyTo(BoundedOutputStream(out, bytes.size.toLong()))
        assertArrayEquals(bytes, out.toByteArray())
    }
}