package com.activitytrace.capture

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ActivityTraceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val text = sbn.notification.extras
            ?.getCharSequence(android.app.Notification.EXTRA_TEXT)
            ?.toString()
        val title = sbn.notification.extras
            ?.getCharSequence(android.app.Notification.EXTRA_TITLE)
            ?.toString()
        if (text == null && title == null) return
        val fullText = listOfNotNull(title, text).joinToString(" — ")
        val serialized = sbn.notification.contentIntent?.serializeIntentSender()
        scope.launch {
            CaptureIngestor.ingest(
                text = fullText,
                appPackage = sbn.packageName,
                contentType = "notification",
                metadata = serialized,
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
