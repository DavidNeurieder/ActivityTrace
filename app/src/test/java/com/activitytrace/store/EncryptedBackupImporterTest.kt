package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coJustRun
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
import java.security.GeneralSecurityException

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
        coEvery { dao.getAllItemKeys() } returns emptyList()
        coJustRun { dao.insertAll(any()) }
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

        val count = EncryptedBackupExporter.import(context, backupUri, password, dao)

        assertEquals(1, count)
        coVerify { dao.insertAll(any()) }
    }

    @Test
    fun `encrypted import merges only new items`() = runTest {
        stubBackupBytes(encryptedBackupBytes())
        coEvery { dao.getAllItemKeys() } returns
            listOf(CaptureDao.ItemKey("secret note", 1000, "com.example"))

        val count = EncryptedBackupExporter.import(context, backupUri, password, dao)

        assertEquals(0, count)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `encrypted import with wrong password fails and does not insert`() = runTest {
        stubBackupBytes(encryptedBackupBytes())

        var thrown = false
        try {
            EncryptedBackupExporter.import(context, backupUri, "wrong".toCharArray(), dao)
        } catch (e: GeneralSecurityException) {
            thrown = true
        }
        assertTrue("expected a GeneralSecurityException for the wrong password", thrown)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    @Test
    fun `failed restore leaves no temporary files`() = runTest {
        val tempDir = File(context.cacheDir, "encrypted_import")
        tempDir.mkdirs()
        tempDir.resolve("plain.sqlite").writeBytes(byteArrayOf(0x01))
        stubBackupBytes(encryptedBackupBytes())

        try {
            EncryptedBackupExporter.import(context, backupUri, "wrong".toCharArray(), dao)
        } catch (_: GeneralSecurityException) {
        }

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

        var thrown = false
        try {
            EncryptedBackupExporter.import(context, backupUri, password, dao)
        } catch (e: GeneralSecurityException) {
            thrown = true
        }
        assertTrue("corrupted backup must be rejected", thrown)
    }

    @Test
    fun `empty password export returns an error without touching the database`() = runTest {
        val result = EncryptedBackupExporter.export(context, CharArray(0))
        assertTrue(result is ExportStatus.Error)
    }
}