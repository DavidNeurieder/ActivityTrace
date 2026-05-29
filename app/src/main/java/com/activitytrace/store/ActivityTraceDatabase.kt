package com.activitytrace.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.activitytrace.model.CapturedItem
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [CapturedItem::class],
    version = 1,
    exportSchema = false,
)
abstract class ActivityTraceDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

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
                    .addCallback(FtsSetupCallback)
                    .build()
            } catch (e: Exception) {
                context.deleteDatabase("activity_trace.db")
                EncryptionManager.getOrCreateKey(context)
                throw e
            }
        }

        private val FtsSetupCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createFtsTable(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                createFtsTable(db)
            }

            private fun createFtsTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS captured_items_fts
                    USING fts5(text, app_package UNINDEXED, content_type UNINDEXED, content=captured_items)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_ai
                    AFTER INSERT ON captured_items BEGIN
                        INSERT INTO captured_items_fts(rowid, text, app_package, content_type)
                        VALUES (new.rowid, new.text, new.app_package, new.content_type);
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_ad
                    AFTER DELETE ON captured_items BEGIN
                        INSERT INTO captured_items_fts(captured_items_fts, rowid, text, app_package, content_type)
                        VALUES ('delete', old.rowid, old.text, old.app_package, old.content_type);
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS captured_items_fts_au
                    AFTER UPDATE ON captured_items BEGIN
                        INSERT INTO captured_items_fts(captured_items_fts, rowid, text, app_package, content_type)
                        VALUES ('delete', old.rowid, old.text, old.app_package, old.content_type);
                        INSERT INTO captured_items_fts(rowid, text, app_package, content_type)
                        VALUES (new.rowid, new.text, new.app_package, new.content_type);
                    END;
                    """.trimIndent()
                )
            }
        }
    }
}
