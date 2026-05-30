package com.activitytrace

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.activitytrace.ui.OnboardingScreen
import com.activitytrace.ui.SearchScreen
import com.activitytrace.ui.SearchViewModel
import com.activitytrace.ui.SettingsScreen
import com.activitytrace.ui.theme.ActivityTraceTheme

class MainActivity : ComponentActivity() {
    private enum class Screen { Search, Settings }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ActivityTraceApplication
        val viewModel = ViewModelProvider(
            this,
            SearchViewModel.Factory(app.searchEngine, app.captureDao, this)
        )[SearchViewModel::class.java]

        setContent {
            ActivityTraceTheme {
                val prefs = getSharedPreferences("activity_trace", Context.MODE_PRIVATE)
                var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
                var screen by remember { mutableStateOf(Screen.Search) }

                if (onboarded) {
                    when (screen) {
                        Screen.Search -> SearchScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { screen = Screen.Settings },
                        )
                        Screen.Settings -> SettingsScreen(
                            onBack = { screen = Screen.Search },
                        )
                    }
                } else {
                    OnboardingScreen(
                        onComplete = {
                            prefs.edit().putBoolean("onboarded", true).apply()
                            onboarded = true
                        },
                    )
                }
            }
        }
    }
}
