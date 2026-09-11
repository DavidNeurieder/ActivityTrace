package com.activitytrace.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.activitytrace.R
import com.activitytrace.capture.FileIndexingWorker
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.BackupEnvelope
import com.activitytrace.store.BackupImporter
import com.activitytrace.store.DataExporter
import com.activitytrace.store.DatabaseExporter
import com.activitytrace.store.EncryptedBackupExporter
import com.activitytrace.store.ExportStatus
import com.activitytrace.store.RestoreResult
import com.activitytrace.store.RetentionCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.Locale
import com.activitytrace.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToBlockedApps: () -> Unit = {},
    onNavigateToDemoData: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChanged: (ThemeMode) -> Unit = {},
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
            CaptureStatusSection(
                context,
                onBlockedAppsClick = onNavigateToBlockedApps,
            )
            Spacer(Modifier.height(24.dp))
            DemoDataSection(onNavigate = onNavigateToDemoData)
            Spacer(Modifier.height(24.dp))
            PrivacySection()
            Spacer(Modifier.height(24.dp))
            AppearanceSection(
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged,
            )
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
            AboutSection(context)
        }
    }
}

@Composable
private fun PrivacySection() {
    Text(
        text = stringResource(R.string.privacy_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.privacy_sensitive_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.privacy_sensitive_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.privacy_data_scope_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.privacy_data_scope_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system_default),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
        ThemeMode.OLED to stringResource(R.string.theme_oled_black),
    )

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.first { it.first == themeMode }.second

    Text(
        text = stringResource(R.string.appearance_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.appearance_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            contentDescription = stringResource(R.string.theme_select),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "theme_dropdown" },
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    options.forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onThemeModeChanged(mode)
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
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.retention_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordVisible by remember { mutableStateOf(false) }
    var backupPasswordError by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var restorePasswordVisible by remember { mutableStateOf(false) }
    var showPlaintextConfirm by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                onExportStatus(null)
                if (isEncryptedBackup(context, uri)) {
                    restorePassword = ""
                    restoreUri = uri
                } else {
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
                onClick = { showBackupDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_button))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    restoreLauncher.launch(
                        arrayOf("application/octet-stream", "application/vnd.sqlite3"),
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
                            if (result is ExportStatus.Success) ExportStatus.Success(context.getString(R.string.exported_as_json))
                            else result
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
                            if (result is ExportStatus.Success) ExportStatus.Success(context.getString(R.string.exported_as_csv))
                            else result
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

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(stringResource(R.string.backup_encrypted_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.backup_encrypted_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = {
                            backupPassword = it
                            if (it.isNotEmpty()) backupPasswordError = false
                        },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        placeholder = { Text(stringResource(R.string.backup_password_hint)) },
                        singleLine = true,
                        isError = backupPasswordError,
                        supportingText = if (backupPasswordError) {
                            { Text(stringResource(R.string.backup_password_required)) }
                        } else null,
                        visualTransformation = if (backupPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(
                                onClick = { backupPasswordVisible = !backupPasswordVisible },
                                modifier = Modifier.testTag("backup_password_visibility_toggle"),
                            ) {
                                Icon(
                                    imageVector = if (backupPasswordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (backupPasswordVisible) R.string.password_hide else R.string.password_show,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backup_unencrypted_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            showBackupDialog = false
                            showPlaintextConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.backup_to_sqlite))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (backupPassword.isBlank()) {
                            backupPasswordError = true
                        } else {
                            showBackupDialog = false
                            backupPasswordError = false
                            scope.launch {
                                onExportStatus(null)
                                val result = EncryptedBackupExporter.export(context, backupPassword.toCharArray())
                                onExportStatus(result)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.backup_encrypted_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text(stringResource(R.string.plaintext_export_cancel))
                }
            },
        )
    }

    if (showPlaintextConfirm) {
        AlertDialog(
            onDismissRequest = { showPlaintextConfirm = false },
            title = { Text(stringResource(R.string.backup_to_sqlite)) },
            text = { Text(stringResource(R.string.plaintext_export_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlaintextConfirm = false
                        scope.launch {
                            onExportStatus(null)
                            val result = DatabaseExporter.exportPlaintextDatabase(context)
                            onExportStatus(
                                if (result is ExportStatus.Success) ExportStatus.Success(context.getString(R.string.database_exported))
                                else result
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.plaintext_export_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaintextConfirm = false }) {
                    Text(stringResource(R.string.plaintext_export_cancel))
                }
            },
        )
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.restore_encrypted_action)) },
            text = {
                OutlinedTextField(
                    value = restorePassword,
                    onValueChange = { restorePassword = it },
                    label = { Text(stringResource(R.string.restore_password_hint)) },
                    singleLine = true,
                    visualTransformation = if (restorePasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(
                            onClick = { restorePasswordVisible = !restorePasswordVisible },
                            modifier = Modifier.testTag("restore_password_visibility_toggle"),
                        ) {
                            Icon(
                                imageVector = if (restorePasswordVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = stringResource(
                                    if (restorePasswordVisible) R.string.password_hide else R.string.password_show,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val password = restorePassword.toCharArray()
                        restoreUri = null
                        scope.launch {
                            onExportStatus(null)
                            val db = ActivityTraceDatabase.getInstance(context)
                            when (
                                val result = EncryptedBackupExporter.import(
                                    context, uri, password, db.captureDao(),
                                )
                            ) {
                                is RestoreResult.Success ->
                                    if (result.importedCount > 0) {
                                        val msg = context.resources.getQuantityString(
                                            R.plurals.imported_count,
                                            result.importedCount,
                                            result.importedCount,
                                        )
                                        onExportStatus(ExportStatus.Success(msg))
                                    } else {
                                        onExportStatus(ExportStatus.Info(context.getString(R.string.no_new_items)))
                                    }

                                is RestoreResult.InvalidBackup ->
                                    onExportStatus(ExportStatus.Error(result.reason))

                                is RestoreResult.Failed ->
                                    onExportStatus(ExportStatus.Error(result.reason))
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.restore_encrypted_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreUri = null }) {
                    Text(stringResource(R.string.plaintext_export_cancel))
                }
            },
        )
    }
}

/**
 * True when the picked file is an encrypted ActivityTrace backup (envelope magic
 * "ATBK" in the first bytes); false for plain SQLite exports or unreadable files.
 */
private suspend fun isEncryptedBackup(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val magic = ByteArray(4)
            if (input.read(magic) != magic.size) return@withContext false
            return@withContext String(magic, Charsets.US_ASCII) == BackupEnvelope.MAGIC
        } ?: false
    } catch (_: Exception) {
        false
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

