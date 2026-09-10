package com.activitytrace.demo

import java.time.Duration
import java.time.Instant
import java.util.Random

/**
 * Deterministic source of demo fixtures. The showcase list is readable source
 * code (the "living search-quality test corpus"), and the benchmark variant is
 * generated from a fixed seed so both content and ordering are reproducible.
 *
 * Timestamps are always expressed relative to the reference time — nothing in
 * this file consults the wall clock.
 */
object DemoRecordFactory {

    val SHOWCASE_RECORD_COUNT: Int get() = SHOWCASE.size

    /** Apps recur across the showcase so `in:<app>` filters produce results. */
    private const val GMAIL = "gmail"
    private const val SIGNAL = "signal"
    private const val WHATSAPP = "whatsapp"
    private const val CHROME = "chrome"
    private const val CALENDAR = "calendar"
    private const val MAPS = "maps"
    private const val FILES = "files"

    private val MINUTE = Duration.ofMinutes(1)
    private val HOUR = Duration.ofHours(1)
    private val DAY = Duration.ofDays(1)
    private fun minutes(m: Int) = MINUTE.multipliedBy(m.toLong())
    private fun hours(h: Int) = HOUR.multipliedBy(h.toLong())
    private fun days(d: Int) = DAY.multipliedBy(d.toLong())

    fun recordsFor(
        scenario: DemoDataScenario,
        config: DemoDataConfig,
    ): List<DemoRecord> = when (scenario) {
        DemoDataScenario.SHOWCASE -> SHOWCASE
        DemoDataScenario.SEARCH_BENCHMARK -> benchmarkRecords(
            count = 1_000,
            seed = config.seed,
        )
    }

    /** Large deterministic dataset for ranking/performance testing. */
    fun benchmarkRecords(
        count: Int = 1_000,
        seed: Long = 12345L,
    ): List<DemoRecord> {
        require(count >= 2) { "benchmark count must be at least 2" }
        val random = Random(seed)
        // The word banks are small so terms recur and produce a realistic BM25
        // distribution instead of a grep of unique tokens.
        val pronouns = listOf("my", "the", "a", "our", "their", "your")
        val subjects = listOf("kite", "lighthouse", "runnel", "cedar", "comet", "breeze", "quartz", "meadow")
        val verbs = listOf("flies", "stands", "reflects", "grows", "trails", "warms", "rings", "folds")
        val objects = listOf("lantern", "harbor", "stone wall", "meadow path", "northern sky", "orchard fence", "station roof", "river bank")
        val times = listOf("at dawn", "before noon", "after drizzle", "each evening", "by the weekend", "under a clear sky")

        val apps = listOf(
            Triple("gmail", GMAIL, "notification"),
            Triple("signal", SIGNAL, "notification"),
            Triple("whatsapp", WHATSAPP, "notification"),
            Triple("chrome", CHROME, "screen"),
            Triple("calendar", CALENDAR, "notification"),
            Triple("maps", MAPS, "screen"),
            Triple("files", FILES, "page"),
        )

        val results = ArrayList<DemoRecord>(count)

        // A dedicated cluster that saturates the 200-row BM25 candidate pool:
        // every record repeats the engineered term three times, so searching
        // for it returns 200+ strong candidates from "today" through old age.
        // One weak but fresh match at the end must still surface through the
        // recent-candidate pool (candidate-cutoff protection, demo §25).
        var clusterIndex = 0
        val clusterSize = if (count >= 400) 210 else (count / 2).coerceAtLeast(2)
        while (clusterIndex < clusterSize && results.size < count - 1) {
            val isWeakFreshMatch = clusterIndex == clusterSize - 1
            val age = if (isWeakFreshMatch) {
                Duration.ZERO // the "moderately weaker match from today"
            } else {
                days(clusterIndex % 89)
            }
            // Strong candidates repeat the engineered term twice; the weak but
            // fresh match only once, so it loses the BM25 top-200 race and must
            // be rescued by the recent-candidate pool (demo §25).
            val text = if (isWeakFreshMatch) {
                "zenith sonic blueprint morning note — cluster status update (record ${clusterIndex + 1})"
            } else {
                "zenith sonic blueprint repeated twice zenith sonic blueprint cluster record number ${clusterIndex + 1}"
            }
            results += DemoRecord(
                appName = "gmail",
                appPackage = GMAIL,
                contentType = "notification",
                offset = age,
                text = text,
            )
            clusterIndex++
        }

        while (results.size < count) {
            val (displayName, pkg, type) = apps[random.nextInt(apps.size)]
            val text = buildString {
                append(pronouns[random.nextInt(pronouns.size)])
                append(' ')
                append(subjects[random.nextInt(subjects.size)])
                append(' ')
                append(verbs[random.nextInt(verbs.size)])
                append(" past the ")
                append(objects[random.nextInt(objects.size)])
                append(' ')
                append(times[random.nextInt(times.size)])
                append(" (fixture #")
                append(results.size + 1)
                append(')')
            }
            val age = when (random.nextInt(10)) {
                0 -> Duration.ZERO
                1 -> hours(1 + random.nextInt(23))
                2 -> days(1)
                3, 4 -> days(2 + random.nextInt(6))      // 2-7 days
                5 -> days(8 + random.nextInt(7))          // 8-14 days
                6 -> days(15 + random.nextInt(16))        // 15-30 days
                7 -> days(31 + random.nextInt(30))        // 31-60 days
                else -> days(61 + random.nextInt(29))     // 61-89 days
            }
            results += DemoRecord(
                appName = displayName,
                appPackage = pkg,
                contentType = type,
                offset = age,
                text = text,
            )
        }
        return results
    }

