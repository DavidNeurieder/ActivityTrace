package com.activitytrace.ui

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DismissValue
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.activitytrace.capture.deserializeToPendingIntent
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val contentTypeFilter by viewModel.contentTypeFilter.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Trace") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text("Search...") },
                singleLine = true,
            )

            FilterChipsRow(
                selected = contentTypeFilter,
                onSelect = { viewModel.setContentTypeFilter(it) },
            )

            if (results.isNotEmpty()) {
                Text(
                    text = "${results.size} results",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (results.isEmpty()) {
                    item {
                        EmptyState(query = query, filter = contentTypeFilter)
                    }
                } else {
                    val grouped = groupByDate(results)
                    grouped.forEach { (dateLabel, items) ->
                        item(key = dateLabel) {
                            Text(
                                text = "── $dateLabel ──",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items.forEach { captured ->
                            item(key = captured.id) {
                                val dismissState = rememberDismissState(
                                    confirmValueChange = {
                                        if (it == DismissValue.DismissedToStart || it == DismissValue.DismissedToEnd) {
                                            viewModel.deleteItem(captured)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Item deleted")
                                            }
                                            true
                                        } else false
                                    },
                                )
                                SwipeToDismiss(
                                    state = dismissState,
                                    background = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    },
                                    dismissContent = {
                                        ResultCard(
                                            item = captured,
                                            appName = resolveAppName(context, captured.appPackage),
                                            onClick = {
                                                openItem(context, captured.appPackage, captured.metadata)
                                            },
                                            onLongClick = {
                                                copyToClipboard(context, captured.text)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Copied to clipboard")
                                                }
                                            },
                                        )
                                    },
                                    modifier = Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemDialog(
            onDismiss = { showAddDialog = false },
            onSave = { text, pkg, type ->
                viewModel.addItem(text, pkg, type)
                showAddDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val filters = listOf(
        null to "All",
        "notification" to "Notifications",
        "clipboard" to "Clipboard",
        "manual" to "Manual",
        "page" to "Pages",
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { (type, label) ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ResultCard(
    item: CapturedItem,
    appName: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(),
    ) {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = contentTypeIcon(item.contentType),
                    contentDescription = item.contentType,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            },
            headlineContent = {
                Text(
                    text = item.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = "$appName • ${formatTimestampShort(item.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun EmptyState(query: String, filter: String?) {
    val subtitle = when {
        query.isNotEmpty() -> "No results for \"$query\""
        filter != null -> "No ${filter} items captured yet"
        else -> "No captured items yet"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onSave: (text: String, appPackage: String, contentType: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var appPackage by remember { mutableStateOf("") }
    var contentType by remember { mutableStateOf("manual") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    if (text.isBlank() && clipText.isNotBlank()) {
        text = clipText
    }

    val contentTypes = listOf("manual" to "Manual", "clipboard" to "Clipboard", "notification" to "Notification")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add entry") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Text content") },
                    singleLine = false,
                    maxLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = appPackage,
                    onValueChange = { appPackage = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Source app (e.g. com.example)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = contentTypes.first { it.first == contentType }.second,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        label = { Text("Content type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                    ) {
                        contentTypes.forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    contentType = type
                                    dropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text, appPackage.ifBlank { "manual" }, contentType) },
                enabled = text.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun groupByDate(items: List<CapturedItem>): List<Pair<String, List<CapturedItem>>> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val todayStart = calendar.timeInMillis

    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStart = calendar.timeInMillis

    calendar.add(Calendar.DAY_OF_YEAR, -6)
    val weekStart = calendar.timeInMillis

    val todayItems = mutableListOf<CapturedItem>()
    val yesterdayItems = mutableListOf<CapturedItem>()
    val weekItems = mutableListOf<CapturedItem>()
    val olderItems = mutableListOf<CapturedItem>()

    for (item in items) {
        when {
            item.timestamp >= todayStart -> todayItems.add(item)
            item.timestamp >= yesterdayStart -> yesterdayItems.add(item)
            item.timestamp >= weekStart -> weekItems.add(item)
            else -> olderItems.add(item)
        }
    }

    val groups = mutableListOf<Pair<String, List<CapturedItem>>>()
    if (todayItems.isNotEmpty()) groups.add("Today" to todayItems)
    if (yesterdayItems.isNotEmpty()) groups.add("Yesterday" to yesterdayItems)
    if (weekItems.isNotEmpty()) groups.add("This Week" to weekItems)
    if (olderItems.isNotEmpty()) groups.add("Older" to olderItems)
    return groups
}

private fun contentTypeIcon(type: String): ImageVector = when (type) {
    "notification" -> Icons.Default.Notifications
    "clipboard" -> Icons.Default.ContentPaste
    "page" -> Icons.Default.Description
    "manual" -> Icons.Default.Edit
    else -> Icons.Default.Description
}

@Suppress("DEPRECATION")
private fun resolveAppName(context: Context, pkg: String): String {
    return try {
        val ai = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            context.packageManager.getApplicationInfo(pkg, 0)
        }
        context.packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) {
        pkg
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("activity_trace", text))
}

private fun openItem(context: Context, appPackage: String, metadata: String? = null) {
    if (metadata != null) {
        val pi = metadata.deserializeToPendingIntent()
        if (pi != null) {
            try {
                pi.send(context, 0, null)
                return
            } catch (_: PendingIntent.CanceledException) {
            }
        }
    }
    val intent = context.packageManager.getLaunchIntentForPackage(appPackage)
    if (intent != null) context.startActivity(intent)
}

private fun formatTimestampShort(millis: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
