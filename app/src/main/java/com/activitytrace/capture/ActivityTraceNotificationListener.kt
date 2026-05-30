package com.activitytrace.capture

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ActivityTraceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("DEPRECATION")
    private fun resolveAppName(pkg: String): String? {
        return try {
            val ai = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getApplicationInfo(pkg, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                packageManager.getApplicationInfo(pkg, 0)
            }
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
        val summaryText = extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString()
        if (text == null && title == null && bigText == null && subText == null && summaryText == null) return
        val fullText = listOfNotNull(title, text, subText, bigText, summaryText).joinToString(" — ")
        val serialized = sbn.notification.contentIntent?.serialize()
        scope.launch {
            CaptureIngestor.ingest(
                text = fullText,
                appPackage = sbn.packageName,
                appName = resolveAppName(sbn.packageName),
                contentType = "notification",
                metadata = serialized,
                category = sbn.notification.category,
                timestamp = sbn.postTime,
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
