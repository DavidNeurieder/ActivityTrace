package com.activitytrace.store

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import net.sqlcipher.database.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        SQLiteDatabase.loadLibs(context)
        dbFile = File(context.cacheDir, "encryption_test_${System.nanoTime()}.db")
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `keystore wrapping key is never exported`() {
        val store = DatabaseKeyStore(context)
        // Android Keystore keys are non-exportable by design; .encoded must be null.
        assertNull(store.wrappingKeyEncoded())
    }

    @Test
    fun `database key is 256 bits and stable across store instances`() {
        val key1 = DatabaseKeyStore(context).getDatabaseKey()
        val key2 = DatabaseKeyStore(context).getDatabaseKey()
        assertEquals(32, key1.size)
        assertEquals(key1, key2)
    }

    @Test
    fun `secret survives a full open-insert-close-reopen-read cycle`() {
        val key = DatabaseKeyStore(context).getDatabaseKey()

        openEncrypted(dbFile, key).use { db ->
            db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY, value TEXT)")
            db.execSQL("INSERT INTO t(value) VALUES ('SECRET_TEST_VALUE')")
        }

        openEncrypted(dbFile, key).use { db ->
            val cursor = db.rawQuery("SELECT value FROM t", null)
            cursor.moveToFirst()
            assertEquals("SECRET_TEST_VALUE", cursor.getString(0))
            cursor.close()
        }
    }

    @Test
    fun `encrypted database file does not contain plaintext`() {
        val key = DatabaseKeyStore(context).getDatabaseKey()

        openEncrypted(dbFile, key).use { db ->
            db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY, value TEXT)")
            db.execSQL("INSERT INTO t(value) VALUES ('SECRET_TEST_VALUE')")
        }

        val contents = dbFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(
            "database file must not contain the plaintext secret",
            contents.contains("SECRET_TEST_VALUE"),
        )
    }

    @Test
    fun `wrong key cannot open the database`() {
        val key = DatabaseKeyStore(context).getDatabaseKey()

        openEncrypted(dbFile, key).use { db ->
            db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY, value TEXT)")
            db.execSQL("INSERT INTO t(value) VALUES ('SECRET_TEST_VALUE')")
        }

        val wrongKey = ByteArray(32) { 0x11 }
        assertThrows(RuntimeException::class.java) {
            openEncrypted(dbFile, wrongKey).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM t", null)
            }
        }
    }

    @Test
    fun `failed open leaves the database byte-for-byte unchanged`() {
        val key = DatabaseKeyStore(context).getDatabaseKey()

        openEncrypted(dbFile, key).use { db ->
            db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY, value TEXT)")
            db.execSQL("INSERT INTO t(value) VALUES ('SECRET_TEST_VALUE')")
        }

        val before = dbFile.readBytes()

        val wrongKey = ByteArray(32) { 0x7e }
        assertThrows(RuntimeException::class.java) {
            openEncrypted(dbFile, wrongKey).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM t", null)
            }
        }

        assertArrayEquals("failed open must never rewrite or delete the database", before, dbFile.readBytes())
    }

    @Test
    fun `tryOpen returns an openable database and clears recovery state`() {
        RecoveryStateStore(context).record(RecoveryReason.CORRUPTED_DATABASE)

        val result = ActivityTraceDatabase.tryOpen(context)

        assertTrue(
            "a correct key must open successfully: got $result",
            result is DatabaseOpenResult.Opened,
        )
        val opened = result as DatabaseOpenResult.Opened
        opened.database.captureDao()
        assertNull("successful open must clear recovery state", RecoveryStateStore(context).current())
    }

    private fun openEncrypted(file: File, key: ByteArray): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(file.absolutePath, key, null, null)
}