package com.activitytrace.demo

import com.activitytrace.demo.DemoAppId.ACTIVITY_TRACE
import com.activitytrace.demo.DemoAppId.BUDGET_BUDDY
import com.activitytrace.demo.DemoAppId.CHATTERBOX
import com.activitytrace.demo.DemoAppId.LIBRECRATE
import com.activitytrace.demo.DemoAppId.MEETING_MONSTER
import com.activitytrace.demo.DemoAppId.OFFLINE_CURRENCY_CONVERTER
import com.activitytrace.demo.DemoAppId.PARCEL_PANIC
import com.activitytrace.demo.DemoAppId.POSTPIGEON
import com.activitytrace.demo.DemoAppId.SNACKTRACK
import com.activitytrace.demo.DemoAppId.WANDERLUST
import com.activitytrace.demo.DemoEventType.APP_USAGE
import com.activitytrace.demo.DemoEventType.NOTIFICATION
import com.activitytrace.demo.DemoEventType.SEARCH
import com.activitytrace.demo.DemoEventType.WEB_ACTIVITY
import com.activitytrace.model.CapturedItem

/**
 * The four interconnected storylines of the demo universe. Each story builder
 * is independent and returns its own events with deterministic ids, so every
 * story can be tested in isolation. The stories cross apps and people and are
 * deliberately funny in places — the final filename is the punchline.
 */
object DemoStories {

    private class Seq(private val prefix: String) {
        private var counter = 0

        fun ev(
            day: Int,
            hour: Int,
            minute: Int,
            app: DemoAppId,
            type: DemoEventType,
            text: String,
        ): DemoEvent = DemoEvent(
            id = "$prefix-%03d".format(counter++),
            timestamp = DemoClock.at(day, hour, minute),
            app = app,
            type = type,
            text = text,
        )
    }

    // ------------------------------------------------------------------
    // Project Aurora (day 1: work). ~700-word retrospective so BM25
    // doc-length normalization is exercised (the plan's long document).
    // ------------------------------------------------------------------
    private val AURORA_RETROSPECTIVE: String = buildString {
        val paragraphs = listOf(
            "Aurora 2.4 shipped ahead of schedule and the customer feedback has been quietly positive. This retrospective captures what worked, what slowed us down, and what we want to change next cycle.",
            "The design review introduced a shared glossary that killed most back-and-forth questions. Engineers reported the API contract, written once in one place, made the migration from the legacy endpoint almost mechanical. The documentation finally stood on four legs instead of two.",
            "Infrastructure consumed more time than planned. The schema migration was tested twice, rolled back once, and shipped on the third attempt. We learned staging data must be refreshed from production before each rehearsal, otherwise threshold bugs hide behind empty tables.",
            "Testing caught two regressions that would have reached customers: a notification de-duplication edge case, and a timestamp ordering defect in search results. Both were fixed by small, focused patches rather than large feature branches.",
            "Release planning worked when the deadline was explicit and the scope stayed small. Every sprint needs an owner for the release checklist, because a checklist is only useful when one person keeps it honest.",
            "Support letters clustered by topic automatically and the themes were clear: fast search, offline behavior, and predictable backup exports. None of the themes required new infrastructure; all of them required polishing existing paths.",
            "The remaining debt is the internal API documentation. We will fold it into the next sprint rather than leaving it for the quiet weeks that never come. The Vienna trip is excluded from the freeze window so nobody ships while travelling.",
            "For Aurora 3.0 the team proposes a smaller release train with weekly checkpoints, automated canaries, and a code freeze three days before the date. The verdict on FINAL FINAL FINAL titles is still out.",
        )
        for (paragraph in paragraphs) {
            append(paragraph)
            append(' ')
            append(paragraph) // double each paragraph to keep the doc comfortably long
            append(' ')
        }
    }

