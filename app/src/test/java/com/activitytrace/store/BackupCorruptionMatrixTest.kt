package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/**
 * Every corrupt or hostile backup fixture must surface as
 * [RestoreResult.InvalidBackup] (never an exception escaping to the UI) and
 * must never reach the database write path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BackupCorruptionMatrixTest {

    private lateinit var context: Context
    private val dao = mockk<CaptureDao>()
    private val backupUri: Uri = Uri.parse("content://backup/corrupt.activitytrace")
    private val password = "matrix-password".toCharArray()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        coEvery { dao.getAllItemKeys() } returns emptyList()
        coEvery { dao.getNullHashBatch(any()) } returns emptyList()
        coEvery { dao.setContentHash(any(), any()) } returns 1
        coEvery { dao.insertAll(any()) } returns emptyList()
    }

    private fun validBackupBytes(): ByteArray {
        val sqliteBytes = File(context.cacheDir, "matrix_source.sqlite").also { it.delete() }.let { file ->
            SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null).use { db ->
                db.execSQL("CREATE TABLE captured_items (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, app_package TEXT NOT NULL, app_name TEXT, content_type TEXT NOT NULL, category TEXT, timestamp INTEGER NOT NULL, metadata TEXT)")
                db.execSQL("INSERT INTO captured_items (text, app_package, content_type, timestamp) VALUES ('matrix row', 'com.example', 'notification', 1000)")
            }
            file.readBytes()
        }
        val payload = BackupPayload.zip(sqliteBytes, """{"schema_version":7}""")
        return BackupCrypto.encrypt(payload, password)
    }

    private fun stubBackupBytes(bytes: ByteArray) {
        shadowOf(context.contentResolver).registerInputStream(backupUri, ByteArrayInputStream(bytes))
    }

    private fun assertInvalidBackup(name: String, bytes: ByteArray) = runTest {
        stubBackupBytes(bytes)
        val result = EncryptedBackupExporter.import(context, backupUri, password, dao)
        assertTrue(
            "`$name' must yield InvalidBackup, was $result",
            result is RestoreResult.InvalidBackup,
        )
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    private fun withPayloadLength(bytes: ByteArray, length: Long): ByteArray {
        val out = bytes.copyOf()
        ByteBuffer.allocate(BackupEnvelope.PAYLOAD_LENGTH_BYTES)
            .order(ByteOrder.BIG_ENDIAN).putLong(length)
            .array()
            .copyInto(
                out,
                destinationOffset = BackupEnvelope.HEADER_BYTES - BackupEnvelope.PAYLOAD_LENGTH_BYTES,
            )
        return out
    }

    @Test
    fun `valid backup is accepted`() = runTest {
        stubBackupBytes(validBackupBytes())
        val result = EncryptedBackupExporter.import(context, backupUri, password, dao)
        assertTrue("control must succeed, was $result", result is RestoreResult.Success)
    }

    @Test
    fun `empty file is an invalid backup`() = assertInvalidBackup("empty", byteArrayOf())

    @Test
    fun `random bytes are an invalid backup`() {
        val random = ByteArray(200).also { SecureRandom().nextBytes(it) }
        assertInvalidBackup("random", random)
    }

    @Test
    fun `truncated header is an invalid backup`() {
        assertInvalidBackup("truncated header", validBackupBytes().copyOfRange(0, 10))
    }

    @Test
    fun `truncated ciphertext is an invalid backup`() {
        val backup = validBackupBytes()
        assertInvalidBackup("truncated ciphertext", backup.copyOfRange(0, backup.size - 24))
    }

    @Test
    fun `wrong password is an invalid backup`() = runTest {
        stubBackupBytes(validBackupBytes())
        val result = EncryptedBackupExporter.import(context, backupUri, "nope".toCharArray(), dao)
        assertTrue("wrong password must be InvalidBackup, was $result", result is RestoreResult.InvalidBackup)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `wrong version is an invalid backup`() {
        val backup = validBackupBytes().also { it[BackupEnvelope.MAGIC.length] = 99 }
        assertInvalidBackup("wrong version", backup)
    }

    @Test
    fun `unknown kdf is an invalid backup`() {
        val backup = validBackupBytes().also { it[BackupEnvelope.MAGIC.length + 1] = 77 }
        assertInvalidBackup("unknown kdf", backup)
    }

    @Test
    fun `oversized payload in the header is rejected before decryption`() {
        assertInvalidBackup(
            "oversized payload",
            withPayloadLength(validBackupBytes(), BackupLimits.MAX_PAYLOAD_BYTES + 1),
        )
    }

    @Test
    fun `modified ciphertext is an invalid backup`() {
        val backup = validBackupBytes()
        val middle = BackupEnvelope.HEADER_BYTES + 8
        backup[middle] = (backup[middle].toInt() xor 0x41).toByte()
        assertInvalidBackup("modified ciphertext", backup)
    }

    @Test
    fun `modified authentication tag is an invalid backup`() {
        val backup = validBackupBytes()
        backup[backup.size - 1] = (backup.last().toInt() xor 0x01).toByte()
        assertInvalidBackup("modified tag", backup)
    }

    @Test
    fun `modified salt is an invalid backup`() {
        val backup = validBackupBytes()
        backup[BackupEnvelope.MAGIC.length + 2] = (backup[BackupEnvelope.MAGIC.length + 2].toInt() xor 0x20).toByte()
        assertInvalidBackup("modified salt", backup)
    }

    @Test
    fun `modified nonce is an invalid backup`() {
        val backup = validBackupBytes()
        backup[BackupEnvelope.HEADER_BYTES - BackupEnvelope.NONCE_LENGTH] =
            (backup[BackupEnvelope.HEADER_BYTES - BackupEnvelope.NONCE_LENGTH].toInt() xor 0x40).toByte()
        assertInvalidBackup("modified nonce", backup)
    }

    @Test
    fun `excessive kdf iterations in the header are an invalid backup`() {
        val backup = validBackupBytes()
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt(BackupLimits.MAX_KDF_ITERATIONS + 1)
            .array()
            .copyInto(
                backup,
                destinationOffset = BackupEnvelope.MAGIC.length + 2 + BackupEnvelope.SALT_LENGTH,
            )
        assertInvalidBackup("excessive iterations", backup)
    }

    @Test
    fun `weak kdf iterations in the header are an invalid backup`() {
        val backup = validBackupBytes()
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putInt(BackupLimits.MIN_KDF_ITERATIONS - 1)
            .array()
            .copyInto(
                backup,
                destinationOffset = BackupEnvelope.MAGIC.length + 2 + BackupEnvelope.SALT_LENGTH,
            )
        assertInvalidBackup("weak iterations", backup)
    }
}