    // ------------------------------------------------------------------
    // Showcase dataset: a coherent few weeks in the life of Alex Fischer.
    // ~140 records, reflecting recurring entities (Project Aurora, Vienna
    // trip, Café Isar, Hotel Danube, invoice 2026-041).
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // A "long document" (demo §12): a ~700-word fictional project email.
    // Built from repeated sentences so document length is controlled and
    // deterministic; BM25 doc-length normalization is exercised by it.
    // ------------------------------------------------------------------
    private val AURORA_RETROSPECTIVE_LONG_EMAIL: String = buildString {
        val paragraphs = listOf(
            "The Aurora 2.4 release shipped ahead of schedule and the customer feedback has been quietly positive. This retrospective captures what worked, what slowed us down, and what we want to change for the next cycle.",
            "The design review introduced a shared glossary that cut the number of back-and-forth questions between teams. Engineers reported that the API contract, once written in one place, made the migration from the legacy endpoint almost mechanical. The standing desk of work — documentation — finally stood on four legs instead of two.",
            "Infrastructure work consumed more time than planned. The database migration to the new schema was tested twice, rolled back once, and then shipped on the third attempt. We learned that staging data must be refreshed from production before each rehearsal, otherwise threshold bugs hide behind empty tables.",
            "Testing caught two regressions that would have reached customers. The first was a notification de-duplication edge case, the second a timestamp ordering defect in the search results. Both were fixed by small, focused patches rather than large feature branches.",
            "Release planning worked well when the deadline was explicit and the scope stayed small. The hard lesson is that every sprint needs an owner for the release checklist, because the checklist is only useful when one person keeps it honest.",
            "Customer feedback letters were clustered by topic automatically and the themes were clear: fast search, offline behavior, and predictable backup exports. None of the themes required new infrastructure; all of them required polishing existing paths.",
            "The remaining debt is the API documentation for the internal endpoints. We will fold that work into the next sprint rather than leaving it for the quiet weeks that never come. Bookmarking the wiki helped, but the wiki is only as good as its newest commit.",
            "For Aurora 3.0 the team proposes a smaller release train with weekly checkpoints, automated canary deployments, and a code freeze three days before the date. The Vienna trip week is excluded from the freeze window so nobody ships while travelling.",
        )
        for (paragraph in paragraphs) {
            append(paragraph)
            append(' ')
            append(paragraph) // double each paragraph to keep the doc comfortably long
            append(' ')
        }
    }

