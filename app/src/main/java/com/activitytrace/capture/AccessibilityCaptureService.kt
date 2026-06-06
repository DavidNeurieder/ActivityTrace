package com.activitytrace.capture

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AccessibilityCaptureService : AccessibilityService() {
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

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val parts = mutableListOf<String>()
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        for (i in 0 until node.childCount) {
            parts.add(collectText(node.getChild(i)))
        }
        node.recycle()
        return parts.joinToString(" ")
    }

    private fun collectEventText(event: AccessibilityEvent): String {
        val source = event.source
        if (source != null) {
            return collectText(source)
        }
        val parts = mutableListOf<String>()
        event.text.joinToString(" ").takeIf { it.isNotBlank() }?.let { parts.add(it) }
        event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        for (i in 0 until event.recordCount) {
            val record = event.getRecord(i)
            record.text.joinToString(" ").takeIf { it.isNotBlank() }?.let { parts.add(it) }
            record.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }
        return parts.joinToString(" — ")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: "unknown"
        if (event == null) return
        if (pkg == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notification = event.parcelableData as? Notification
                if (notification != null) {
                    val extras = notification.extras ?: return
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                    val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
                    if (title.isBlank() && text.isBlank() && bigText == null && subText == null && summaryText == null) return
                    val fullText = listOfNotNull(title, text, subText, bigText, summaryText).joinToString(" — ")
                    val serialized = notification.contentIntent?.serialize()
                    scope.launch {
                        CaptureIngestor.ingest(
                            text = fullText,
                            appPackage = pkg,
                            appName = resolveAppName(pkg),
                            contentType = "notification",
                            metadata = serialized,
                            category = notification.category,
                        )
                    }
                } else {
                    val collected = collectEventText(event)
                    if (collected.isBlank()) return
                    scope.launch {
                        CaptureIngestor.ingest(
                            text = collected,
                            appPackage = pkg,
                            appName = resolveAppName(pkg),
                            contentType = "notification",
                        )
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val collected = collectEventText(event)
                if (collected.isBlank()) return
                scope.launch {
                    CaptureIngestor.ingest(
                        text = collected,
                        appPackage = pkg,
                        appName = resolveAppName(pkg),
                        contentType = "screen",
                    )
                }
            }
        }
    }

    override fun onInterrupt() {}
}
