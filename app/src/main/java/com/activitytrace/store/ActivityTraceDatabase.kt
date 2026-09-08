package com.activitytrace.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.activitytrace.model.BlockedApp
import com.activitytrace.model.CapturedItem
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [CapturedItem::class, BlockedApp::class],
    version = ActivityTraceDatabase.CURRENT_VERSION,
    exportSchema = true,
)
abstract class ActivityTraceDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    abstract fun blockedAppDao(): BlockedAppDao

    companion object {
        const val CURRENT_VERSION = 9
        @Volatile
        private var INSTANCE: ActivityTraceDatabase? = null

        fun getInstance(context: Context): ActivityTraceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext, EncryptionManager.getOrCreateKey(context))
                    .also { INSTANCE = it }
            }
        }

        fun tryOpen(context: Context): DatabaseOpenResult =
            tryOpen(context) { EncryptionManager.getOrCreateKey(context) }

        internal fun tryOpen(context: Context, keyProvider: () -> ByteArray): DatabaseOpenResult {
            val applicationContext = context.applicationContext
            val passphrase = try {
                keyProvider()
            } catch (error: Throwable) {
                val reason = RecoveryReason.INVALID_KEY
                RecoveryStateStore(applicationContext).record(reason)
                return DatabaseOpenResult.RecoveryRequired(reason)
            }
            val stateStore = RecoveryStateStore(applicationContext)
            val database = buildDatabase(applicationContext, passphrase)
            return DatabaseOpener(database, stateStore).open()
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray): ActivityTraceDatabase {
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                ActivityTraceDatabase::class.java,
                "activity_trace.db"
            )
                .openHelperFactory(factory)
                .addCallback(SEED_DEFAULTS_CALLBACK)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .build()
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

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_captured_items_app_package_content_type_text_timestamp
                    ON captured_items(app_package, content_type, text, timestamp)
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captured_items ADD COLUMN content_hash TEXT")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_captured_items_content_hash
                    ON captured_items(content_hash)
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFtsIndex(db)
                db.execSQL(
                    """
                    INSERT INTO `captured_items_fts` (`rowid`, `text`, `app_name`)
                    SELECT `id`, `text`, `app_name` FROM `captured_items`
                    """.trimIndent()
                )
            }
        }

        private fun createFtsIndex(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `captured_items_fts`
                USING fts5(`text`, `app_name`, content=`captured_items`, content_rowid=`id`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS `captured_items_fts_ai`
                AFTER INSERT ON `captured_items`
                BEGIN
                    INSERT INTO `captured_items_fts` (`rowid`, `text`, `app_name`)
                    VALUES (new.`id`, new.`text`, new.`app_name`);
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS `captured_items_fts_ad`
                AFTER DELETE ON `captured_items`
                BEGIN
                    INSERT INTO `captured_items_fts` (`captured_items_fts`, `rowid`, `text`, `app_name`)
                    VALUES ('delete', old.`id`, old.`text`, old.`app_name`);
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS `captured_items_fts_au`
                AFTER UPDATE ON `captured_items`
                BEGIN
                    INSERT INTO `captured_items_fts` (`captured_items_fts`, `rowid`, `text`, `app_name`)
                    VALUES ('delete', old.`id`, old.`text`, old.`app_name`);
                    INSERT INTO `captured_items_fts` (`rowid`, `text`, `app_name`)
                    VALUES (new.`id`, new.`text`, new.`app_name`);
                END
                """.trimIndent()
            )
        }

        private val SEED_DEFAULTS_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createFtsIndex(db)
                seedDefaultBlocked(db)
            }
        }

        private fun seedDefaultBlocked(db: SupportSQLiteDatabase) {
            for (pkg in DEFAULT_BLOCKED) {
                db.execSQL("INSERT OR IGNORE INTO blocked_apps(app_package) VALUES('$pkg')")
            }
        }
    }
}
