package com.activitytrace.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.activitytrace.model.BlockedApp
import com.activitytrace.model.CapturedItem
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [CapturedItem::class, BlockedApp::class],
    version = 6,
    exportSchema = false,
)
abstract class ActivityTraceDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    abstract fun blockedAppDao(): BlockedAppDao

    companion object {
        @Volatile
        private var INSTANCE: ActivityTraceDatabase? = null

        fun getInstance(context: Context): ActivityTraceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ActivityTraceDatabase {
            return try {
                val passphrase = EncryptionManager.getOrCreateKey(context)
                val factory = SupportFactory(passphrase)
                Room.databaseBuilder(
                    context.applicationContext,
                    ActivityTraceDatabase::class.java,
                    "activity_trace.db"
                )
                    .openHelperFactory(factory)
                    .addCallback(SEED_DEFAULTS_CALLBACK)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
            } catch (e: Exception) {
                context.deleteDatabase("activity_trace.db")
                EncryptionManager.getOrCreateKey(context)
                throw e
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captured_items ADD COLUMN app_name TEXT DEFAULT NULL")
                db.execSQL("DROP TABLE IF EXISTS captured_items_fts")
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS captured_items_fts
                    USING fts5(text, app_name, app_package UNINDEXED, content_type UNINDEXED, content=captured_items)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_ai
                    AFTER INSERT ON captured_items BEGIN
                        INSERT INTO captured_items_fts(rowid, text, app_name, app_package, content_type)
                        VALUES (new.rowid, new.text, new.app_name, new.app_package, new.content_type);
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_ad
                    AFTER DELETE ON captured_items BEGIN
                        INSERT INTO captured_items_fts(captured_items_fts, rowid, text, app_name, app_package, content_type)
                        VALUES ('delete', old.rowid, old.text, old.app_name, old.app_package, old.content_type);
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_au
                    AFTER UPDATE ON captured_items BEGIN
                        INSERT INTO captured_items_fts(captured_items_fts, rowid, text, app_name, app_package, content_type)
                        VALUES ('delete', old.rowid, old.text, old.app_name, old.app_package, old.content_type);
                        INSERT INTO captured_items_fts(rowid, text, app_name, app_package, content_type)
                        VALUES (new.rowid, new.text, new.app_name, new.app_package, new.content_type);
                    END;
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captured_items ADD COLUMN category TEXT DEFAULT NULL")
                db.execSQL("DROP TABLE IF EXISTS captured_items_fts")
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS captured_items_fts
                    USING fts5(text, app_name, category, app_package UNINDEXED, content_type UNINDEXED, content=captured_items)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_ai
                    AFTER INSERT ON captured_items BEGIN
                        INSERT INTO captured_items_fts(rowid, text, app_name, category, app_package, content_type)
                        VALUES (new.rowid, new.text, new.app_name, new.category, new.app_package, new.content_type);
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_ad
                    AFTER DELETE ON captured_items BEGIN
                        INSERT INTO captured_items_fts(captured_items_fts, rowid, text, app_name, category, app_package, content_type)
                        VALUES ('delete', old.rowid, old.text, old.app_name, old.category, old.app_package, old.content_type);
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_au
                    AFTER UPDATE ON captured_items BEGIN
                        INSERT INTO captured_items_fts(captured_items_fts, rowid, text, app_name, category, app_package, content_type)
                        VALUES ('delete', old.rowid, old.text, old.app_name, old.category, old.app_package, old.content_type);
                        INSERT INTO captured_items_fts(rowid, text, app_name, category, app_package, content_type)
                        VALUES (new.rowid, new.text, new.app_name, new.category, new.app_package, new.content_type);
                    END;
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TRIGGER IF EXISTS captured_items_fts_ai")
                db.execSQL("DROP TRIGGER IF EXISTS captured_items_fts_ad")
                db.execSQL("DROP TRIGGER IF EXISTS captured_items_fts_au")
                db.execSQL("DROP TABLE IF EXISTS captured_items_fts")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captured_items ADD COLUMN is_bookmarked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE captured_items ADD COLUMN image_blob BLOB DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS blocked_apps (
                        app_package TEXT NOT NULL PRIMARY KEY
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                seedDefaultBlocked(db)
            }
        }

        private val SEED_DEFAULTS_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seedDefaultBlocked(db)
            }
        }

        private fun seedDefaultBlocked(db: SupportSQLiteDatabase) {
            for (pkg in DEFAULT_BLOCKED) {
                db.execSQL("INSERT OR IGNORE INTO blocked_apps(app_package) VALUES(?)", arrayOf(pkg))
            }
        }
    }
}
