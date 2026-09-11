package com.activitytrace.store

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
class EncryptedBackupRoundTripTest {

    private lateinit var context: Context
    private val password = "instrumented round trip password".toCharArray()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        // Encrypted exports accumulate in Downloads across runs and make the
        // MediaStore unique-file resolver fail ("Failed to build unique file"),
        // so purge everything this test can produce.
        purgeDownloads("activity_trace_backup")
        purgeDownloads("export_error_")
        File(context.cacheDir, "encrypted_import").deleteRecursively()
        File(context.cacheDir, "encrypted_export").deleteRecursively()
    }

    private fun purgeDownloads(prefix: String) {
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                if (name != null && name.startsWith(prefix)) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    context.contentResolver.delete(
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                        null,
                        null,
                    )
                }
            }
        }
    }

    private fun latestBackupUri(): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        // MediaStore may index the freshly written file asynchronously, so poll.
        repeat(20) {
            val found = findBackupUri()
            if (found != null) return found
            Thread.sleep(250)
        }
        return null
    }

    private fun findBackupUri(): Uri? {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.IS_PENDING,
        )
        val baseName = "activity_trace_backup"
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Downloads._ID} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                val pending = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Downloads.IS_PENDING))
                if (pending == 0 && name.startsWith(baseName)) {
                    return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)),
                    )
                }
            }
        }
        return null
    }

    private fun decryptedRestoreFile(uri: Uri): File {
        val decrypted = File(context.cacheDir, "roundtrip_decrypted.zip").also { it.delete() }
        context.contentResolver.openInputStream(uri)!!.use { input ->
            decrypted.outputStream().use { output ->
                BackupCrypto.decryptTo(
                    input,
                    output,
                    password,
                    BackupLimits.MAX_CIPHERTEXT_BYTES,
                )
            }
        }
        val restored = File(context.cacheDir, "roundtrip_restored.sqlite").also { it.delete() }
        decrypted.inputStream().use { payload ->
            restored.outputStream().use { databaseOut ->
                BackupPayload.unzipDatabase(
                    payload,
                    BackupLimits.MAX_DATABASE_ENTRY_BYTES,
                    BackupLimits.MAX_METADATA_ENTRY_BYTES,
                    databaseOut,
                )
            }
        }
        decrypted.delete()
        return restored
    }

    @Test
    fun encrypted_backup_round_trips_through_the_real_database() = runTest {
        val dao = ActivityTraceDatabase.getInstance(context).captureDao()
        val distinctive = "roundtrip_secret_${System.nanoTime()}"

        dao.insert(
            CapturedItem(
                text = distinctive,
                appPackage = "com.example",
                contentType = "notification",
                timestamp = System.currentTimeMillis(),
            ),
        )

        val status = EncryptedBackupExporter.export(context, password)
        assertTrue("export must succeed on device, was $status", status is ExportStatus.Success)

        val uri = requireNotNull(latestBackupUri()) { "backup file not found in MediaStore" }

        val encryptedBytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertFalse("encrypted backup must not contain the plaintext", String(encryptedBytes, Charsets.ISO_8859_1).contains(distinctive))

        val restored = decryptedRestoreFile(uri)
        try {
            assertTrue(
                "restored sqlite must contain the distinctive row",
                String(restored.readBytes(), Charsets.ISO_8859_1).contains(distinctive),
            )
        } finally {
            restored.delete()
        }
    }

