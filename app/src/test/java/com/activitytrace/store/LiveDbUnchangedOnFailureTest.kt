package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.security.SecureRandom

/**
 * Every failure path must leave the live database completely unchanged.
 * Uses [RestoreIntegrityVerifier] to assert that no writes reach the DAO.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class LiveDbUnchangedOnFailureTest {

    private lateinit var context: Context
    private val dao = mockk<CaptureDao>()
    private val uri: Uri = Uri.parse("content://backup/integrity.activitytrace")
    private val password = "integrity-password".toCharArray()
    private lateinit var verifier: RestoreIntegrityVerifier

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        verifier = RestoreIntegrityVerifier(dao)
        verifier.installMocks()
    }

    private fun validBackupBytes(): ByteArray {
        val sqliteBytes = File(context.cacheDir, "integrity_source.sqlite").also { it.delete() }.let { file ->
            SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null).use { db ->
                db.execSQL(
                    "CREATE TABLE captured_items (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, " +
                        "app_package TEXT NOT NULL, app_name TEXT, content_type TEXT NOT NULL, " +
                        "category TEXT, timestamp INTEGER NOT NULL, metadata TEXT)",
                )
                db.execSQL(
                    "INSERT INTO captured_items (text, app_package, content_type, timestamp) " +
                        "VALUES ('integrity row', 'com.example', 'notification', 1000)",
                )
            }
            file.readBytes()
        }
        val payload = BackupPayload.zip(sqliteBytes, """{"schema_version":7}""")
        return BackupCrypto.encrypt(payload, password)
    }

    private fun stubBackupBytes(bytes: ByteArray) {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
    }

    @Test
    fun wrong_password_does_not_write_to_dao() = verifier.verifyUnchanged {
        stubBackupBytes(validBackupBytes())
        EncryptedBackupExporter.import(context, uri, "wrong".toCharArray(), dao)
    }

    @Test
    fun empty_backup_does_not_write_to_dao() = verifier.verifyUnchanged {
        stubBackupBytes(byteArrayOf())
        EncryptedBackupExporter.import(context, uri, password, dao)
    }

    @Test
    fun random_bytes_does_not_write_to_dao() = verifier.verifyUnchanged {
        val garbage = ByteArray(200).also { SecureRandom().nextBytes(it) }
        stubBackupBytes(garbage)
        EncryptedBackupExporter.import(context, uri, password, dao)
    }

    @Test
    fun truncated_ciphertext_does_not_write_to_dao() = verifier.verifyUnchanged {
        val backup = validBackupBytes()
        stubBackupBytes(backup.copyOfRange(0, backup.size - 24))
        EncryptedBackupExporter.import(context, uri, password, dao)
    }

    @Test
    fun corrupted_gmac_tag_does_not_write_to_dao() = verifier.verifyUnchanged {
        val backup = validBackupBytes()
        backup[backup.size - 1] = (backup.last().toInt() xor 0x01).toByte()
        stubBackupBytes(backup)
        EncryptedBackupExporter.import(context, uri, password, dao)
    }

    @Test
    fun unknown_kdf_does_not_write_to_dao() = verifier.verifyUnchanged {
        val backup = validBackupBytes().also { it[BackupEnvelope.MAGIC.length + 1] = 77 }
        stubBackupBytes(backup)
        EncryptedBackupExporter.import(context, uri, password, dao)
    }

    @Test
    fun modified_ciphertext_does_not_write_to_dao() = verifier.verifyUnchanged {
        val backup = validBackupBytes()
        val mid = BackupEnvelope.HEADER_BYTES + 8
        backup[mid] = (backup[mid].toInt() xor 0x41).toByte()
        stubBackupBytes(backup)
        EncryptedBackupExporter.import(context, uri, password, dao)
    }
}
