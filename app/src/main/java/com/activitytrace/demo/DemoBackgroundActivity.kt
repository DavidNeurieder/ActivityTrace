package com.activitytrace.demo

import com.activitytrace.demo.DemoAppId.ACTIVITY_TRACE
import com.activitytrace.demo.DemoAppId.BUDGET_BUDDY
import com.activitytrace.demo.DemoAppId.CHATTERBOX
import com.activitytrace.demo.DemoAppId.MEETING_MONSTER
import com.activitytrace.demo.DemoAppId.POSTPIGEON
import com.activitytrace.demo.DemoAppId.SNACKTRACK
import com.activitytrace.demo.DemoAppId.WANDERLUST
import com.activitytrace.demo.DemoEventType.APP_USAGE
import com.activitytrace.demo.DemoEventType.NOTIFICATION
import com.activitytrace.demo.DemoEventType.SEARCH

/**
 * The mundane 30–40% of the dataset: opens, checks, browsing, reminders.
 * Nothing here drives a story — it just makes the timeline feel lived-in
 * instead of like a scripted comedy show.
 */
object DemoBackgroundActivity {

    fun events(): List<DemoEvent> {
        val s = object {
            private var counter = 0
            fun ev(day: Int, hour: Int, minute: Int, app: DemoAppId, type: DemoEventType, text: String) =
                DemoEvent(
                    id = "bg-%03d".format(counter++),
                    timestamp = DemoClock.at(day, hour, minute),
                    app = app,
                    type = type,
                    text = text,
                )
        }

        return listOf(
            // Day 1 — coffee, errands, exports.
            s.ev(0, 6, 35, ACTIVITY_TRACE, APP_USAGE, "ActivityTrace: capture session started"),
            s.ev(0, 8, 1, ACTIVITY_TRACE, APP_USAGE, "Blocked apps: 4 apps blocked this hour"),
            s.ev(0, 9, 24, ACTIVITY_TRACE, SEARCH, "Search: productive — 0 results. Maybe tomorrow."),
            s.ev(0, 9, 25, ACTIVITY_TRACE, SEARCH, "Search: coffee — 38 results. Priorities sound."),
            s.ev(0, 16, 12, ACTIVITY_TRACE, APP_USAGE, "Export: backup to .sqlite completed"),
            s.ev(0, 11, 48, POSTPIGEON, NOTIFICATION, "Lunch ordering: who wants pasta?"),
            s.ev(0, 16, 55, POSTPIGEON, NOTIFICATION, "Security digest for team-aurora"),
            s.ev(0, 17, 44, POSTPIGEON, NOTIFICATION, "Re: the office freezer is making noise"),
            s.ev(0, 12, 41, CHATTERBOX, NOTIFICATION, "team-aurora: lunch at the bistro downstairs"),
            s.ev(0, 12, 15, SNACKTRACK, NOTIFICATION, "Salad — the healthy one"),
            s.ev(0, 12, 17, SNACKTRACK, NOTIFICATION, "Fries — for balance"),
            s.ev(0, 16, 22, SNACKTRACK, NOTIFICATION, "Afternoon snack — granola bar"),
            s.ev(0, 19, 30, BUDGET_BUDDY, NOTIFICATION, "€5.60 — Office vending machine"),
            s.ev(0, 21, 41, BUDGET_BUDDY, NOTIFICATION, "€23.00 — 'Last pair' of sneakers, again"),
            s.ev(0, 20, 20, WANDERLUST, APP_USAGE, "Hotel Capri — saved to favorites"),

            // Day 2 — the road.
            s.ev(1, 9, 0, MEETING_MONSTER, NOTIFICATION, "Sync from the road — your mic is muted"),
            s.ev(1, 10, 12, MEETING_MONSTER, NOTIFICATION, "Project Aurora — 1:1 Priya/Alex"),
            s.ev(1, 10, 41, POSTPIGEON, NOTIFICATION, "ÖBB ticket: Vienna return 11.09., train 18:42"),

            // Day 3 — home again, parcel chaos winding down.
            s.ev(2, 7, 30, WANDERLUST, APP_USAGE, "Back in Munich — the apartment seems small"),
            s.ev(2, 16, 40, MEETING_MONSTER, NOTIFICATION, "Project Aurora — grip review"),
            s.ev(2, 17, 58, CHATTERBOX, NOTIFICATION, "team-aurora: whoever moved it, thank you"),
            s.ev(2, 18, 3, ACTIVITY_TRACE, APP_USAGE, "ActivityTrace: retention cleanup complete — 0 removed"),
        )
    }
}