    fun projectAurora(): List<DemoEvent> {
        val s = Seq("aurora")
        return listOf(
            // Proposal arrives and survives several rounds.
            s.ev(0, 6, 41, POSTPIGEON, NOTIFICATION, "Project Aurora — proposal attached (from Priya)"),
            s.ev(0, 6, 42, POSTPIGEON, NOTIFICATION, "Re: Project Aurora — thanks everyone"),
            s.ev(0, 7, 33, POSTPIGEON, NOTIFICATION, "Invoice 2026-041 — payment due in 14 days"),
            s.ev(0, 8, 17, POSTPIGEON, NOTIFICATION, "Sam: quick question about the API slugs"),
            s.ev(0, 9, 2, POSTPIGEON, NOTIFICATION, "Re: Re: Re: Re: Quick question"),
            s.ev(0, 9, 31, POSTPIGEON, NOTIFICATION, "Project Aurora — feedback from the design review"),
            s.ev(0, 10, 5, POSTPIGEON, NOTIFICATION, "Aurora 2.4 staging expires Friday"),
            s.ev(0, 13, 14, POSTPIGEON, NOTIFICATION, "Re: Project Aurora — timeline confirmed"),
            s.ev(0, 14, 2, POSTPIGEON, NOTIFICATION, "Invoice 2026-042 is ready"),
            s.ev(0, 15, 37, POSTPIGEON, NOTIFICATION, "Project Aurora — minutes of today's sync"),
            s.ev(0, 18, 40, POSTPIGEON, NOTIFICATION, AURORA_RETROSPECTIVE),

            // The meeting spiral.
            s.ev(0, 8, 59, MEETING_MONSTER, NOTIFICATION, "Project Aurora — Weekly Sync"),
            s.ev(0, 9, 3, MEETING_MONSTER, NOTIFICATION, "Project Aurora — design review"),
            s.ev(0, 9, 45, MEETING_MONSTER, NOTIFICATION, "Project Aurora — 5 Minute Sync"),
            s.ev(0, 11, 30, MEETING_MONSTER, NOTIFICATION, "Interview debrief — Maya & Sam"),
            s.ev(0, 13, 30, MEETING_MONSTER, NOTIFICATION, "Project Aurora — backlog grooming"),
            s.ev(0, 15, 0, MEETING_MONSTER, NOTIFICATION, "Project Aurora — FINAL FINAL FINAL"),
            s.ev(0, 17, 20, MEETING_MONSTER, NOTIFICATION, "Retro: what went wrong this sprint"),

            // The document trail — the final filename is the joke.
            s.ev(0, 8, 12, LIBRECRATE, APP_USAGE, "Opened LibreCrate — newest first"),
            s.ev(0, 10, 41, LIBRECRATE, APP_USAGE, "LibreCrate: synced 4 files for Aurora"),
            s.ev(0, 14, 45, LIBRECRATE, APP_USAGE, "LibreCrate: rename war — v2-REAL vs really-final"),

            // Team banter drives the file chaos.
            s.ev(0, 7, 58, CHATTERBOX, NOTIFICATION, "Maya: Is this actually the final version?"),
            s.ev(0, 8, 3, CHATTERBOX, NOTIFICATION, "Maya: because I can already hear the client asking"),
            s.ev(0, 9, 12, CHATTERBOX, NOTIFICATION, "Priya: proposal looks good to me"),
            s.ev(0, 9, 16, CHATTERBOX, NOTIFICATION, "Sam: quick question — is the API paginated?"),
            s.ev(0, 10, 18, CHATTERBOX, NOTIFICATION, "Sam: quick question — does it run offline?"),
            s.ev(0, 10, 54, CHATTERBOX, NOTIFICATION, "Jonas: you SENT that to the client? lol"),
            s.ev(0, 11, 22, CHATTERBOX, NOTIFICATION, "Sam: quick question — what's a cursor?"),
            s.ev(0, 14, 33, CHATTERBOX, NOTIFICATION, "Maya: stale screenshots in the proposal"),
            s.ev(0, 15, 49, CHATTERBOX, NOTIFICATION, "Priya: demo is Friday, don't renumber pages again"),
            s.ev(0, 16, 58, CHATTERBOX, NOTIFICATION, "Sam: quick question — who owns the changelog?"),

            // ActivityTrace captures of the workday.
            s.ev(0, 6, 47, ACTIVITY_TRACE, SEARCH, "Search: aurora — 12 results"),
            s.ev(0, 7, 19, ACTIVITY_TRACE, SEARCH, "Search: invoice — 6 results"),
            s.ev(0, 12, 3, ACTIVITY_TRACE, SEARCH, "Capture: PostPigeon — Re: Re: Re: Re: Quick question"),
            s.ev(0, 14, 40, ACTIVITY_TRACE, SEARCH, "Search: maya — 9 results"),
            s.ev(0, 17, 2, ACTIVITY_TRACE, SEARCH, "Search: parcel — 0 results (yet)"),
        )
    }

