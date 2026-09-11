package com.activitytrace.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.activitytrace.demo.DemoDataRepository
import com.activitytrace.demo.DemoDataScenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Workflow tests for the Demo Data screen driven by a fake repository — no
 * database involved. Covers EMPTY / BUSY / READY / error state transitions,
 * generate → regenerate (no doubling) → clear, and dataset metadata.
 */
class DemoDataScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fake: FakeDemoDataRepository

    @Before
    fun setUp() {
        fake = FakeDemoDataRepository()
        fake.generateDelayMs = 250
        val viewModel = DemoDataViewModel(fake)
        composeTestRule.setContent {
            MaterialTheme {
                DemoDataScreen(viewModel = viewModel, onBack = {})
            }
        }
    }

    @Test
    fun initial_state_shows_empty_and_generate() {
        composeTestRule.onNodeWithText("Demo data").assertExists()
        composeTestRule.onNodeWithText("No demo data installed").assertExists()
        composeTestRule.onNodeWithTag("demo_generate").assertExists()
        composeTestRule.onNodeWithTag("demo_regenerate").assertDoesNotExist()
        composeTestRule.onNodeWithTag("demo_clear").assertDoesNotExist()
    }

    @Test
    fun generate_flows_to_ready_state() {
        composeTestRule.onNodeWithTag("demo_generate").performClick()

        assertBusyVisible()
        waitForReady()

        composeTestRule.onNodeWithTag("demo_showcase_meta")
            .assertTextContains("141 captures · Version 1")
        composeTestRule.onAllNodesWithText("Showcase").assertCountEquals(1)
        composeTestRule.onNodeWithTag("demo_regenerate").assertExists()
        composeTestRule.onNodeWithTag("demo_clear").assertExists()
        composeTestRule.onNodeWithText("No demo data installed").assertDoesNotExist()
    }

    @Test
    fun regenerate_keeps_a_single_dataset() {
        generate()

        composeTestRule.onNodeWithTag("demo_regenerate").performClick()
        assertBusyVisible()
        waitForReady()

        composeTestRule.onNodeWithTag("demo_showcase_meta")
            .assertTextContains("141 captures · Version 1")
        assertEquals("regenerate must replace, not append", 141, fake.showcaseCountValue())
    }

    @Test
    fun clear_requires_confirmation_and_returns_to_empty() {
        generate()

        composeTestRule.onNodeWithTag("demo_clear").performClick()
        composeTestRule.onNodeWithText("Clear demo data?").assertExists()
        composeTestRule.onAllNodesWithText("This removes 141 fictional captures", substring = true)
            .assertCountEquals(1)

        composeTestRule.onNodeWithTag("demo_clear_confirm").performClick()
        waitUntilPresent(hasTestTag("demo_generate"))
        composeTestRule.onNodeWithTag("demo_showcase_meta").assertDoesNotExist()
        composeTestRule.onNodeWithText("No demo data installed").assertExists()
        assertEquals(0, fake.showcaseCountValue())
    }

    @Test
    fun error_state_is_visible_and_retry_works() {
        fake.throwOnNext = true

        composeTestRule.onNodeWithTag("demo_generate").performClick()
        waitUntilPresent(
            composeTestRule.onNodeWithText("Something went wrong while updating demo data."),
        )
        composeTestRule.onNodeWithText("Generating…").assertDoesNotExist()
        composeTestRule.onNodeWithTag("demo_generate").assertExists()

        composeTestRule.onNodeWithTag("demo_generate").performClick()
        waitForReady()
        composeTestRule.onNodeWithText("Something went wrong while updating demo data.").assertDoesNotExist()
        composeTestRule.onNodeWithTag("demo_showcase_meta").assertTextContains("141 captures · Version 1")
    }

    @Test
    fun dataset_information_shows_count_version_and_generated_date() {
        generate()

        composeTestRule.onNodeWithTag("demo_showcase_meta")
            .assertTextContains("141 captures · Version 1")
        composeTestRule.onNodeWithText("Generated on", substring = true).assertExists()
    }

    private fun generate() {
        composeTestRule.onNodeWithTag("demo_generate").performClick()
        waitForReady()
    }

    private fun waitForReady() {
        waitUntilPresent(composeTestRule.onNodeWithTag("demo_showcase_meta"))
    }

    private fun assertBusyVisible() {
        // The BusyState spinner animates indefinitely, and on slow (SW-rendered)
        // emulators the brief busy frame can be dropped between real frames.
        // Crank virtual frames through the test clock so the BUSY composition is
        // observed deterministically instead of racing the rasterizer.
        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.mainClock.advanceTimeBy(200)
            composeTestRule.onNodeWithText("Generating…").assertExists()
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    private fun waitUntilPresent(query: androidx.compose.ui.test.SemanticsNodeInteraction, timeoutMillis: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { query.assertExists() }.isSuccess
        }
    }

    private fun waitUntilPresent(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { composeTestRule.onNode(matcher).assertExists() }.isSuccess
        }
    }
}

private class FakeDemoDataRepository : DemoDataRepository {

    var generateDelayMs: Long = 0
    var throwOnNext: Boolean = false

    private val showcase = MutableStateFlow(0)
    private val benchmark = MutableStateFlow(0)
    private var showcaseGenerated: Long = -1L
    private var benchmarkGenerated: Long = -1L

    fun showcaseCountValue() = showcase.value

    override fun recordCount(scenario: DemoDataScenario): Flow<Int> = when (scenario) {
        DemoDataScenario.SHOWCASE -> showcase.asStateFlow()
        DemoDataScenario.SEARCH_BENCHMARK -> benchmark.asStateFlow()
    }

    override suspend fun generate(scenario: DemoDataScenario): Int {
        if (throwOnNext) {
            throwOnNext = false
            throw RuntimeException("boom")
        }
        if (generateDelayMs > 0) delay(generateDelayMs)
        when (scenario) {
            DemoDataScenario.SHOWCASE -> {
                showcase.value = 141
                showcaseGenerated = 1_752_500_000_000L
            }
            DemoDataScenario.SEARCH_BENCHMARK -> {
                benchmark.value = 1_000
                benchmarkGenerated = 1_752_500_000_000L
            }
        }
        return showcase.value
    }

    override suspend fun regenerate(scenario: DemoDataScenario): Int = generate(scenario)

    override suspend fun clear(scenario: DemoDataScenario): Int = when (scenario) {
        DemoDataScenario.SHOWCASE -> {
            showcase.value = 0
            showcaseGenerated = -1L
            141
        }
        DemoDataScenario.SEARCH_BENCHMARK -> {
            benchmark.value = 0
            benchmarkGenerated = -1L
            1_000
        }
    }

    override suspend fun clearDemoDataset(datasetId: String): Int =
        DemoDataScenario.forDatasetId(datasetId)?.let { clear(it) } ?: 0

    override fun lastGeneratedAt(scenario: DemoDataScenario): Long = when (scenario) {
        DemoDataScenario.SHOWCASE -> showcaseGenerated
        DemoDataScenario.SEARCH_BENCHMARK -> benchmarkGenerated
    }
}