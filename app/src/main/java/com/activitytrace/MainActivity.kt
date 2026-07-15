package com.activitytrace

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.activitytrace.ui.OnboardingScreen
import com.activitytrace.ui.SearchScreen
import com.activitytrace.ui.SearchViewModel
import com.activitytrace.ui.SettingsScreen
import com.activitytrace.ui.BlockedAppsScreen
import com.activitytrace.ui.StatisticsScreen
import com.activitytrace.ui.theme.ActivityTraceTheme

class MainActivity : ComponentActivity() {
    private enum class Screen { Search, Settings, Statistics, BlockedApps }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ActivityTraceApplication
        val searchEngine = app.searchEngine

        setContent {
            ActivityTraceTheme {
                    if (searchEngine == null) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.db_init_failed),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.db_init_restart),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        return@ActivityTraceTheme
                    }

                val viewModel = ViewModelProvider(
                    this,
                    SearchViewModel.Factory(searchEngine, app)
                )[SearchViewModel::class.java]

                val prefs = getSharedPreferences("activity_trace", Context.MODE_PRIVATE)
                var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
                var screen by remember { mutableStateOf(Screen.Search) }
                var initialStatsAppPackage by remember { mutableStateOf<String?>(null) }
                var initialStatsQuery by remember { mutableStateOf("") }

                if (onboarded) {
                    when (screen) {
                        Screen.Search -> SearchScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { screen = Screen.Settings },
                            onNavigateToStatistics = { appPackage, query ->
                                initialStatsAppPackage = appPackage
                                initialStatsQuery = query
                                screen = Screen.Statistics
                            },
                        )
                        Screen.Statistics -> {
                            BackHandler {
                                screen = Screen.Search
                                initialStatsAppPackage = null
                                initialStatsQuery = ""
                            }
                            StatisticsScreen(
                                onBack = {
                                    screen = Screen.Search
                                    initialStatsAppPackage = null
                                    initialStatsQuery = ""
                                },
                                initialAppPackage = initialStatsAppPackage,
                                initialQuery = initialStatsQuery,
                            )
                        }
                        Screen.Settings -> {
                            BackHandler { screen = Screen.Search }
                            SettingsScreen(
                                onBack = { screen = Screen.Search },
                                onNavigateToBlockedApps = { screen = Screen.BlockedApps },
                            )
                        }
                        Screen.BlockedApps -> {
                            BackHandler { screen = Screen.Settings }
                            BlockedAppsScreen(
                                onBack = { screen = Screen.Settings },
                            )
                        }
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
