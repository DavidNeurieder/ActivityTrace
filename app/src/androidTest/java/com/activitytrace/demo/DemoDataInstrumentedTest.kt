package com.activitytrace.demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.CaptureDao
import com.activitytrace.store.ContentHasher
import com.activitytrace.ui.AppIconResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * End-to-end demo lifecycle against the real (SQLCipher) database: the
 * generated records must flow through the exact production FTS5 search path,
 * and demo deletion must never reach real captures.
 *
 * Also the living search-quality contract for the showcase: the four storylines,
 * the per-app prefix resolution, and relevance ranking are asserted against
 * production search here — if the corpus drifts, this file fails.
 *
 * Every test runs against its own freshly created database, so a test can
 * never observe another test's rows. Teardown is still strictly scoped to the
 * demo datasets (defense in depth) — no test uses an unscoped delete.
 */
class DemoDataInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val passphrase = "demo-passphrase".toByteArray()
    private lateinit var database: ActivityTraceDatabase
    private lateinit var dao: CaptureDao
    private lateinit var repository: DemoDataRepository
    private lateinit var generator: DemoDataGenerator
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var dbFile: File

    private val referenceTime: Instant = Instant.parse("2026-03-10T18:00:00Z")

    private val showcaseSize: Int get() = generator.showcaseDataset().events.size

    @Before
    fun setUp() {
        val unique = System.nanoTime()
        dbFile = context.getDatabasePath("demo_test_$unique.db")
        dbFile.delete()
        database = ActivityTraceDatabase.buildDatabase(context, passphrase, dbFile.name)
        dao = database.captureDao()
        prefs = context.getSharedPreferences("demo_test_prefs_$unique", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        generator = DemoDataGenerator(
            captureDao = dao,
            database = database,
        )
        repository = DemoDataRepository.create(
            captureDao = dao,
            database = database,
            prefs = prefs,
            config = DemoDataConfig(referenceTime = referenceTime),
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            repository.clearDemoDataset(DemoDataScenario.SHOWCASE_DATASET_ID)
            repository.clearDemoDataset(DemoDataScenario.BENCHMARK_DATASET_ID)
            database.close()
            dbFile.delete()
            File("${dbFile.path}-wal").delete()
            File("${dbFile.path}-shm").delete()
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun generate_showcase_then_search_finds_demo_records() = runBlocking {
        val inserted = repository.generate(DemoDataScenario.SHOWCASE)

        assertEquals(showcaseSize, inserted)
        assertEquals(inserted, dao.countByDemoDatasetId(DemoDataScenario.SHOWCASE_DATASET_ID))

        val engine = SearchEngine(dao)
        val viennaResults = engine.search("Vienna").first()
        assertTrue("search must find Vienna records", viennaResults.isNotEmpty())
        assertTrue(
            "Vienna cluster should contain the trip docs",
            viennaResults.any { it.text.contains("Vienna Itinerary") },
        )
        assertTrue(
            "Vienna cluster should contain the currency confusion",
            viennaResults.any { it.text.contains("VIENNA USES EUROS") },
        )
        assertTrue(
            "every result must be marked as demo",
            viennaResults.all { it.demoDatasetId == DemoDataScenario.SHOWCASE_DATASET_ID },
        )

        val invoiceResults = engine.search("invoice").first()
        assertTrue("invoice corpus must include the incoming PDF", invoiceResults.any { it.text.contains("invoice-2026-041.pdf") })
        assertTrue(invoiceResults.any { it.text.contains("2026-042 is ready") })

        val chatterboxOnly = engine.search("in:chatterbox chat").first()
        assertTrue(chatterboxOnly.isNotEmpty())
        assertTrue("in:chatterbox filter must return only Chatterbox", chatterboxOnly.all { it.appPackage == DemoAppCatalog.byId(DemoAppId.CHATTERBOX).packageName })
    }

    @Test
    fun regenerate_does_not_double_the_dataset() = runBlocking {
        val first = repository.generate(DemoDataScenario.SHOWCASE)
        val second = repository.regenerate(DemoDataScenario.SHOWCASE)

        assertEquals(first, second)
        assertEquals(first, dao.countByDemoDatasetId(DemoDataScenario.SHOWCASE_DATASET_ID))
        assertEquals(0, dao.countByDemoDatasetId(DemoDataScenario.BENCHMARK_DATASET_ID))
    }

    @Test
    fun real_and_benchmark_survive_showcase_clear() = runBlocking {
        insertRealRecord()

        repository.generate(DemoDataScenario.SHOWCASE)
        repository.generate(DemoDataScenario.SEARCH_BENCHMARK)
        assertEquals(1 + showcaseSize + 1_000, dao.getAllItems().size)

        val removed = repository.clear(DemoDataScenario.SHOWCASE)

        assertEquals(showcaseSize, removed)
        assertEquals(0, dao.countByDemoDatasetId(DemoDataScenario.SHOWCASE_DATASET_ID))
        assertEquals(1_000, dao.countByDemoDatasetId(DemoDataScenario.BENCHMARK_DATASET_ID))

        val remaining = dao.getAllItems()
        assertEquals("benchmark + real must survive the clear", 1_001, remaining.size)
        assertTrue(
            "real record must survive and stay real",
            remaining.any { it.demoDatasetId == null && it.text.contains("mortgage") },
        )
        assertTrue(
            "no showcase record may remain",
            remaining.none { it.demoDatasetId == DemoDataScenario.SHOWCASE_DATASET_ID },
        )
    }

    @Test
    fun clearing_unknown_dataset_is_a_noop() = runBlocking {
        assertEquals("clearing a missing dataset must not throw", 0, repository.clearDemoDataset("showcase-v2"))

        repository.generate(DemoDataScenario.SHOWCASE)

        val removed = repository.clearDemoDataset("does-not-exist")
        assertEquals("unknown id must delete nothing", 0, removed)
        assertEquals(
            showcaseSize,
            dao.countByDemoDatasetId(DemoDataScenario.SHOWCASE_DATASET_ID),
        )
    }

    @Test
    fun benchmark_search_surfaces_recent_weak_match_beyond_bm25_cutoff() = runBlocking {
        repository.generate(DemoDataScenario.SEARCH_BENCHMARK)

        assertEquals(1_000, dao.countByDemoDatasetId(DemoDataScenario.BENCHMARK_DATASET_ID))

        val engine = SearchEngine(dao)
        val results = engine.search("zenith").first()

        assertTrue(
            "200 BM25 candidates must not push today's weak match out",
            results.any { it.text.contains("zenith sonic blueprint morning note") },
        )
        assertTrue(results.size >= 200)
    }

    @Test
    fun prefix_search_resolves_each_demo_app() = runBlocking {
        repository.generate(DemoDataScenario.SHOWCASE)
        val engine = SearchEngine(dao)

        mapOf(
            "chat" to DemoAppId.CHATTERBOX,
            "chatt" to DemoAppId.CHATTERBOX,
            "parc" to DemoAppId.PARCEL_PANIC,
            "parcel" to DemoAppId.PARCEL_PANIC,
            "meet" to DemoAppId.MEETING_MONSTER,
            "libre" to DemoAppId.LIBRECRATE,
            "curr" to DemoAppId.OFFLINE_CURRENCY_CONVERTER,
            "wand" to DemoAppId.WANDERLUST,
            "snac" to DemoAppId.SNACKTRACK,
            "budget" to DemoAppId.BUDGET_BUDDY,
            "aurora" to DemoAppId.ACTIVITY_TRACE,
            "maya" to DemoAppId.CHATTERBOX,
        ).forEach { (query, expected) ->
            val results = engine.search(query).first()
            assertTrue(
                "prefix search '$query' must surface ${expected.name}",
                results.any { it.appPackage == DemoAppCatalog.byId(expected).packageName },
            )
            assertTrue(
                "prefix search '$query' must only return demo records",
                results.all { it.demoDatasetId == DemoDataScenario.SHOWCASE_DATASET_ID },
            )
        }
    }

    @Test
    fun search_returns_relevant_ranked_results() = runBlocking {
        repository.generate(DemoDataScenario.SHOWCASE)
        val engine = SearchEngine(dao)

        val invoiceResults = engine.search("invoice").first()
        assertTrue(invoiceResults.isNotEmpty())
        assertTrue(
            "top results must all be invoice-relevant",
            invoiceResults.take(3).all { it.text.contains("invoice", ignoreCase = true) },
        )
    }

    @Test
    fun every_storyline_is_findable_and_isolated() = runBlocking {
        repository.generate(DemoDataScenario.SHOWCASE)
        val engine = SearchEngine(dao)

        // Story 1: Project Aurora.
        val aurora = engine.search("aurora").first()
        assertTrue("aurora story missing", aurora.isNotEmpty())
        assertTrue(aurora.any { it.text.contains("retrospective") })

        // Story 3: the parcel delivered to a neighbor.
        val parcel = engine.search("which neighbor").first()
        assertTrue("parcel story missing", parcel.isNotEmpty())
        assertTrue(parcel.any { it.text.contains("which neighbor??") })

        // Story 4: the keyboard spiral.
        val keyboard = engine.search("keyboard").first()
        assertTrue("keyboard story missing", keyboard.isNotEmpty())
        assertTrue(keyboard.any { it.text.contains("Cancelled: keyboard insurance") })

        // Negative isolation: no cross-app leakage from a term outside the corpus.
        val leaked = engine.search("whatsapp").first()
        assertTrue("forbidden term must not leak into the showcase", leaked.isEmpty())
    }

    @Test
    fun every_demo_app_has_icon() {
        DemoAppCatalog.all.forEach { app ->
            assertNotNull(
                "Demo app '${app.name}' has no bundled icon",
                AppIconResolver.resolveDrawable(context, app.iconRes)
            )
        }
    }

    private suspend fun insertRealRecord() {
        dao.insert(
            CapturedItem(
                text = "the real private conversation about the mortgage",
                appPackage = "com.example.real",
                contentType = "screen",
                timestamp = 1_700_000_000_000L,
                contentHash = ContentHasher.hash("com.example.real", "screen", "x"),
            )
        )
    }
}