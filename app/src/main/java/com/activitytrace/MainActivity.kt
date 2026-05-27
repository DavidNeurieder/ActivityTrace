package com.activitytrace

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.activitytrace.ui.OnboardingScreen
import com.activitytrace.ui.SearchScreen
import com.activitytrace.ui.SearchViewModel
import com.activitytrace.ui.theme.ActivityTraceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ActivityTraceApplication
        val viewModel = ViewModelProvider(
            this,
            SearchViewModel.Factory(app.searchEngine)
        )[SearchViewModel::class.java]

        setContent {
            ActivityTraceTheme {
                var onboarded by mutableStateOf(
                    getSharedPreferences("activity_trace", Context.MODE_PRIVATE)
                        .getBoolean("onboarded", false)
                )

                if (onboarded) {
                    SearchScreen(viewModel = viewModel)
                } else {
                    OnboardingScreen(
                        onComplete = {
                            getSharedPreferences("activity_trace", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("onboarded", true)
                                .apply()
                            onboarded = true
                        },
                    )
                }
            }
        }
    }
}