@Test
    fun encrypted_backup_rejects_a_wrong_password_on_device() = runTest {
        val dao = ActivityTraceDatabase.getInstance(context).captureDao()
        val status = EncryptedBackupExporter.export(context, password)
        assertTrue("export must succeed on device, was $status", status is ExportStatus.Success)

        val uri = requireNotNull(latestBackupUri()) { "backup file not found in MediaStore" }
        val result = EncryptedBackupExporter.import(context, uri, "wrong-password".toCharArray(), dao)
        assertTrue(
            "wrong password must yield InvalidBackup, was $result",
            result is RestoreResult.InvalidBackup,
        )
    }

    @Test
    fun encrypted_backup_export_import_round_trips_to_a_fresh_database() = runTest {
        val dao = ActivityTraceDatabase.getInstance(context).captureDao()
        val markers = listOf(
            "roundtrip_alpha_${System.nanoTime()}",
            "roundtrip_beta_${System.nanoTime()}",
        )
        dao.insert(
            CapturedItem(
                text = markers[0],
                appPackage = "com.roundtrip",
                contentType = "notification",
                timestamp = 42_000L,
            ),
        )
        dao.insert(
            CapturedItem(
                text = markers[1],
                appPackage = "com.roundtrip",
                appName = "RoundTrip",
                contentType = "screen",
                category = "browsing",
                timestamp = 43_000L,
                metadata = "roundtrip metadata",
            ),
        )

        val status = EncryptedBackupExporter.export(context, password)
        assertTrue("export must succeed on device, was $status", status is ExportStatus.Success)

        val uri = requireNotNull(latestBackupUri()) { "backup file not found in MediaStore" }

        val sourceCount = dao.getAllItems().size

        // Restore into a brand-new, empty database — the "other device" half of
        // the round trip.
        val unique = System.nanoTime()
        val freshDbFileName = "fresh_restore_$unique.db"
        val freshDbFile = context.getDatabasePath(freshDbFileName)
        freshDbFile.delete()
        val freshDb = ActivityTraceDatabase.buildDatabase(
            context,
            "fresh-restore-passphrase".toByteArray(),
            freshDbFileName,
        )
        try {
            val freshDao = freshDb.captureDao()
            val result = EncryptedBackupExporter.import(context, uri, password, freshDao)
            assertTrue(
                "restore into a fresh database must succeed, was $result",
                result is RestoreResult.Success,
            )

            val exportedTexts = dao.getAllItems().map { it.text }
            val restoredTexts = freshDao.getAllItems().map { it.text }
            assertEquals("every exported row must round trip", exportedTexts.sorted(), restoredTexts.sorted())
            assertEquals("source count must equal import count", sourceCount, (result as RestoreResult.Success).importedCount)

            val alpha = freshDao.getAllItems().first { it.text == markers[0] }
            assertEquals("com.roundtrip", alpha.appPackage)
            assertEquals("notification", alpha.contentType)
            assertEquals(42_000L, alpha.timestamp)

            val beta = freshDao.getAllItems().first { it.text == markers[1] }
            assertEquals("RoundTrip", beta.appName)
            assertEquals("screen", beta.contentType)
            assertEquals("browsing", beta.category)
            assertEquals(43_000L, beta.timestamp)
            assertEquals("roundtrip metadata", beta.metadata)
        } finally {
            freshDb.close()
            freshDbFile.delete()
            File("${freshDbFile.path}-wal").delete()
            File("${freshDbFile.path}-shm").delete()
        }
    }

    @Test
    fun failed_restore_leaves_existing_rows_intact() = runTest {
        val dao = ActivityTraceDatabase.getInstance(context).captureDao()
        val keeper = "keeper_row_${System.nanoTime()}"
        dao.insert(
            CapturedItem(
                text = keeper,
                appPackage = "com.example",
                contentType = "notification",
                timestamp = System.currentTimeMillis(),
            ),
        )
        val status = EncryptedBackupExporter.export(context, password)
        assertTrue("export must succeed on device, was $status", status is ExportStatus.Success)
        val uri = requireNotNull(latestBackupUri()) { "backup file not found in MediaStore" }

        val tamperedPassword = charArrayOf(*password).also { it[0] = (it[0].code xor 0x01).toChar() }
        val result = EncryptedBackupExporter.import(context, uri, tamperedPassword, dao)

        assertTrue("tampered restore must fail, was $result", result is RestoreResult.InvalidBackup)
        val rows = dao.getAllItems()
        assertTrue(
            "existing rows must survive a failed restore",
            rows.any { it.text == keeper },
        )
    }

    @Test
    fun failed_restore_leaves_no_temporary_files_on_disk() = runTest {
        val dao = ActivityTraceDatabase.getInstance(context).captureDao()
        val status = EncryptedBackupExporter.export(context, password)
        assertTrue("export must succeed on device, was $status", status is ExportStatus.Success)
        val uri = requireNotNull(latestBackupUri()) { "backup file not found in MediaStore" }

        EncryptedBackupExporter.import(context, uri, "wrong-password".toCharArray(), dao)

        assertTrue(
            "import temp dir must be removed after a failed restore",
            !File(context.cacheDir, "encrypted_import").exists(),
        )
        val stray = context.cacheDir.walkTopDown().filter { it.isFile && it.extension == "sqlite" }
            .toList()
        assertTrue("no plaintext sqlite files may remain after a failed restore, found: $stray", stray.isEmpty())
    }

    @Test
    fun encrypted_backup_export_leaves_no_plaintext_database_on_disk() = runTest {
        val dao = ActivityTraceDatabase.getInstance(context).captureDao()
        dao.insert(
            CapturedItem(
                text = "plaintext_that_must_not_persist",
                appPackage = "com.example",
                contentType = "notification",
                timestamp = System.currentTimeMillis(),
            ),
        )

        val status = EncryptedBackupExporter.export(context, password)
        assertTrue("export must succeed on device, was $status", status is ExportStatus.Success)

        // No plaintext SQLite may survive in the export temp dir.
        val tempDir = File(context.cacheDir, "encrypted_export")
        assertFalse("export temp dir must be removed", tempDir.exists())
        // And no stray plaintext sqlite anywhere in the app cache.
        val stray = context.cacheDir.walkTopDown().filter { it.isFile && it.extension == "sqlite" }
            .toList()
        assertTrue("no plaintext sqlite files may remain in cache, found: $stray", stray.isEmpty())
    }
}