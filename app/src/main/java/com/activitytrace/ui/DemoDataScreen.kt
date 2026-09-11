package com.activitytrace.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.activitytrace.R
import com.activitytrace.demo.DemoDataScenario
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoDataScreen(
    viewModel: DemoDataViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showcaseCount by viewModel.showcaseCount.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val lastGeneratedAt by viewModel.lastGeneratedAt.collectAsState()

    var confirmClearShowcase by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.demo_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_description),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.demo_data_description),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.demo_data_what_it_contains),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            if (busy) {
                BusyState(stringResource(R.string.demo_data_busy))
            } else if (showcaseCount > 0) {
                ReadyState(
                    count = showcaseCount,
                    lastGeneratedAt = lastGeneratedAt,
                    onRegenerate = viewModel::regenerateShowcase,
                    onClear = { confirmClearShowcase = true },
                )
            } else {
                EmptyState(onGenerate = viewModel::generateShowcase)
            }

            if (error) {
                Spacer(Modifier.height(12.dp))
                Row {
                    Text(
                        text = stringResource(R.string.demo_data_error_occurred),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = viewModel::consumeError) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }
    }

    if (confirmClearShowcase) {
        AlertDialog(
            onDismissRequest = { confirmClearShowcase = false },
            title = { Text(stringResource(R.string.demo_data_clear_showcase_title)) },
            text = {
                Text(pluralStringResource(R.plurals.demo_data_clear_removes, showcaseCount, showcaseCount))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearShowcase = false
                        viewModel.clearShowcase()
                    },
                    modifier = Modifier.testTag("demo_clear_confirm"),
                ) {
                    Text(stringResource(R.string.demo_data_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearShowcase = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BusyState(label: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun EmptyState(onGenerate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.demo_data_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onGenerate,
                modifier = Modifier.testTag("demo_generate"),
            ) {
                Text(stringResource(R.string.demo_data_generate))
            }
        }
    }
}

@Composable
private fun ReadyState(
    count: Int,
    lastGeneratedAt: Long,
    onRegenerate: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            DatasetHeader(DemoDataScenario.SHOWCASE.displayName)
            Spacer(Modifier.height(8.dp))
            DatasetMeta(
                count = count,
                datasetId = DemoDataScenario.SHOWCASE.datasetId,
                lastGeneratedAt = lastGeneratedAt,
                metaTag = "demo_showcase_meta",
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRegenerate,
                modifier = Modifier.testTag("demo_regenerate"),
            ) {
                Text(stringResource(R.string.demo_data_regenerate))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.testTag("demo_clear"),
            ) {
                Text(stringResource(R.string.demo_data_clear))
            }
        }
    }
}

@Composable
private fun DatasetHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DatasetMeta(
    count: Int,
    datasetId: String,
    lastGeneratedAt: Long,
    metaTag: String? = null,
) {
    val version = DemoDataScenario.datasetVersion(datasetId) ?: 1
    Text(
        text = stringResource(R.string.demo_data_dataset_meta, count, version),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        modifier = if (metaTag != null) Modifier.testTag(metaTag) else Modifier,
    )
    if (lastGeneratedAt > 0) {
        Spacer(Modifier.height(4.dp))
        val date = remember(lastGeneratedAt) {
            java.time.Instant.ofEpochMilli(lastGeneratedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
        Text(
            text = stringResource(R.string.demo_data_generated_on, date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}