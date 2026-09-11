package com.activitytrace.demo

import com.activitytrace.R

/**
 * Stable identity for the ten apps in the demo universe. Identity is never the
 * display name: [DemoAppId] maps to a [DemoApp] which owns presentation.
 */
enum class DemoAppId {
    ACTIVITY_TRACE,
    OFFLINE_CURRENCY_CONVERTER,
    LIBRECRATE,
    CHATTERBOX,
    POSTPIGEON,
    PARCEL_PANIC,
    SNACKTRACK,
    MEETING_MONSTER,
    WANDERLUST,
    BUDGET_BUDDY,
}

/** One participant of the demo universe. */
data class DemoApp(
    val id: DemoAppId,
    val name: String,
    val packageName: String,
    val iconRes: Int,
)