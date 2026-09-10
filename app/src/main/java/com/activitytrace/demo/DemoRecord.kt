package com.activitytrace.demo

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.ContentHasher
import java.time.Duration
import java.time.Instant

/**
 * A declarative demo fixture. Timestamps are expressed relative to a
 * [referenceTime] so generation is fully deterministic and never uses
 * `Instant.now()` internally.
 *
 * [offset] is how long before the reference time the capture happened, e.g.
 * `Duration.ofDays(3)` or `Duration.ofMinutes(40)` for "today" items.
 */
data class DemoRecord(
    val appName: String,
    val appPackage: String,
    val contentType: String,
    val offset: Duration,
    val text: String,
) {
    fun toCapturedItem(
        referenceTime: Instant,
        demoDatasetId: String,
    ): CapturedItem = CapturedItem(
        text = text,
        appPackage = appPackage,
        appName = appName,
        contentType = contentType,
        timestamp = referenceTime.minus(offset).toEpochMilli(),
        contentHash = ContentHasher.hash(appPackage, contentType, text),
        demoDatasetId = demoDatasetId,
    )
}