package com.activitytrace.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.activitytrace.MainActivity
import com.activitytrace.ui.theme.ThemeMode
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class ThemePersistenceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theme_selected_in_settings_survives_activity_recreation() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("theme_dropdown").performScrollTo().performClick()

        composeRule.onNodeWithText("OLED Black", useUnmergedTree = true).performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("theme_dropdown").assertExists()
        composeRule.onNodeWithText("OLED Black").assertExists()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun classSetUp() {
            val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("activity_trace", Context.MODE_PRIVATE)
                .edit().putBoolean("onboarded", true).remove("theme_mode").apply()
        }

        @AfterClass
        @JvmStatic
        fun classTearDown() {
            val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("activity_trace", Context.MODE_PRIVATE)
                .edit().remove("theme_mode").apply()
        }
    }
}