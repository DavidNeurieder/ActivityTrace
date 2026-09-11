package com.activitytrace.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.MainActivity
import com.activitytrace.demo.DemoAppCatalog
import com.activitytrace.demo.DemoDataConfig
import com.activitytrace.demo.DemoDataGenerator
import com.activitytrace.demo.DemoDataRepository
import com.activitytrace.demo.DemoDataScenario
import com.activitytrace.store.ActivityTraceDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Deterministic screenshot automation for the F-Droid listing.
 *
 * Every scenario starts from the same canonical state: the showcase dataset
 * regenerated on the deterministic [DemoClock] window (timestamps are pinned,
 * never the wall clock), real device data untouched, light theme.
 *
 * Screenshots are only produced when the instrumentation is asked for them
 * (`-e screenshots true`), so this suite stays a no-op on normal CI runs.
 *
 * Run on an emulator with animations disabled:
 *   adb shell settings put global animator_duration_scale 0
 *   adb shell settings put global transition_animation_scale 0
 *   adb shell settings put global window_animation_scale 0
 *   ANDROID_SERIAL=emulator-xxxx ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.screenshots=true \
 *     -Pandroid.testInstrumentationRunnerArguments.screenshotsOutputDir=/sdcard/screenshots
 *   adb pull /sdcard/screenshots app/src/main/play/phoneScreenshots
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var outputDir: File

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue("screenshots requested via -e screenshots true", args.getString("screenshots") == "true")

        val dir = args.getString("screenshotsOutputDir")
        outputDir = if (dir != null) {
            File(dir)
        } else {
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        }
        outputDir.mkdirs()
    }

    @Test
    fun capture_the_seven_listing_screenshots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Canonical dataset: showcase only, on the deterministic demo clock.
        val config = DemoDataConfig()
        val db = ActivityTraceDatabase.getInstance(context)
        val repo = DemoDataRepository.create(db.captureDao(), db, context.getSharedPreferences(
            "activity_trace", Context.MODE_PRIVATE), config)
        val showcaseCount = DemoDataGenerator(db.captureDao(), db)
            .showcaseDataset().events.size
        runBlocking {
            repo.clearDemoDataset(DemoDataScenario.SHOWCASE_DATASET_ID)
            repo.clearDemoDataset(DemoDataScenario.BENCHMARK_DATASET_ID)
            repo.generate(DemoDataScenario.SHOWCASE)
        }
        val showcaseText = "$showcaseCount captures · Version 1"

        // Verify demo icons resolve: every catalog app must have a bundled icon.
        DemoAppCatalog.all.forEach { app ->
            assert(AppIconResolver.resolveDrawable(context, app.iconRes) != null) {
                "Demo app '${app.name}' has no bundled icon"
            }
        }

        // Deterministic UI state: light theme, onboarded.
        context.getSharedPreferences("activity_trace", Context.MODE_PRIVATE).edit()
            .putBoolean("onboarded", true)
            .putString("theme_mode", "LIGHT")
            .apply()
        // Relaunch so onboarding/theme state are applied before capturing.
        composeTestRule.activityRule.scenario.recreate()

        val search = composeTestRule.onNode(hasSetTextAction())

        // The view model survives recreate() with its query; a prior run may
        // also have persisted one. Start from a blank field every time.
        search.performTextClearance()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeTestRule.onNode(hasSetTextAction() and androidx.compose.ui.test.hasText("")).assertExists()
            }.isSuccess
        }

        // 01_history — recent activity list.
        android.util.Log.i("ST", "01 waiting")
        waitUntilPresent("Cancelled: keyboard insurance")
        composeTestRule.waitForIdle()
        capture("01_history")
        android.util.Log.i("ST", "01 done")

        // 02_search_vienna.
        android.util.Log.i("ST", "02 typing vienna")
        search.performTextClearance()
        search.performTextInput("Vienna")
        android.util.Log.i("ST", "02 waiting count")
        waitUntilPresent(" results")
        android.util.Log.i("ST", "02 waiting first result")
        waitUntilPresent("Arrive Vienna Central — 20:11")
        composeTestRule.waitForIdle()
        capture("02_search_vienna")
        android.util.Log.i("ST", "02 done")

        // 03_search_invoice.
        android.util.Log.i("ST", "03 typing invoice")
        search.performTextClearance()
        search.performTextInput("invoice")
        android.util.Log.i("ST", "03 waiting count")
        waitUntilPresent(" results")
        composeTestRule.waitForIdle()
        capture("03_search_invoice")
        android.util.Log.i("ST", "03 done")

        // 04_filtered_postpigeon.
        android.util.Log.i("ST", "04 typing")
        search.performTextClearance()
        search.performTextInput("invoice in:postpigeon")
        android.util.Log.i("ST", "04 waiting")
        waitUntilPresent(" results")
        composeTestRule.waitForIdle()
        capture("04_filtered_postpigeon")
        android.util.Log.i("ST", "04 done")

