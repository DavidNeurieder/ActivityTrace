package com.activitytrace.ui

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.activitytrace.capture.deserializeToIntentSender
import com.activitytrace.capture.deserializeToPendingIntent
import com.activitytrace.model.CapturedItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding()) {
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
                                val canOpen = remember(captured.appPackage) {
                                    if (captured.metadata != null) true
                                    else {
                                        context.packageManager.getLaunchIntentForPackage(captured.appPackage) != null
                                        ||                                         context.packageManager.queryIntentActivities(
                                            Intent(Intent.ACTION_MAIN).apply { setPackage(captured.appPackage) },
                                            0,
                                        ).any { it.activityInfo.packageName == captured.appPackage }
                                    }
                                }
                                ResultCard(
                                    item = captured,
                                    appName = resolveAppName(context, captured.appPackage, captured.appName),
                                    canOpen = canOpen,
                                    onOpenApp = {
                                        if (!openItem(context, captured.appPackage, captured.metadata, captured.category)) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Could not open")
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        copyToClipboard(context, captured.text)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Copied to clipboard")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
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
        "screen" to "Accessibility",
        "page" to "Folders",
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
    canOpen: Boolean,
    onOpenApp: () -> Unit,
    onLongClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clearAndSetSemantics { }
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(),
    ) {
        val context = LocalContext.current
        val appIcon = if (item.appPackage != "local") {
            var cachedIcon by remember(item.appPackage) { mutableStateOf<ImageBitmap?>(null) }
            if (cachedIcon == null) {
                cachedIcon = try {
                    context.packageManager.getApplicationIcon(item.appPackage)
                        .toBitmap().asImageBitmap()
                } catch (_: Exception) { null }
            }
            cachedIcon
        } else null

        ListItem(
            leadingContent = {
                if (appIcon != null) {
                    Icon(
                        painter = BitmapPainter(appIcon),
                        contentDescription = item.contentType,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = contentTypeIcon(item.contentType),
                                contentDescription = item.contentType,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            },
            headlineContent = {
                Column {
                    Text(
                        text = item.text,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (expanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Show less",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            supportingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "$appName • ${formatTimestampShort(item.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (canOpen) {
                        IconButton(onClick = onOpenApp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open app",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
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

private fun Drawable.toBitmap(defaultSize: Int = 256): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val w = if (intrinsicWidth > 0) intrinsicWidth else defaultSize
    val h = if (intrinsicHeight > 0) intrinsicHeight else defaultSize
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

private fun contentTypeIcon(type: String): ImageVector = when (type) {
    "notification" -> Icons.Default.Notifications
    "screen" -> Icons.Default.Visibility
    "page" -> Icons.Default.Description
    else -> Icons.Default.Description
}

@Suppress("DEPRECATION")
private fun resolveAppName(context: Context, pkg: String, storedName: String? = null): String {
    if (storedName != null) return storedName
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

private fun openItem(context: Context, appPackage: String, metadata: String? = null, mimeType: String? = null): Boolean {
    if (appPackage == "local" && metadata != null) {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(metadata)
                type = mimeType
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
    if (metadata != null) {
        val pi = metadata.deserializeToPendingIntent()
        if (pi != null) {
            try {
                pi.send(context, 0, null)
                return true
            } catch (_: PendingIntent.CanceledException) {
            }
        }
        val intentSender = metadata.deserializeToIntentSender()
        if (intentSender != null) {
            try {
                context.startIntentSender(intentSender, null, 0, 0, 0)
                return true
            } catch (_: Exception) {
            }
        }
    }
    var intent = context.packageManager.getLaunchIntentForPackage(appPackage)
    if (intent != null) {
        context.startActivity(intent)
        return true
    }
    intent = Intent(Intent.ACTION_MAIN).apply {
        setPackage(appPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val ris = context.packageManager.queryIntentActivities(intent, 0)
    val ri = ris.firstOrNull { it.activityInfo.packageName == appPackage }
    if (ri != null) {
        intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
        context.startActivity(intent)
        return true
    }
    return false
}

private fun formatTimestampShort(millis: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}
