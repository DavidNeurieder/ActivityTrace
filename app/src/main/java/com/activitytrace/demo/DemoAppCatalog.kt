package com.activitytrace.demo

import com.activitytrace.R

/**
 * The authoritative catalog: exactly ten apps, three owned by the user
 * (ActivityTrace, Offline Currency Converter, LibreCrate) and seven fictional
 * ones. Every demo event references an app from this catalog.
 */
object DemoAppCatalog {

    val all: List<DemoApp> = listOf(
        DemoApp(
            id = DemoAppId.ACTIVITY_TRACE,
            name = "ActivityTrace",
            packageName = "com.activitytrace",
            iconRes = R.drawable.demo_icon_activitytrace,
        ),
        DemoApp(
            id = DemoAppId.OFFLINE_CURRENCY_CONVERTER,
            name = "Offline Currency Converter",
            packageName = "com.offline.currency.converter",
            iconRes = R.drawable.demo_icon_currency,
        ),
        DemoApp(
            id = DemoAppId.LIBRECRATE,
            name = "LibreCrate",
            packageName = "org.librecrate",
            iconRes = R.drawable.demo_icon_librecrate,
        ),
        DemoApp(
            id = DemoAppId.CHATTERBOX,
            name = "Chatterbox",
            packageName = "demo.chatterbox",
            iconRes = R.drawable.demo_icon_chatterbox,
        ),
        DemoApp(
            id = DemoAppId.POSTPIGEON,
            name = "PostPigeon",
            packageName = "demo.postpigeon",
            iconRes = R.drawable.demo_icon_postpigeon,
        ),
        DemoApp(
            id = DemoAppId.PARCEL_PANIC,
            name = "Parcel Panic",
            packageName = "demo.parcelpanic",
            iconRes = R.drawable.demo_icon_parcel_panic,
        ),
        DemoApp(
            id = DemoAppId.SNACKTRACK,
            name = "SnackTrack",
            packageName = "demo.snacktrack",
            iconRes = R.drawable.demo_icon_snacktrack,
        ),
        DemoApp(
            id = DemoAppId.MEETING_MONSTER,
            name = "Meeting Monster",
            packageName = "demo.meetingmonster",
            iconRes = R.drawable.demo_icon_meeting_monster,
        ),
        DemoApp(
            id = DemoAppId.WANDERLUST,
            name = "Wanderlust",
            packageName = "demo.wanderlust",
            iconRes = R.drawable.demo_icon_wanderlust,
        ),
        DemoApp(
            id = DemoAppId.BUDGET_BUDDY,
            name = "Budget Buddy",
            packageName = "demo.budgetbuddy",
            iconRes = R.drawable.demo_icon_budget_buddy,
        ),
    )

    private val byId: Map<DemoAppId, DemoApp> = all.associateBy { it.id }

    fun byId(id: DemoAppId): DemoApp = requireNotNull(byId[id]) { "unknown DemoAppId $id" }

    fun byPackage(packageName: String): DemoApp? = all.firstOrNull { it.packageName == packageName }

    fun byName(name: String): DemoApp? = all.firstOrNull { it.name == name }
}