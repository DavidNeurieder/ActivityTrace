package com.activitytrace.capture

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AccessibilityCaptureService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: "unknown"
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notification = event.parcelableData as? Notification
                if (notification != null) {
                    val extras = notification.extras ?: return
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                    if (title.isBlank() && text.isBlank()) return
                    scope.launch {
                        CaptureIngestor.ingest(
                            text = if (title.isNotEmpty()) "$title — $text" else text,
                            appPackage = pkg,
                            contentType = "notification",
                        )
                    }
                } else {
                    val text = event.text.joinToString(" ")
                    if (text.isBlank()) return
                    scope.launch {
                        CaptureIngestor.ingest(
                            text = text,
                            appPackage = pkg,
                            contentType = "notification",
                        )
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val text = event.text.joinToString(" ")
                if (text.isBlank()) return
                scope.launch {
                    CaptureIngestor.ingest(
                        text = text,
                        appPackage = pkg,
                        contentType = "page",
                    )
                }
            }
        }
    }

    override fun onInterrupt() {}
}
