package com.activitytrace.capture

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AccessibilityCaptureService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val textExtractor = AccessibilityTextExtractor()
    private val privacyFilter = PrivacyFilter(
        SensitiveAppPolicy { CaptureIngestor.isBlocked(it) },
    )
    private val debouncer = CaptureLimits.EventDebouncer()
    private val rateLimiter = CaptureLimits.EventRateLimiter()

    private fun collectEventText(event: AccessibilityEvent, sourceNode: AccessibilityTextNode?): String {
        val source = sourceNode
        if (source != null) {
            return textExtractor.extract(source)
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

    private fun isToastEvent(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: return false
        return className.contains("Toast", ignoreCase = true) ||
                className.contains("PopupWindow", ignoreCase = true) ||
                event.packageName == "android"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: PrivacyFilter.UNKNOWN_PACKAGE
        if (pkg == packageName) return
        if (!rateLimiter.tryAcquire()) return

        val sourceNode = event.source?.let { AccessibilityNodeInfoNode(it) }
        try {
            val decision = privacyFilter.evaluate(pkg, sourceNode)
            if (decision != PrivacyDecision.ALLOW) {
                Log.d(TAG, "Blocked accessibility capture (pkg=$pkg, decision=$decision)")
                return
            }

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
                        scope.launch {
                            CaptureIngestor.ingest(
                                text = fullText,
                                appPackage = pkg,
                                appName = CaptureIngestor.resolveAppName(this@AccessibilityCaptureService, pkg),
                                contentType = "notification",
                                category = notification.category,
                            )
                        }
                    } else {
                        val collected = collectEventText(event, sourceNode)
                        if (collected.isBlank()) return
                        scope.launch {
                            CaptureIngestor.ingest(
                                text = collected,
                                appPackage = pkg,
                                appName = CaptureIngestor.resolveAppName(this@AccessibilityCaptureService, pkg),
                                contentType = "notification",
                            )
                        }
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    if (isToastEvent(event)) {
                        val collected = collectEventText(event, sourceNode)
                        if (collected.isBlank()) return
                        scope.launch {
                            CaptureIngestor.ingest(
                                text = collected,
                                appPackage = pkg,
                                appName = CaptureIngestor.resolveAppName(this@AccessibilityCaptureService, pkg),
                                contentType = "toast",
                            )
                        }
                        return
                    }
                    val collected = collectEventText(event, sourceNode)
                    if (collected.isBlank()) return
                    scope.launch {
                        CaptureIngestor.ingest(
                            text = collected,
                            appPackage = pkg,
                            appName = CaptureIngestor.resolveAppName(this@AccessibilityCaptureService, pkg),
                            contentType = "screen",
                        )
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val windowKey = "$pkg|${event.className}"
                    if (!debouncer.allow(windowKey)) return
                    if (isToastEvent(event)) {
                        val collected = collectEventText(event, sourceNode)
                        if (collected.isBlank()) return
                        scope.launch {
                            CaptureIngestor.ingest(
                                text = collected,
                                appPackage = pkg,
                                appName = CaptureIngestor.resolveAppName(this@AccessibilityCaptureService, pkg),
                                contentType = "toast",
                            )
                        }
                    }
                }
            }
        } finally {
            sourceNode?.release()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "AccessibilityCaptureService"
    }
}