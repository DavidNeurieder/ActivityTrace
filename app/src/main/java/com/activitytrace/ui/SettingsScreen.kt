package com.activitytrace.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.activitytrace.capture.AccessibilityCaptureService
import com.activitytrace.capture.FileIndexingWorker
import com.activitytrace.store.RetentionCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
                .navigationBarsPadding()
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
            FileIndexingSection(context, scope)
            Spacer(Modifier.height(24.dp))
            DataSection(context, scope, exportMessage, onExportMessage = { exportMessage = it })
            Spacer(Modifier.height(24.dp))
            AboutSection(context)
        }
    }
}

@Composable
private fun PermissionsSection(context: Context) {
    var notificationGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            notificationGranted = enabledPackages.contains(context.packageName)

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            val serviceName =
                "${context.packageName}/${AccessibilityCaptureService::class.java.name}"
            accessibilityGranted =
                enabledServices?.split(":")?.any { it.trim() == serviceName } == true
        }
    }

    Text(
        text = "Permissions",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Notification access", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (notificationGranted) "Granted" else "Not granted",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (notificationGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        )
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Accessibility service", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (accessibilityGranted) "Granted" else "Not granted",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (accessibilityGranted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Notification Access captures notifications in real time. " +
               "If blocked by Restricted Settings (common on F-Droid/sideloaded apps), " +
               "enable the Accessibility Service instead (Android 14+).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
private fun FileIndexingSection(context: Context, scope: CoroutineScope) {
    val prefs = context.getSharedPreferences("activity_trace", Context.MODE_PRIVATE)
    var directoryUris by remember {
        mutableStateOf(prefs.getStringSet(FileIndexingWorker.PREF_DIRECTORY_URIS, emptySet()) ?: emptySet())
    }
    var schedule by remember {
        mutableStateOf(prefs.getString(FileIndexingWorker.PREF_SCHEDULE, "never") ?: "never")
    }
    var lastRun by remember { mutableStateOf(prefs.getLong(FileIndexingWorker.PREF_LAST_RUN, 0L)) }
    var scanning by remember { mutableStateOf(false) }

    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val uriString = it.toString()
            if (uriString !in directoryUris) {
                val updated = directoryUris + uriString
                prefs.edit().putStringSet(FileIndexingWorker.PREF_DIRECTORY_URIS, updated).apply()
                directoryUris = updated
            }
        }
    }

    LaunchedEffect(schedule) {
        prefs.edit().putString(FileIndexingWorker.PREF_SCHEDULE, schedule).apply()
        when (schedule) {
            "daily" -> FileIndexingWorker.scheduleDaily(context)
            "never" -> FileIndexingWorker.cancelDaily(context)
        }
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        while (true) {
            delay(1000)
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("file_indexing_manual").get()
            if (infos.all { it.state.isFinished }) {
                scanning = false
                lastRun = System.currentTimeMillis()
                prefs.edit().putLong(FileIndexingWorker.PREF_LAST_RUN, lastRun).apply()
                break
            }
        }
    }

    Text(
        text = "Index Documents",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Folder list
            Text("Folders", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            if (directoryUris.isEmpty()) {
                Text(
                    text = "No folders selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                directoryUris.forEach { uriString ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = directoryDisplayName(context, uriString) ?: uriString,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val updated = directoryUris - uriString
                                prefs.edit().putStringSet(
                                    FileIndexingWorker.PREF_DIRECTORY_URIS, updated
                                ).apply()
                                directoryUris = updated
                            },
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove folder",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { directoryPicker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add folder")
            }
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Schedule
            Text("Schedule", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            val scheduleOptions = listOf("never" to "Never", "daily" to "Daily")
            scheduleOptions.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { schedule = value }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = schedule == value,
                        onClick = { schedule = value },
                    )
                    Spacer(Modifier.padding(start = 4.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Scan now
            Button(
                onClick = {
                    scanning = true
                    FileIndexingWorker.triggerNow(context)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !scanning && directoryUris.isNotEmpty(),
            ) {
                Text(if (scanning) "Scanning\u2026" else "Scan now")
            }

            if (!scanning && lastRun > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Last scan: ${formatDate(lastRun)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun directoryDisplayName(context: Context, uriString: String): String? {
    if (uriString.isBlank()) return null
    val uri = Uri.parse(uriString)
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val name = docId.substringAfter(":")
        if (name.isBlank()) uri.lastPathSegment else name
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}

private fun formatDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
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