    // ------------------------------------------------------------------
    // The Vienna trip (day 2) — including the questionable currency work.
    // ------------------------------------------------------------------
    fun viennaTrip(): List<DemoEvent> {
        val s = Seq("vienna")
        return listOf(
            // Planning on the eve of departure.
            s.ev(0, 18, 7, WANDERLUST, WEB_ACTIVITY, "Next stop: Vienna — packing list"),
            s.ev(1, 8, 19, POSTPIGEON, NOTIFICATION, "Vienna hotel confirmation — Hotel Capri"),
            s.ev(0, 21, 2, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "EUR → CHF — 1 EUR = 0.94 CHF"),
            s.ev(0, 21, 3, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "CHF → EUR — 1 CHF = 1.06 EUR (checking twice)"),

            // Travel day.
            s.ev(1, 6, 50, WANDERLUST, WEB_ACTIVITY, "Munich → Vienna — train at 18:42"),
            s.ev(1, 8, 10, WANDERLUST, NOTIFICATION, "Hotel Capri — booking confirmed"),
            s.ev(1, 9, 5, WANDERLUST, NOTIFICATION, "Departure — platform 4? it said platform 7"),
            s.ev(1, 10, 33, WANDERLUST, NOTIFICATION, "Arrive Vienna Central — 20:11"),
            s.ev(1, 12, 40, WANDERLUST, WEB_ACTIVITY, "Prater — walking directions"),
            s.ev(1, 13, 5, WANDERLUST, WEB_ACTIVITY, "Schnitzel map: your search is over"),
            s.ev(1, 14, 18, WANDERLUST, NOTIFICATION, "Hotel Capri — wifi password: Vienna2026"),
            s.ev(1, 16, 0, WANDERLUST, WEB_ACTIVITY, "Ring tram — route overview"),
            s.ev(1, 18, 42, WANDERLUST, NOTIFICATION, "Train boarded — platform 7, seat 31"),
            s.ev(1, 19, 40, WANDERLUST, WEB_ACTIVITY, "Stephansdom — tower queue 20 minutes"),
            s.ev(1, 21, 15, WANDERLUST, WEB_ACTIVITY, "Walk back to Hotel Capri — 2.4 km"),

            // The deliberate Swiss confusion.
            s.ev(1, 8, 30, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "EUR → CHF — 1 EUR = 0.94 CHF, still?"),
            s.ev(1, 10, 4, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "EUR → USD — 1 EUR = 1.09 USD"),
            s.ev(1, 12, 11, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "EUR → CZK — 1 EUR = 25.08 CZK"),
            s.ev(1, 14, 35, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "CHF → EUR — 1 CHF = 1.06 EUR (why?)"),

            // Food and budget on the road.
            s.ev(1, 8, 48, SNACKTRACK, NOTIFICATION, "Emergency Croissant — before the train"),
            s.ev(1, 12, 30, SNACKTRACK, NOTIFICATION, "Pretzel at the Naschmarkt"),
            s.ev(1, 14, 1, SNACKTRACK, NOTIFICATION, "Sachertorte — no regrets"),
            s.ev(1, 15, 10, SNACKTRACK, NOTIFICATION, "Coffee #3 — glazed"),
            s.ev(1, 17, 55, SNACKTRACK, NOTIFICATION, "Chocolate cake — it's a landmark"),
            s.ev(1, 8, 52, BUDGET_BUDDY, NOTIFICATION, "€7.80 — Travel food (on the train)"),
            s.ev(1, 12, 34, BUDGET_BUDDY, NOTIFICATION, "€31.70 — Lunch (remorse included)"),
            s.ev(1, 17, 58, BUDGET_BUDDY, NOTIFICATION, "€12.50 — Croissant, apparently"),
            s.ev(1, 22, 10, BUDGET_BUDDY, NOTIFICATION, "€4.20 — Sticker: 'I survived the Prater'"),

            // The friends who keep Alex honest.
            s.ev(1, 9, 47, CHATTERBOX, NOTIFICATION, "Maya: did you pack the adapter?"),
            s.ev(1, 10, 29, CHATTERBOX, NOTIFICATION, "Jonas: Prater at 20:00, be there"),
            s.ev(1, 19, 16, CHATTERBOX, NOTIFICATION, "Jonas: YOU KNOW VIENNA USES EUROS, RIGHT?"),

            // Captures for the travel day.
            s.ev(1, 8, 22, ACTIVITY_TRACE, SEARCH, "Capture: Wanderlust — Munich → Vienna"),
            s.ev(1, 11, 31, ACTIVITY_TRACE, SEARCH, "Capture: Offline Currency Converter — EUR → CHF"),
            s.ev(1, 14, 55, ACTIVITY_TRACE, SEARCH, "Search: schnitzel — 4 results"),
            s.ev(1, 19, 3, ACTIVITY_TRACE, SEARCH, "Capture: Wanderlust — Prater night ride"),
        )
    }

