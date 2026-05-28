package com.activitytrace.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.activitytrace.store.RetentionCleanupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retentionDays by remember { mutableIntStateOf(RetentionCleanupWorker.getRetentionDays(context)) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PermissionsSection(context)
            Spacer(Modifier.height(24.dp))
            RetentionSection(
                selectedDays = retentionDays,
                onSelect = { days ->
                    retentionDays = days
                    RetentionCleanupWorker.setRetentionDays(context, days)
                },
            )
            Spacer(Modifier.height(24.dp))
            DataSection(context, scope, exportMessage, onExportMessage = { exportMessage = it })
            Spacer(Modifier.height(24.dp))
            AboutSection(context)
        }
    }
}

@Composable
private fun PermissionsSection(context: Context) {
    val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
    val isGranted = enabledPackages.contains(context.packageName)

    Text(
        text = "Permissions",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    )
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Notification access", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (isGranted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RetentionSection(
    selectedDays: Int,
    onSelect: (Int) -> Unit,
) {
    val options = listOf(
        0 to "Never",
        3 to "3 days",
        7 to "7 days",
        14 to "14 days",
        30 to "30 days",
    )

    Text(
        text = "Retention period",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            options.forEach { (days, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(days) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedDays == days,
                        onClick = { onSelect(days) },
                    )
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun DataSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    exportMessage: String?,
    onExportMessage: (String?) -> Unit,
) {
    Text(
        text = "Data",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            scope.launch {
                onExportMessage(null)
                val result = exportDatabase(context)
                onExportMessage(if (result) "Database exported" else "Export failed")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Export database")
    }
    if (exportMessage != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = exportMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (exportMessage.startsWith("Export failed"))
                MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AboutSection(context: Context) {
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
    }

    Text(
        text = "About",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "GPL-3.0-only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "github.com/DavidNeurieder/ActivityTrace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DavidNeurieder/ActivityTrace"))
                    context.startActivity(intent)
                },
            )
        }
    }
}

private suspend fun exportDatabase(context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        val dbFile = context.getDatabasePath("activity_trace.db")
        if (!dbFile.exists()) return@withContext false

        val exportDir = if (Build.VERSION.SDK_INT >= 29) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } ?: return@withContext false

        val appDir = File(exportDir, "ActivityTrace")
        appDir.mkdirs()

        val exportFile = File(appDir, "activity_trace.db")
        FileInputStream(dbFile).use { input ->
            FileOutputStream(exportFile).use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}
