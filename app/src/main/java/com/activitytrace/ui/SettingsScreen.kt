package com.activitytrace.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.activitytrace.R
import com.activitytrace.capture.AccessibilityCaptureService
import com.activitytrace.capture.FileIndexingWorker
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.BackupImporter
import com.activitytrace.store.DataExporter
import com.activitytrace.store.DatabaseExporter
import com.activitytrace.store.ExportStatus
import com.activitytrace.store.RetentionCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToBlockedApps: () -> Unit = {},
    onNavigateToSavedSearches: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retentionDays by remember { mutableIntStateOf(RetentionCleanupWorker.getRetentionDays(context)) }
    var exportStatus by remember { mutableStateOf<ExportStatus?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_description))
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
                    RetentionCleanupWorker.triggerNow(context)
                },
            )
            Spacer(Modifier.height(24.dp))
            FileIndexingSection(context, scope)
            Spacer(Modifier.height(24.dp))
            DataSection(context, scope, exportStatus, onExportStatus = { exportStatus = it })
            Spacer(Modifier.height(24.dp))
            BlockedAppsSection(onNavigate = onNavigateToBlockedApps)
            Spacer(Modifier.height(24.dp))
            SavedSearchesSection(onNavigate = onNavigateToSavedSearches)
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
        text = stringResource(R.string.permissions_title),
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
                    Text(stringResource(R.string.notification_access), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (notificationGranted) stringResource(R.string.permission_granted)
                               else stringResource(R.string.permission_not_granted),
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
                    Text(stringResource(R.string.accessibility_service), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (accessibilityGranted) stringResource(R.string.permission_granted)
                               else stringResource(R.string.permission_not_granted),
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
        text = stringResource(R.string.restricted_settings_note),
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
        0 to stringResource(R.string.retention_always),
        7 to stringResource(R.string.retention_7d),
        30 to stringResource(R.string.retention_30d),
        90 to stringResource(R.string.retention_90d),
    )

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.first { it.first == selectedDays }.second

    Text(
        text = stringResource(R.string.retention_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true }
                        .semantics { testTag = "retention_dropdown" },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    options.forEach { (days, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelect(days)
                                expanded = false
                            },
                        )
                    }
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
        text = stringResource(R.string.index_documents),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Folder list
            Text(stringResource(R.string.folders_label), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            if (directoryUris.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_folders_selected),
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
                                contentDescription = stringResource(R.string.remove_folder),
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
                Text(stringResource(R.string.add_folder))
            }
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Schedule
            Text(stringResource(R.string.schedule_label), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            val scheduleOptions = listOf("never" to stringResource(R.string.schedule_never), "daily" to stringResource(R.string.schedule_daily))
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
                Text(if (scanning) stringResource(R.string.scanning) else stringResource(R.string.scan_now))
            }

            if (!scanning && lastRun > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.last_scan, formatDate(lastRun)),
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
    val zdt = java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
    return java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault()).format(zdt)
}

@Composable
private fun DataSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    exportStatus: ExportStatus?,
    onExportStatus: (ExportStatus?) -> Unit,
) {
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                onExportStatus(null)
                try {
                    val db = ActivityTraceDatabase.getInstance(context)
                    val count = BackupImporter.importFromBackup(context, uri, db.captureDao())
                    if (count > 0) {
                        val msg = context.resources.getQuantityString(R.plurals.imported_count, count, count)
                        onExportStatus(ExportStatus.Success(msg))
                    } else {
                        onExportStatus(ExportStatus.Info(context.getString(R.string.no_new_items)))
                    }
                } catch (_: Exception) {
                    onExportStatus(ExportStatus.Error(context.getString(R.string.import_failed)))
                }
            }
        }
    }

    Text(
        text = stringResource(R.string.data_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.backup_restore_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.backup_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        onExportStatus(null)
                        val result = DatabaseExporter.export(context)
                        onExportStatus(
                            if (result) ExportStatus.Success(context.getString(R.string.database_exported))
                            else ExportStatus.Error(context.getString(R.string.export_failed))
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_to_sqlite))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/vnd.sqlite3", "application/octet-stream"),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.restore_from_backup))
            }
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.export_formats_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.export_formats_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        onExportStatus(null)
                        val db = ActivityTraceDatabase.getInstance(context)
                        val result = DataExporter.exportToJson(context, db.captureDao())
                        onExportStatus(
                            if (result) ExportStatus.Success(context.getString(R.string.exported_as_json))
                            else ExportStatus.Error(context.getString(R.string.export_failed))
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_as_json))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        onExportStatus(null)
                        val db = ActivityTraceDatabase.getInstance(context)
                        val result = DataExporter.exportToCsv(context, db.captureDao())
                        onExportStatus(
                            if (result) ExportStatus.Success(context.getString(R.string.exported_as_csv))
                            else ExportStatus.Error(context.getString(R.string.export_failed))
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_as_csv))
            }
            if (exportStatus != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (exportStatus) {
                        is ExportStatus.Success -> exportStatus.message
                        is ExportStatus.Error -> exportStatus.message
                        is ExportStatus.Info -> exportStatus.message
                        is ExportStatus.Progress -> exportStatus.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (exportStatus) {
                        is ExportStatus.Success -> MaterialTheme.colorScheme.primary
                        is ExportStatus.Error -> MaterialTheme.colorScheme.error
                        is ExportStatus.Info -> MaterialTheme.colorScheme.error
                        is ExportStatus.Progress -> MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun BlockedAppsSection(onNavigate: () -> Unit) {
    Text(
        text = stringResource(R.string.blocked_apps_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() },
    ) {
        Text(
            text = stringResource(R.string.blocked_apps_description),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun SavedSearchesSection(onNavigate: () -> Unit) {
    Text(
        text = stringResource(R.string.saved_searches_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() },
    ) {
        Text(
            text = stringResource(R.string.saved_searches_description),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
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