    private val SHOWCASE: List<DemoRecord> = buildList {

        // ---- Emails (Gmail, notifications) ------------------------------
        addAll(
            listOf(
                DemoRecord("Gmail", GMAIL, "notification", days(7), "Project Aurora: design review notes"),
                DemoRecord("Gmail", GMAIL, "notification", days(5), "Re: Project Aurora backlog grooming"),
                DemoRecord("Gmail", GMAIL, "notification", days(2), "Invoice 2026-041 is ready — payment due in 14 days"),
                DemoRecord("Gmail", GMAIL, "notification", days(3), AURORA_RETROSPECTIVE_LONG_EMAIL),
                DemoRecord("Gmail", GMAIL, "notification", days(6), "Meeting invitation: design review Thursday 10:00"),
                DemoRecord("Gmail", GMAIL, "notification", days(30), "Hotel Danube: booking confirmed for Vienna 21.–24.03."),
                DemoRecord("Gmail", GMAIL, "notification", days(29), "ÖBB ticket: Vienna return 24.03., train at 18:42 from platform 7"),
                DemoRecord("Gmail", GMAIL, "notification", minutes(220), "Your coffee grinder order has shipped"),
                DemoRecord("Gmail", GMAIL, "notification", days(4), "Standing desk pre-order confirmed"),
                DemoRecord("Gmail", GMAIL, "notification", hours(3), "Your running shoes arrive today"),
                DemoRecord("Gmail", GMAIL, "notification", days(10), "Wi-Fi bill for March is overdue"),
                DemoRecord("Gmail", GMAIL, "notification", days(45), "Subscription renewed: cloud storage"),
                DemoRecord("Gmail", GMAIL, "notification", days(8), "Payment received for invoice 2026-039"),
                DemoRecord("Gmail", GMAIL, "notification", hours(5), "Weekly summary from the Project Aurora board"),
                DemoRecord("Gmail", GMAIL, "notification", days(12), "Security alert: new sign-in from an iPhone"),
                DemoRecord("Gmail", GMAIL, "notification", days(16), "Please review the API migration plan"),
                DemoRecord("Gmail", GMAIL, "notification", days(20), "Customer feedback summary — Q1"),
                DemoRecord("Gmail", GMAIL, "notification", days(24), "Budget approval for the infrastructure upgrade"),
                DemoRecord("Gmail", GMAIL, "notification", days(40), "Gym membership renewal notice"),
            ),
        )

        // ---- Messages (Signal + WhatsApp, notifications) ----------------
        addAll(
            listOf(
                DemoRecord("Signal", SIGNAL, "notification", hours(2), "Maya: Are we still on for Café Isar Friday 19:30?"),
                DemoRecord("Signal", SIGNAL, "notification", days(1), "Jonas: The apartment entrance is fixed"),
                DemoRecord("Signal", SIGNAL, "notification", days(30), "Elena: I booked the hotel, it's Hotel Danube"),
                DemoRecord("Signal", SIGNAL, "notification", hours(4), "Daniel: The API migration is done"),
                DemoRecord("Signal", SIGNAL, "notification", days(1), "Maya: Send me the invoice from the printer"),
                DemoRecord("Signal", SIGNAL, "notification", days(1), "Family: Weekend cycling — meet at 9 by the Isar"),
                DemoRecord("Signal", SIGNAL, "notification", days(2), "Jonas: Dentist moved to Thursday 15:00"),
                DemoRecord("Signal", SIGNAL, "notification", hours(6), "Maya: Package from the shop is at the concierge"),
                DemoRecord("Signal", SIGNAL, "notification", days(4), "Elena: Happy birthday — see you for dinner"),
                DemoRecord("Signal", SIGNAL, "notification", days(1), "Daniel: Release is green, deploying tonight"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", days(29), "Maya: Look at the photos from the Vienna trip"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", days(28), "Jonas: Two tickets for the Prater wheel at 20:00"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", hours(7), "Elena: Your USB-C cable is in the drawer"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", hours(1), "Family: Dinner at 19:00 — don't be late"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", days(3), "Daniel: The standing desk arrived, it's huge"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", days(2), "Maya: The coffee grinder is a game changer"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", days(1), "Jonas: Invoice 2026-040 needs your signature"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", minutes(90), "Elena: Where did we put the Café Isar reservation?"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", days(28), "Daniel: Return ticket from Vienna — double-check the platform"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", days(2), "Family: Dentist appointment reminder for Mom"),
                DemoRecord("Signal", SIGNAL, "notification", days(5), "Maya: Did you call the plumber about the drip?"),
                DemoRecord("Signal", SIGNAL, "notification", days(27), "Jonas: The ÖBB app needs the new password"),
                DemoRecord("Signal", SIGNAL, "notification", days(30), "Elena: Hotel Danube has no free parking"),
                DemoRecord("Signal", SIGNAL, "notification", days(6), "Daniel: CI is failing again on flaky tests"),
                DemoRecord("Signal", SIGNAL, "notification", days(7), "Maya: Thanks for covering my shift Friday"),
                DemoRecord("Signal", SIGNAL, "notification", days(1), "Jonas: Bring a charger, the office ones are broken"),
                DemoRecord("Signal", SIGNAL, "notification", days(27), "Elena: The bakery near the Prater is amazing"),
                DemoRecord("Signal", SIGNAL, "notification", days(4), "Daniel: Merge the design review comments"),
                DemoRecord("Signal", SIGNAL, "notification", days(1), "Maya: The weekend cycling track is flooded"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", hours(8), "Jonas: Package tracking number is DHL123456"),
            ),
        )

        // ---- Emails again: the app-name-weighting triplet (§11) ---------
        addAll(
            listOf(
                DemoRecord("Gmail", GMAIL, "notification", hours(3), "Your invoice is ready"),
                DemoRecord("Signal", SIGNAL, "notification", hours(3), "Your invoice is ready"),
                DemoRecord("WhatsApp", WHATSAPP, "notification", hours(3), "Your invoice is ready"),
            ),
        )

        // ---- Browser captures (Chrome, screens) -------------------------
        addAll(
            listOf(
                DemoRecord("Chrome", CHROME, "screen", days(30), "Vienna travel guide — Prater attractions"),
                DemoRecord("Chrome", CHROME, "screen", days(29), "ÖBB timetable Munich–Vienna"),
                DemoRecord("Chrome", CHROME, "screen", days(1), "Project Aurora wiki — architecture overview"),
                DemoRecord("Chrome", CHROME, "screen", days(3), "Standing desk ergonomics guide"),
                DemoRecord("Chrome", CHROME, "screen", days(2), "How to dial in a coffee grinder"),
                DemoRecord("Chrome", CHROME, "screen", hours(5), "Running shoe size guide"),
                DemoRecord("Chrome", CHROME, "screen", days(50), "Munich apartment moving checklist"),
                DemoRecord("Chrome", CHROME, "screen", days(29), "Airport transfer Vienna–Hotel Danube"),
                DemoRecord("Chrome", CHROME, "screen", days(10), "Wi-Fi router configuration notes"),
                DemoRecord("Chrome", CHROME, "screen", days(12), "API documentation: FTS5 index options"),
                DemoRecord("Chrome", CHROME, "screen", days(1), "Cycling routes along the Isar"),
                DemoRecord("Chrome", CHROME, "screen", days(18), "Quarterly expense report template"),
                DemoRecord("Chrome", CHROME, "screen", days(4), "Dentist near the office — reviews"),
                DemoRecord("Chrome", CHROME, "screen", hours(6), "USB-C cable compatibility guide"),
                DemoRecord("Chrome", CHROME, "screen", days(60), "Invoice numbering best practices"),
                DemoRecord("Chrome", CHROME, "screen", days(7), "Release checklist template"),
                DemoRecord("Chrome", CHROME, "screen", hours(2), "Café Isar menu and opening hours"),
                DemoRecord("Chrome", CHROME, "screen", days(28), "Prater entrance tickets online"),
                DemoRecord("Chrome", CHROME, "screen", days(29), "Munich to Vienna by train — platform 7"),
                DemoRecord("Chrome", CHROME, "screen", days(5), "Standing desk warranty card"),
            ),
        )

        // ---- Calendar (notifications) -----------------------------------
        addAll(
            listOf(
                DemoRecord("Calendar", CALENDAR, "notification", hours(1), "Design review at 10:00 (Project Aurora)"),
                DemoRecord("Calendar", CALENDAR, "notification", days(30), "Vienna trip: 21.03.–24.03."),
                DemoRecord("Calendar", CALENDAR, "notification", days(2), "Dentist — Thursday 15:00"),
                DemoRecord("Calendar", CALENDAR, "notification", hours(3), "Team lunch at Café Isar"),
                DemoRecord("Calendar", CALENDAR, "notification", days(1), "Release day: ship Aurora 2.4"),
                DemoRecord("Calendar", CALENDAR, "notification", days(1), "Weekend cycling — Sat 09:00"),
                DemoRecord("Calendar", CALENDAR, "notification", hours(5), "Gym — Mon/Wed/Fri 18:30"),
                DemoRecord("Calendar", CALENDAR, "notification", days(6), "Standing desk return window ends"),
                DemoRecord("Calendar", CALENDAR, "notification", days(29), "Flight Munich → Vienna 20:45"),
                DemoRecord("Calendar", CALENDAR, "notification", days(15), "Quarterly planning — Q2 goals"),
            ),
        )

        // ---- Maps / travel (screens) ------------------------------------
        addAll(
            listOf(
                DemoRecord("Maps", MAPS, "screen", minutes(40), "Directions to Café Isar — 12 min"),
                DemoRecord("Maps", MAPS, "screen", days(30), "Directions to Hotel Danube"),
                DemoRecord("Maps", MAPS, "screen", days(29), "Route preview: Munich → Vienna"),
                DemoRecord("Maps", MAPS, "screen", days(28), "Prater — walking directions"),
                DemoRecord("Maps", MAPS, "screen", days(1), "Nearby: bike repair station Isar"),
                DemoRecord("Maps", MAPS, "screen", days(29), "Airport hotel — check-in times"),
                DemoRecord("Maps", MAPS, "screen", days(1), "Traffic: Isar ramp closed today"),
                DemoRecord("Maps", MAPS, "screen", days(2), "Directions to the dentist"),
                DemoRecord("Maps", MAPS, "screen", days(50), "Charging stations near the Munich apartment"),
                DemoRecord("Maps", MAPS, "screen", hours(2), "Café Isar — saved to favorites"),
            ),
        )

        // ---- Documents (Files, pages) ------------------------------------
        addAll(
            listOf(
                DemoRecord("Files", FILES, "page", days(1), "Aurora-release-notes.pdf"),
                DemoRecord("Files", FILES, "page", days(2), "invoice-2026-041.pdf"),
                DemoRecord("Files", FILES, "page", days(50), "moving-checklist-munich.txt"),
                DemoRecord("Files", FILES, "page", days(7), "project-aurora-architecture.md"),
                DemoRecord("Files", FILES, "page", days(30), "vienna-itinerary.txt"),
                DemoRecord("Files", FILES, "page", days(18), "expense-report-q1.xlsx"),
                DemoRecord("Files", FILES, "page", days(25), "training-plan-cycling.md"),
                DemoRecord("Files", FILES, "page", days(48), "apartment-handover-notes.txt"),
                DemoRecord("Files", FILES, "page", days(7), "meeting-notes-design-review.md"),
                DemoRecord("Files", FILES, "page", days(21), "customer-letter-template.odt"),
                DemoRecord("Files", FILES, "page", days(2), "coffee-grinder-manual.pdf"),
                DemoRecord("Files", FILES, "page", days(10), "wifi-setup-guide.txt"),
                DemoRecord("Files", FILES, "page", days(45), "budget-2026-spreadsheet.xlsx"),
                DemoRecord("Files", FILES, "page", days(16), "api-migration-checklist.md"),
                DemoRecord("Files", FILES, "page", days(3), "birthday-gift-ideas.txt"),
            ),
        )

        // ---- Miscellaneous app notifications ---------------------------
        addAll(
            listOf(
                DemoRecord("Amazon", "amazon", "notification", hours(6), "Your package will be delivered today by 21:00"),
                DemoRecord("DHL", "dhl", "notification", hours(5), "Shipment DHL123456 is on its way"),
                DemoRecord("DB Navigator", "db", "notification", days(30), "Your 18:42 train to Vienna is on time — platform 7"),
                DemoRecord("Fitness", "fitness", "notification", days(1), "Weekly goal reached — 42 km this week"),
                DemoRecord("Calendar", CALENDAR, "notification", days(30), "Reminder: water the plants before the trip"),
                DemoRecord("Finance", "finance", "notification", days(2), "Monthly budget: 68% used"),
                DemoRecord("Deliveroo", "deliveroo", "notification", days(3), "Your order from Café Isar is confirmed"),
                DemoRecord("Deliveroo", "deliveroo", "notification", days(3), "Rider arrived — your food is at the door"),
                DemoRecord("Ing", "ing", "notification", days(3), "Payment approved: standing desk shop"),
                DemoRecord("Ing", "ing", "notification", days(9), "Salary received — March"),
                DemoRecord("DHL", "dhl", "notification", days(1), "Delivery attempt failed — rebook a slot"),
                DemoRecord("Amazon", "amazon", "notification", hours(4), "USB-C cable delivered to the parcel station"),
                DemoRecord("Weather", "weather", "notification", days(1), "Rain expected Saturday — cycling may be wet"),
                DemoRecord("Lufthansa", "lufthansa", "notification", days(29), "Check-in open: Munich → Vienna 20:45"),
                DemoRecord("Lufthansa", "lufthansa", "notification", days(29), "Flight VI201 departs 20:45, gate B12"),
                DemoRecord("Music", "music", "notification", days(5), "New album out — classical playlist waiting"),
                DemoRecord("News", "news", "notification", days(4), "Munich apartment prices keep rising"),
                DemoRecord("System", "system", "notification", days(1), "Battery low — 15% remaining"),
                DemoRecord("System", "system", "notification", days(1), "Backup completed successfully"),
                DemoRecord("Fitness", "fitness", "notification", hours(2), "Time to move — 500 steps this morning"),
                DemoRecord("Photos", "photos", "notification", days(29), "Photos from Vienna are synced"),
                DemoRecord("Shopping", "shopping", "notification", days(4), "The standing desk price dropped 12%"),
                DemoRecord("Shopping", "shopping", "notification", hours(3), "Your wish list: coffee grinder now in stock"),
                DemoRecord("Ing", "ing", "notification", days(2), "Invoice 2026-041 is due in 14 days"),
                DemoRecord("Parcel", "parcel", "notification", hours(6), "Package left at the concierge — pickup code 4471"),
                DemoRecord("Transit", "transit", "notification", days(6), "U-Bahn delay on line U1 — allow extra time"),
                DemoRecord("Health", "health", "notification", hours(2), "Sleep score: 78 — better than usual"),
                DemoRecord("Health", "health", "notification", days(1), "Daily walk: 8,412 steps"),
                DemoRecord("Meet", "meet", "notification", minutes(15), "15 minutes before: design review"),
                DemoRecord("Meet", "meet", "notification", days(1), "Recording of yesterday's sync is ready"),
                DemoRecord("Docs", "gmail", "notification", days(18), "You can now edit 'expense-report-q1.xlsx'"),
                DemoRecord("Browser", "chrome", "notification", days(12), "Password safe: review 3 entries"),
                DemoRecord("Browser", "chrome", "notification", days(30), "Bookmarks backup completed"),
                DemoRecord("Home", "home", "notification", days(1), "Washing machine finished"),
                DemoRecord("Home", "home", "notification", days(50), "Thermostat: away mode set for the Munich apartment"),
            ),
        )

        // ---- Archive (61-89 days, exercising the old end of the window) --
        addAll(
            listOf(
                DemoRecord("Gmail", GMAIL, "notification", days(75), "Security alert: suspicious sign-in attempt blocked"),
                DemoRecord("Gmail", GMAIL, "notification", days(78), "Your 2025 annual review is ready in the portal"),
                DemoRecord("Photos", "photos", "notification", days(80), "Photo memories: one year since the Lisbon trip"),
                DemoRecord("Calendar", CALENDAR, "notification", days(70), "Annual reminder: renew the VPN subscription"),
                DemoRecord("Finance", "finance", "notification", days(65), "Quarterly statement: Q1 2026"),
                DemoRecord("DHL", "dhl", "notification", days(74), "Parcel delivered to the packing station"),
                DemoRecord("News", "news", "notification", days(72), "Investors react to the latest central bank decision"),
            ),
        )

        // ---- Deliberate BM25 candidates (§10) ----------------------------
        addAll(
            listOf(
                DemoRecord("Signal", SIGNAL, "notification", days(7), "Project Aurora design review"),
                DemoRecord("Signal", SIGNAL, "notification", days(60), "Project Aurora Project Aurora Project Aurora design review"),
                DemoRecord("Signal", SIGNAL, "notification", hours(3), "Today's meeting covered Project Aurora, release planning, infrastructure, testing, deployment and customer feedback."),
                DemoRecord("Signal", SIGNAL, "notification", hours(2), "Tomorrow's lunch reservation is confirmed"),
            ),
        )
    }

}