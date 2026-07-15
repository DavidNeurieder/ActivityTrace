package com.activitytrace.capture

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.activitytrace.model.BlockedApp
import com.activitytrace.model.CapturedItem
import com.activitytrace.store.ActivityTraceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal val DEFAULT_BLOCKED = setOf(
    "com.android.systemui",
    "com.android.settings",
    "com.android.launcher3",
    "com.google.android.apps.nexuslauncher",
    "com.android.launcher",
    "com.google.android.inputmethod.latin",
    "com.android.inputmethod.latin",
)

object CaptureIngestor {
    private var db: ActivityTraceDatabase? = null
    private const val TAG = "CaptureIngestor"
    private const val DEDUP_COOLDOWN_MS = 2000L

    private val recentHashes = object : LinkedHashMap<String, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean {
            return size > 128
        }
    }

    private val blockedApps = mutableSetOf<String>()
    private var blockedAppsLoaded = false

    @Suppress("DEPRECATION")
    fun resolveAppName(context: Context, pkg: String): String? {
        return try {
            val ai = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getApplicationInfo(
                    pkg, PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                context.packageManager.getApplicationInfo(pkg, 0)
            }
            context.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            null
        }
    }

    fun init(ctx: Context) {
        db = ActivityTraceDatabase.getInstance(ctx.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            reloadBlockedApps()
        }
    }

    suspend fun reloadBlockedApps() {
        try {
            val dao = db?.blockedAppDao()
            if (dao != null) {
                val blocked = dao.getAllBlocked()
                synchronized(blockedApps) {
                    blockedApps.clear()
                    blockedApps.addAll(blocked.toSet())
                    blockedAppsLoaded = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load blocked apps", e)
        }
    }

    fun isBlocked(appPackage: String): Boolean {
        return synchronized(blockedApps) {
            blockedAppsLoaded && blockedApps.contains(appPackage)
        }
    }

    suspend fun ingest(
        text: String,
        appPackage: String,
        contentType: String,
        metadata: String? = null,
        appName: String? = null,
        category: String? = null,
        timestamp: Long? = null,
        imageBlob: ByteArray? = null,
    ) {
        if (!blockedAppsLoaded) {
            reloadBlockedApps()
        }
        if (isBlocked(appPackage)) return

        val key = "$appPackage|$contentType|$text"
        val now = System.currentTimeMillis()
        synchronized(recentHashes) {
            val lastSeen = recentHashes[key]
            if (lastSeen != null && now - lastSeen < DEDUP_COOLDOWN_MS) return@ingest
            recentHashes[key] = now
        }

        try {
            db?.captureDao()?.insert(
                CapturedItem(
                    text = text,
                    appPackage = appPackage,
                    appName = appName,
                    contentType = contentType,
                    category = category,
                    timestamp = timestamp ?: System.currentTimeMillis(),
                    metadata = metadata,
                    imageBlob = imageBlob,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ingest item (type=$contentType, pkg=$appPackage)", e)
        }
    }
}
