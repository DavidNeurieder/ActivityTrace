package com.activitytrace.store

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Full-chain migration matrix against the REAL encrypted stack: every
 * committed schema fixture (6, 7, 8) is materialised as a SQLCipher database
 * (DDL taken verbatim from the Room schema JSONs that ship in the APK), seeded
 * with rows, then opened through [ActivityTraceDatabase.buildDatabase] so the
 * real Room + SQLCipher open path applies the migration chain to 9.
 *
 * This closes the gap the framework-SQLite MigrationTestHelper tests leave:
 * FTS5 (MIGRATION_8_9) only exists here, and the migrations run against the
 * same SQLCipher `SupportSQLiteDatabase` production uses.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MigrationMatrixTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val passphrase: ByteArray = "matrix-passphrase".toByteArray()
    private val createdDbs = mutableListOf<File>()

    @Before
    fun loadSqlCipher() {
        System.loadLibrary("sqlcipher")
    }

    @After
    fun tearDown() {
        createdDbs.forEach { it.delete() }
        createdDbs.clear()
    }

    @Test
    fun full_chain_migrates_every_encrypted_source_version_to_current() {
        for (version in arrayOf(6, 7, 8)) {
            migrateEncryptedDatabaseFrom(version)
        }
    }

    private fun migrateEncryptedDatabaseFrom(sourceVersion: Int) {
        val fixture = seedEncryptedDatabase(sourceVersion)

        ActivityTraceDatabase.buildDatabase(context, passphrase, fixture.name).apply {
            val db = openHelper.writableDatabase

            assertEquals(
                "v$sourceVersion must be migrated to the current schema version",
                ActivityTraceDatabase.CURRENT_VERSION,
                db.version,
            )
            assertEquals("rows must survive the chain", 2L, count(db, "SELECT COUNT(*) FROM captured_items"))

            val ftsRecovered = count(
                db,
                "SELECT COUNT(*) FROM captured_items_fts WHERE captured_items_fts MATCH 'tracking'",
            )
            assertEquals("MIGRATION_8_9 must backfill rows into FTS", 1L, ftsRecovered)

            assertEquals("integrity_check must pass on the migrated database", "ok", scalar(db, "PRAGMA integrity_check"))

            db.execSQL(
                "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked, content_hash) " +
                    "VALUES ('fresh matrix row', 'com.example', 'screen', 3000, 0, 'hash-fresh')"
            )
            val liveSync = count(
                db,
                "SELECT COUNT(*) FROM captured_items_fts WHERE captured_items_fts MATCH 'fresh'",
            )
            assertEquals("migration triggers must keep FTS in sync on insert", 1L, liveSync)

            val blockedKept = count(
                db,
                "SELECT COUNT(*) FROM blocked_apps WHERE app_package = 'com.custom.app'",
            )
            assertEquals("existing rows in every table must survive", 1L, blockedKept)

            val hashIndex = count(
                db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_captured_items_content_hash'",
            )
            assertEquals("the content_hash unique index must exist", 1L, hashIndex)
            close()
        }

        ActivityTraceDatabase.buildDatabase(context, passphrase, fixture.name).apply {
            val db = openHelper.writableDatabase
            assertEquals("reopen must not re-migrate", ActivityTraceDatabase.CURRENT_VERSION, db.version)
            assertEquals("reopen must keep all rows", 3L, count(db, "SELECT COUNT(*) FROM captured_items"))
            close()
        }
    }

    private fun seedEncryptedDatabase(version: Int): File {
        val file = context.getDatabasePath("migration_matrix_${version}_${System.nanoTime()}.db")
        createdDbs += file
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file.absolutePath, passphrase, null, null).use { db ->
            val schema = readSchema(version)
            val entities = schema.getJSONArray("entities")
            applyDdl(db, entities)
            applyIndices(db, entities)
            db.execSQL("PRAGMA user_version = $version")
            db.execSQL("INSERT INTO blocked_apps(app_package) VALUES ('com.custom.app')")
            insertSeedRows(db, version)
        }
        return file
    }

    private fun applyDdl(db: SQLiteDatabase, entities: JSONArray) {
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
        }
    }

    private fun applyIndices(db: SQLiteDatabase, entities: JSONArray) {
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
        }
    }

    private fun insertSeedRows(db: SQLiteDatabase, version: Int) {
        val hashColumn = if (version >= 8) ", content_hash" else ""
        val firstHashValue = if (version >= 8) ", 'hash-matrix-1'" else ""
        val secondHashValue = if (version >= 8) ", 'hash-matrix-2'" else ""
        db.execSQL(
            "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked$hashColumn) " +
                "VALUES ('dhl tracking number', 'com.example', 'notification', 1000, 0$firstHashValue)"
        )
        db.execSQL(
            "INSERT INTO captured_items (text, app_package, content_type, timestamp, is_bookmarked$hashColumn) " +
                "VALUES ('project discussion notes', 'com.example', 'screen', 2000, 0$secondHashValue)"
        )
    }

    private fun readSchema(version: Int): JSONObject {
        val raw = context.assets
            .open("com.activitytrace.store.ActivityTraceDatabase/$version.json")
            .bufferedReader()
            .use { it.readText() }
        return JSONObject(raw).getJSONObject("database")
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long {
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            return cursor.getLong(0)
        }
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String {
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            return cursor.getString(0)
        }
    }
}