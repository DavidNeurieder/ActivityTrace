package com.activitytrace.demo

import com.activitytrace.model.CapturedItem
import com.activitytrace.store.ContentHasher
import java.time.Instant

/**
 * Event types reuse the content types ActivityTrace already captures instead of
 * inventing a second, incompatible model. The FTS index and every search filter
 * understand these strings natively.
 */
enum class DemoEventType(val contentType: String) {
    /** A notification shown by an app (PostPigeon email, Parcel Panic alert, …). */
    NOTIFICATION("notification"),

    /** A document opened/saved/managed (LibreCrate files). */
    DOCUMENT("page"),

    /** The user actively using an app (a screen capture). */
    APP_USAGE("screen"),

    /** Browsing / traveling / looking something up. */
    WEB_ACTIVITY("screen"),

    /** ActivityTrace itself recording a capture or a search. */
    SEARCH("screen"),
}

/**
 * A single declarative demo event. [timestamp] is absolute (from [DemoClock]),
 * identity is the stable [DemoAppId], and the final persistence representation
 * is the ordinary [CapturedItem] via [toCapturedItem].
 */
data class DemoEvent(
    val id: String,
    val timestamp: Instant,
    val app: DemoAppId,
    val type: DemoEventType,
    val text: String,
) {
    fun toCapturedItem(demoDatasetId: String): CapturedItem {
        val demoApp = DemoAppCatalog.byId(app)
        return CapturedItem(
            text = text,
            appPackage = demoApp.packageName,
            appName = demoApp.name,
            contentType = type.contentType,
            timestamp = timestamp.toEpochMilli(),
            contentHash = ContentHasher.hash(demoApp.packageName, type.contentType, text),
            demoDatasetId = demoDatasetId,
        )
    }
}