package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class EncryptedBackupImporterTest {

    private lateinit var context: Context
    private val dao = mockk<CaptureDao>()
    private val backupUri: Uri = Uri.parse("content://backup/activity_trace_backup.activitytrace")
    private val password = "test-password".toCharArray()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        coEvery { dao.insertAll(any()) } returns listOf(1L)
    }

    @After
    fun tearDown() {
        File(context.cacheDir, "encrypted_import").deleteRecursively()
        File(context.cacheDir, "encrypted_export").deleteRecursively()
    }

    private fun encryptedBackupBytes(): ByteArray {
        val sqliteBytes = File(context.cacheDir, "source.sqlite").also { it.delete() }.let { file ->
            SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null).use { db ->
                db.execSQL("CREATE TABLE captured_items (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, app_package TEXT NOT NULL, app_name TEXT, content_type TEXT NOT NULL, category TEXT, timestamp INTEGER NOT NULL, metadata TEXT)")
                db.execSQL("INSERT INTO captured_items (text, app_package, content_type, timestamp) VALUES ('secret note', 'com.example', 'notification', 1000)")
            }
            file.readBytes()
        }
        val payload = BackupPayload.zip(sqliteBytes, """{"schema_version":7}""")
        return BackupCrypto.encrypt(payload, password)
    }

    private fun stubBackupBytes(bytes: ByteArray) {
        shadowOf(context.contentResolver).registerInputStream(backupUri, ByteArrayInputStream(bytes))
    }

    @Test
    fun `encrypted import restores items from the backup`() = runTest {
        stubBackupBytes(encryptedBackupBytes())

        val result = EncryptedBackupExporter.import(context, backupUri, password, dao)

        assertTrue("expected success, was $result", result is RestoreResult.Success)
        assertEquals(1, (result as RestoreResult.Success).importedCount)
        coVerify { dao.insertAll(any()) }
    }

    @Test
    fun `encrypted import merges only new items`() = runTest {
        stubBackupBytes(encryptedBackupBytes())
        coEvery { dao.insertAll(any()) } returns listOf(-1L)

        val result = EncryptedBackupExporter.import(context, backupUri, password, dao)

        assertTrue("expected success with 0 imports, was $result", result is RestoreResult.Success)
        assertEquals(0, (result as RestoreResult.Success).importedCount)
    }

    @Test
    fun `encrypted import with wrong password is an invalid backup and does not insert`() = runTest {
        stubBackupBytes(encryptedBackupBytes())

        val result = EncryptedBackupExporter.import(context, backupUri, "wrong".toCharArray(), dao)

        assertTrue("expected InvalidBackup, was $result", result is RestoreResult.InvalidBackup)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `failed restore leaves no temporary files`() = runTest {
        val tempDir = File(context.cacheDir, "encrypted_import")
        tempDir.mkdirs()
        tempDir.resolve("plain.sqlite").writeBytes(byteArrayOf(0x01))
        stubBackupBytes(encryptedBackupBytes())

        EncryptedBackupExporter.import(context, backupUri, "wrong".toCharArray(), dao)

        assertTrue("temp dir must be removed after failed restore", !tempDir.exists())
    }

    @Test
    fun `successful restore leaves no temp files behind`() = runTest {
        stubBackupBytes(encryptedBackupBytes())

        EncryptedBackupExporter.import(context, backupUri, password, dao)

        assertTrue("temp dir must be removed after successful restore", !File(context.cacheDir, "encrypted_import").exists())
    }

    @Test
    fun `corrupted encrypted backup is rejected`() = runTest {
        val encrypted = encryptedBackupBytes()
        encrypted[encrypted.size - 1] = (encrypted.last().toInt() xor 0x01).toByte()
        stubBackupBytes(encrypted)

        val result = EncryptedBackupExporter.import(context, backupUri, password, dao)

        assertTrue("corrupted backup must be rejected, was $result", result is RestoreResult.InvalidBackup)
    }

    @Test
    fun `empty password export returns an error without touching the database`() = runTest {
        val result = EncryptedBackupExporter.export(context, CharArray(0))
        assertTrue(result is ExportStatus.Error)
    }
}