// 05_detail — long-press the top Vienna result to reveal its actions.
        android.util.Log.i("ST", "05 typing")
        search.performTextClearance()
        search.performTextInput("Vienna")
        waitForStableResults("Train boarded — platform 7, seat 31")
        repeat(5) { attempt ->
            try {
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithText("Train boarded — platform 7, seat 31", substring = true)
                    .performTouchInput { longClick() }
                return@repeat
            } catch (e: Throwable) {
                if (attempt == 4) throw e
                composeTestRule.waitForIdle()
            }
        }
        waitUntilPresent("Copy to clipboard")
        composeTestRule.waitForIdle()
        capture("05_detail")
        android.util.Log.i("ST", "05 done")

        // Dismiss the context dialog.
        pressBack()
        composeTestRule.waitForIdle()

        // 06_demo_data — via Settings.
        android.util.Log.i("ST", "06 clicking settings")
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        android.util.Log.i("ST", "06 scrolling")
        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Demo data"))
        android.util.Log.i("ST", "06 waiting Demo data")
        waitUntilPresent("Demo data")
        android.util.Log.i("ST", "06 clicking Demo data")
        composeTestRule.onAllNodesWithText("Demo data").onFirst().performClick()
        android.util.Log.i("ST", "06 waiting showcase")
        waitUntilPresent(showcaseText)
        composeTestRule.waitForIdle()
        capture("06_demo_data")
        android.util.Log.i("ST", "06 done")

        // Back to search results, then an empty result state.
        navigateBackToSearch()
        search.performTextClearance()
        search.performTextInput("quantum_penguin_7391")
        waitUntilPresent("No results for quantum_penguin_7391")
        composeTestRule.waitForIdle()
        capture("07_empty_search")
    }

    private fun navigateBackToSearch() {
        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            runCatching {
                if (composeTestRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1) {
                    return@runCatching true
                }
                val back = composeTestRule.onAllNodesWithContentDescription("Back", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
                if (back) {
                    composeTestRule.onNodeWithContentDescription("Back").performClick()
                    composeTestRule.waitForIdle()
                }
                false
            }.getOrDefault(false)
        }
    }

    @Suppress("DEPRECATION")
    private fun capture(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val target = File(outputDir, "$name.png")
        FileOutputStream(target).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG compress failed for $name" }
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendStatus(0, android.os.Bundle().apply { putString("screenshot", name) })
    }

    private fun waitUntilPresent(text: String, timeoutMillis: Long = 8_000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                runCatching {
                    composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
                }.isSuccess
            }
        } catch (e: Throwable) {
            val matches = composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size
            android.util.Log.e("WAIT_TREE", "timeout waiting for '$text', matches=$matches")
            if (matches == 0) {
                composeTestRule.onRoot().printToLog("WAIT_TREE")
            }
            throw e
        }
    }

    /**
     * The search pipeline re-emits while the query settles (typing keystrokes,
     * debounce, paging), so a result can appear and vanish between polls. Wait
     * until the result-count chip reports the same value for several consecutive
     * polls while [anchor] stays present — i.e. the final stable emission.
     */
    private fun waitForStableResults(anchor: String, timeoutMillis: Long = 12_000) {
        var stablePolls = 0
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                runCatching {
                    val anchorOk = composeTestRule.onAllNodesWithText(anchor, substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
                    val countOk = composeTestRule.onAllNodesWithText(" results", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
                    if (anchorOk && countOk) stablePolls++ else stablePolls = 0
                    stablePolls >= 3
                }.getOrDefault(false)
            }
        } catch (e: Throwable) {
            android.util.Log.e("WAIT_TREE", "timeout waiting for stable results, anchor='$anchor'")
            composeTestRule.onRoot().printToLog("WAIT_TREE")
            throw e
        }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            AccessibilityService.GLOBAL_ACTION_BACK)
    }
}