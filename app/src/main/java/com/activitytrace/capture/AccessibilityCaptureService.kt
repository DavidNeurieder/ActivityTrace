package com.activitytrace.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AccessibilityCaptureService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val text = event?.text?.joinToString(" ") ?: return
        if (text.isBlank()) return
        scope.launch {
            CaptureIngestor.ingest(
                text = text,
                appPackage = event?.packageName?.toString() ?: "unknown",
                contentType = "page",
            )
        }
    }

    override fun onInterrupt() {}
}
