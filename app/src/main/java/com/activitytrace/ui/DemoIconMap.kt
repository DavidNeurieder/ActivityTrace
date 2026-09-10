package com.activitytrace.ui

import com.activitytrace.R

object DemoIconMap {
    private val map: Map<String, Int> = mapOf(
        "Gmail" to R.drawable.demo_icon_gmail,
        "Signal" to R.drawable.demo_icon_signal,
        "WhatsApp" to R.drawable.demo_icon_whatsapp,
        "Chrome" to R.drawable.demo_icon_chrome,
        "Calendar" to R.drawable.demo_icon_calendar,
        "Maps" to R.drawable.demo_icon_maps,
        "Files" to R.drawable.demo_icon_files,
        "Amazon" to R.drawable.demo_icon_amazon,
        "DHL" to R.drawable.demo_icon_dhl,
        "DB Navigator" to R.drawable.demo_icon_db,
        "Fitness" to R.drawable.demo_icon_fitness,
        "Finance" to R.drawable.demo_icon_finance,
        "Deliveroo" to R.drawable.demo_icon_deliveroo,
        "Ing" to R.drawable.demo_icon_ing,
        "Weather" to R.drawable.demo_icon_weather,
        "Lufthansa" to R.drawable.demo_icon_lufthansa,
        "Music" to R.drawable.demo_icon_music,
        "News" to R.drawable.demo_icon_news,
        "System" to R.drawable.demo_icon_system,
        "Photos" to R.drawable.demo_icon_photos,
        "Shopping" to R.drawable.demo_icon_shopping,
        "Parcel" to R.drawable.demo_icon_parcel,
        "Transit" to R.drawable.demo_icon_transit,
        "Health" to R.drawable.demo_icon_health,
        "Meet" to R.drawable.demo_icon_meet,
        "Home" to R.drawable.demo_icon_home,
        "Docs" to R.drawable.demo_icon_docs,
        "Browser" to R.drawable.demo_icon_browser,
    )

    fun resId(appName: String): Int? = map[appName]
}