    // ------------------------------------------------------------------
    // Parcel Panic (day 3) — a small recurring subplot, not a takeover.
    // ------------------------------------------------------------------
    fun parcelIncident(): List<DemoEvent> {
        val s = Seq("parcel")
        return listOf(
            s.ev(2, 7, 48, PARCEL_PANIC, NOTIFICATION, "Package shipped — tracking PPN-7731"),
            s.ev(2, 8, 40, PARCEL_PANIC, NOTIFICATION, "Package arriving tomorrow — today"),
            s.ev(2, 9, 0, PARCEL_PANIC, NOTIFICATION, "Delivery attempted — you were elsewhere; a keyboard was rejected"),
            s.ev(2, 9, 50, PARCEL_PANIC, NOTIFICATION, "Package delayed — 'inspecting the label'"),
            s.ev(2, 10, 22, PARCEL_PANIC, NOTIFICATION, "Package delivered — to your neighbor"),
            s.ev(2, 15, 12, PARCEL_PANIC, WEB_ACTIVITY, "7 minutes staring at a truck on a map"),

            s.ev(2, 8, 55, CHATTERBOX, NOTIFICATION, "Maya: did you see the parcel update?"),
            s.ev(2, 9, 34, CHATTERBOX, NOTIFICATION, "Maya: which neighbor??"),
            s.ev(2, 9, 36, CHATTERBOX, NOTIFICATION, "Maya: I don't know."),
            s.ev(2, 10, 48, CHATTERBOX, NOTIFICATION, "Jonas: it's definitely the one with the dog"),
            s.ev(2, 9, 15, MEETING_MONSTER, NOTIFICATION, "Standup — cancelled (parcel incident)"),

            s.ev(2, 8, 4, ACTIVITY_TRACE, SEARCH, "Capture: Parcel Panic — delivery attempted"),
            s.ev(2, 9, 6, ACTIVITY_TRACE, SEARCH, "Search: parcel — 5 results"),
        )
    }

    // ------------------------------------------------------------------
    // Questionable spending (day 3) — snack escalation and a mystery order.
    // ------------------------------------------------------------------
    fun questionableSpending(): List<DemoEvent> {
        val s = Seq("spend")
        return listOf(
            s.ev(2, 12, 36, SNACKTRACK, NOTIFICATION, "Salad — redemption"),
            s.ev(2, 12, 38, SNACKTRACK, NOTIFICATION, "Large fries — oops"),
            s.ev(2, 14, 8, SNACKTRACK, NOTIFICATION, "Chocolate cake — the Friday special"),
            s.ev(2, 16, 15, SNACKTRACK, NOTIFICATION, "Croissant #2, obviously"),
            s.ev(2, 18, 20, SNACKTRACK, NOTIFICATION, "Emergency Croissant — for the parcel vigil"),

            s.ev(2, 10, 14, BUDGET_BUDDY, NOTIFICATION, "€89.00 — Electronics"),
            s.ev(2, 11, 36, BUDGET_BUDDY, NOTIFICATION, "€89.00 — I can explain (invoice for a keyboard)"),
            s.ev(2, 13, 55, BUDGET_BUDDY, NOTIFICATION, "€2.90 — Salad, plus €3.10 of fries"),
            s.ev(2, 18, 41, BUDGET_BUDDY, NOTIFICATION, "€0.00 — Cancelled: keyboard insurance"),

            s.ev(2, 10, 12, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "EUR → USD — 1 EUR = 1.10 USD"),
            s.ev(2, 11, 40, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "EUR → EUR — 1 EUR = 1.00 EUR. Correct."),
            s.ev(2, 13, 42, OFFLINE_CURRENCY_CONVERTER, APP_USAGE, "USD → EUR — 1 USD = 0.91 EUR"),

            s.ev(2, 9, 28, POSTPIGEON, NOTIFICATION, "A Bluetooth keyboard was ordered from your account??"),
            s.ev(2, 10, 13, POSTPIGEON, NOTIFICATION, "Receipt from Budget Buddy attached"),
            s.ev(2, 12, 55, CHATTERBOX, NOTIFICATION, "Priya: why did you buy a second keyboard?"),
            s.ev(2, 14, 6, CHATTERBOX, NOTIFICATION, "Sam: quick question — does Orbitz cover keyboard insurance?"),
            s.ev(2, 15, 22, CHATTERBOX, NOTIFICATION, "Maya: the keyboard arrives while you're at work"),

            s.ev(2, 10, 31, ACTIVITY_TRACE, SEARCH, "Capture: Budget Buddy — €89.00 Electronics"),
            s.ev(2, 12, 11, ACTIVITY_TRACE, SEARCH, "Search: keyboard — 4 results"),
            s.ev(2, 14, 22, ACTIVITY_TRACE, SEARCH, "Capture: LibreCrate — Things I absolutely did not buy.md"),
        )
    }
}