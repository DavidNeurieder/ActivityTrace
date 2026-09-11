package com.activitytrace.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.activitytrace.R
import com.activitytrace.demo.DemoDataRepository
import com.activitytrace.demo.DemoDataScenario
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/** Entry card into the demo-data screen, shared by the Settings and the welcome screen. */
@Composable
fun DemoDataSection(
    onNavigate: () -> Unit,
    context: Context = LocalContext.current,
) {
    val showcaseCountFlow = remember(context) {
        runCatching {
            DemoDataRepository.getInstance(context)
                .recordCount(DemoDataScenario.SHOWCASE)
                .catch { emit(0) }
        }.getOrNull() ?: flowOf(0)
    }
    val showcaseCount by showcaseCountFlow.collectAsState(initial = 0)

    Text(
        text = stringResource(R.string.demo_data_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.demo_data_description),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (showcaseCount > 0) {
                    pluralStringResource(
                        R.plurals.demo_data_event_count,
                        showcaseCount,
                        showcaseCount,
                    )
                } else {
                    stringResource(R.string.demo_data_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.demo_data_separate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}