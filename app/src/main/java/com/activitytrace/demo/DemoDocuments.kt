package com.activitytrace.demo

import com.activitytrace.demo.DemoEventType.DOCUMENT
import java.time.Instant

/**
 * The curated LibreCrate document collection. Every document appears in the
 * timeline as a `page` capture so the file collection corresponds to activity —
 * there are no documents that are never referenced.
 */
object DemoDocuments {

    data class DemoDocument(
        val fileName: String,
        val day: Int,
        val hour: Int,
        val minute: Int,
    )

    val documents: List<DemoDocument> = listOf(
        // Day 1 — Project Aurora.
        DemoDocument("Project Aurora Proposal.pdf", 0, 8, 28),
        DemoDocument("Project Aurora Notes.md", 0, 8, 44),
        DemoDocument("Project Aurora-final-v2.pdf", 0, 9, 40),
        DemoDocument("project-aurora-final-v7-really-final.pdf", 0, 10, 22),
        DemoDocument("invoice-2026-041.pdf", 0, 11, 2),
        DemoDocument("release-checklist.md", 0, 12, 21),
        DemoDocument("Project Aurora-final-v2-REAL.pdf", 0, 14, 12),
        DemoDocument("Things I Should Remember.md", 0, 17, 30),
        // Day 2 — Vienna.
        DemoDocument("Vienna Itinerary.pdf", 1, 8, 13),
        DemoDocument("Vienna translation cheat sheet.txt", 1, 12, 18),
        DemoDocument("Schnitzel authentication guide.pdf", 1, 15, 26),
        DemoDocument("Conference Ticket.pkpass", 1, 18, 54),
        // Day 3 — chaos.
        DemoDocument("Keyboard Warranty.pdf", 2, 9, 44),
        DemoDocument("Things I absolutely did not buy.md", 2, 13, 35),
        DemoDocument("Definitely Not Secret.txt", 2, 16, 11),
    )

    /** Every document as a [DemoEvent] with a deterministic id. */
    fun events(): List<DemoEvent> = documents.mapIndexed { i, doc ->
        DemoEvent(
            id = "doc-%03d".format(i),
            timestamp = Instant.from(DemoClock.at(doc.day, doc.hour, doc.minute)),
            app = DemoAppId.LIBRECRATE,
            type = DOCUMENT,
            text = doc.fileName,
        )
    }
}