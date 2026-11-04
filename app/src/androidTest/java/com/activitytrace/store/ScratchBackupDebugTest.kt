package com.activitytrace.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScratchBackupDebugTest {

    @Test
    fun debug_export_contents() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityTraceDatabase.resetForTesting()
        val db = context.getDatabasePath(ActivityTraceDatabase.DB_NAME)
        db.delete()
        File("${db.path}-wal").delete()
        File("${db.path}-shm").delete()

        val dao = ActivityTraceDatabase.getInstance(context).captureDao()
        val results = (1..60).map { i ->
            dao.insert(
                CapturedItem(
                    text = "stress_sentinel_$i",
                    appPackage = "com.stress",
                    appName = null,
                    contentType = "text",
                    category = null,
                    timestamp = i.toLong(),
                    metadata = null,
                )
            )
        }
        println("SCRATCH inserts: ${results.count { it != -1L }} of 60; ignored=${results.filter { it == -1L }}")
        println("SCRATCH count after insert: ${dao.getAllItems().size}")
        delay(500)
        println("SCRATCH count after 500ms: ${dao.getAllItems().size}")
        delay(2000)
        println("SCRATCH count after 2500ms: ${dao.getAllItems().size}")

        val backupFile = File(context.cacheDir, "scratch/backup.sqlite").also {
            it.parentFile?.mkdirs()
            it.delete()
        }
        DatabaseExporter.exportToPlainSqlite(context, backupFile)
        SQLiteDatabase.openDatabase(backupFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { pdb ->
            val texts = pdb.rawQuery("SELECT text FROM captured_items ORDER BY text", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            println("SCRATCH exported count: ${texts.size}")
            println("SCRATCH missing: ${(1..60).map { "stress_sentinel_$it" }.filter { it !in texts }}")
            val tables = pdb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            println("SCRATCH tables: $tables")
        }
    }
}
