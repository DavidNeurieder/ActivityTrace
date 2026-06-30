package com.activitytrace.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ActivityTraceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "ActivityTraceNL"
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
        val serialized = sbn.notification.contentIntent?.extractIntent()?.serialize()
        scope.launch {
            try {
                CaptureIngestor.ingest(
                    text = fullText,
                    appPackage = sbn.packageName,
                    appName = CaptureIngestor.resolveAppName(this@ActivityTraceNotificationListener, sbn.packageName),
                    contentType = "notification",
                    metadata = serialized,
                    category = sbn.notification.category,
                    timestamp = sbn.postTime,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
