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
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.activitytrace.R
import com.activitytrace.capture.deserializeToIntent
import com.activitytrace.capture.deserializeToIntentSender
import com.activitytrace.capture.deserializeToPendingIntent
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.QueryParser
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val contentTypeFilter by viewModel.contentTypeFilter.collectAsState()
    val bookmarkedOnly by viewModel.bookmarkedOnly.collectAsState()
    val appFilter by viewModel.appFilter.collectAsState()
    val dateFilter by viewModel.dateFilter.collectAsState()
    val savedSearches by viewModel.savedSearches.collectAsState()
    val popularSearches by viewModel.popularSearches.collectAsState()
    val keywords = remember(query) {
        if (query.isBlank()) emptyList() else QueryParser.parse(query).keywords
    }
    val canOpenPackages by viewModel.canOpenPackages.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchFocused by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = {
                        viewModel.setBookmarkedFilter(!bookmarkedOnly)
                    }) {
                        Icon(
                            imageVector = if (bookmarkedOnly) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.filter_bookmarked),
                        )
                    }
                    IconButton(onClick = onNavigateToStatistics) {
                        Icon(Icons.Default.BarChart, contentDescription = stringResource(R.string.statistics_title))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.search_settings))
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
            Box {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .onFocusChanged { searchFocused = it.isFocused },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                )
                if (searchFocused && savedSearches.isNotEmpty()) {
                    SavedSearchesDropdown(
                        savedSearches = savedSearches,
                        popularSearches = popularSearches,
                        onSelect = { viewModel.onSavedSearchClick(it) },
                        onClearAll = { viewModel.clearSavedSearches() },
                        onDismiss = { searchFocused = false },
                    )
                }
            }

            FilterChipsRow(
                selected = if (bookmarkedOnly) "bookmarked" else contentTypeFilter,
                onSelect = { type ->
                    if (type == "bookmarked") {
                        viewModel.setBookmarkedFilter(!bookmarkedOnly)
                    } else {
                        viewModel.setContentTypeFilter(type)
                    }
                },
            )

            val distinctApps = remember(results) { viewModel.getDistinctAppPackages() }
            if (distinctApps.isNotEmpty()) {
                QuickAppChips(
                    apps = distinctApps,
                    selected = appFilter,
                    onSelect = { viewModel.setAppFilter(it) },
                )
            }

            DateFilterChips(
                selected = dateFilter,
                onToday = { viewModel.quickDateFilterToday() },
                onThisWeek = { viewModel.quickDateFilterThisWeek() },
                onThisMonth = { viewModel.quickDateFilterThisMonth() },
                onClear = { viewModel.setDateFilter(null) },
            )

            if (results.isNotEmpty()) {
                val resultCountText = if (results.size >= 100) {
                    context.resources.getQuantityString(R.plurals.result_count_capped, results.size, results.size)
                } else {
                    context.resources.getQuantityString(R.plurals.result_count, results.size, results.size)
                }
                Text(
                    text = resultCountText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            val todayLabel = stringResource(R.string.date_today)
            val yesterdayLabel = stringResource(R.string.date_yesterday)
            val thisWeekLabel = stringResource(R.string.date_this_week)
            val olderLabel = stringResource(R.string.date_older)
            val grouped = remember(results, todayLabel, yesterdayLabel, thisWeekLabel, olderLabel) {
                groupByDate(results, todayLabel, yesterdayLabel, thisWeekLabel, olderLabel)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (results.isEmpty()) {
                    item {
                        EmptyState(query = query, filter = if (bookmarkedOnly) "bookmarked" else contentTypeFilter)
                    }
                } else {
                    grouped.forEach { (dateLabel, items) ->
                        item(key = dateLabel) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items.forEach { captured ->
                            item(key = captured.id) {
                                val canOpen = captured.metadata != null || canOpenPackages[captured.appPackage] == true
                                ResultCard(
                                    item = captured,
                                    appName = resolveAppName(context, captured.appPackage, captured.appName),
                                    canOpen = canOpen,
                                    onOpenApp = {
                                        if (!openItem(context, captured.appPackage, captured.metadata, captured.category)) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.could_not_open))
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        copyToClipboard(context, captured.text)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.copied_to_clipboard))
                                        }
                                    },
                                    onToggleBookmark = { viewModel.toggleBookmark(captured) },
                                    keywords = keywords,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun SavedSearchesDropdown(
    savedSearches: List<String>,
    popularSearches: List<Pair<String, Int>>,
    onSelect: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(horizontal = 8.dp),
    ) {
        if (popularSearches.isNotEmpty()) {
            Text(
                text = "Popular",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            popularSearches.forEach { (term, count) ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(term, modifier = Modifier.weight(1f))
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onSelect(term); onDismiss() },
                )
            }
        }
        if (savedSearches.isNotEmpty()) {
            Text(
                text = stringResource(R.string.saved_searches_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            savedSearches.take(5).forEach { term ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(term)
                        }
                    },
                    onClick = { onSelect(term); onDismiss() },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.clear_saved_searches),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { onClearAll(); onDismiss() },
            )
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
        null to stringResource(R.string.filter_all),
        "notification" to stringResource(R.string.filter_notifications),
        "screen" to stringResource(R.string.filter_accessibility),
        "toast" to stringResource(R.string.filter_toast),
        "page" to stringResource(R.string.filter_folders),
        "bookmarked" to stringResource(R.string.filter_bookmarked),
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAppChips(
    apps: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.filter_all_apps)) },
            )
        }
        items(apps) { app ->
            val appName = app.substringAfterLast('.')
            FilterChip(
                selected = selected == app,
                onClick = { onSelect(if (selected == app) null else app) },
                label = { Text(appName) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterChips(
    selected: Pair<Long, Long>?,
    onToday: () -> Unit,
    onThisWeek: () -> Unit,
    onThisMonth: () -> Unit,
    onClear: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = onClear,
                label = { Text(stringResource(R.string.filter_all_time)) },
            )
        }
        item {
            FilterChip(
                selected = isTodayFilter(selected),
                onClick = onToday,
                label = { Text(stringResource(R.string.date_today)) },
            )
        }
        item {
            FilterChip(
                selected = isThisWeekFilter(selected),
                onClick = onThisWeek,
                label = { Text(stringResource(R.string.date_this_week)) },
            )
        }
        item {
            FilterChip(
                selected = isThisMonthFilter(selected),
                onClick = onThisMonth,
                label = { Text(stringResource(R.string.date_this_month)) },
            )
        }
    }
}

private fun isTodayFilter(range: Pair<Long, Long>?): Boolean {
    if (range == null) return false
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return range.first == cal.timeInMillis
}

private fun isThisWeekFilter(range: Pair<Long, Long>?): Boolean {
    if (range == null) return false
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return range.first == cal.timeInMillis
}

private fun isThisMonthFilter(range: Pair<Long, Long>?): Boolean {
    if (range == null) return false
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return range.first == cal.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ResultCard(
    item: CapturedItem,
    appName: String,
    canOpen: Boolean,
    onOpenApp: () -> Unit,
    onLongClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    keywords: List<String>,
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
                    val scanText = if (expanded) item.text else item.text.take(250)
                    val annotated = remember(scanText, keywords) { highlightText(scanText, keywords) }
                    Text(
                        text = annotated,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (expanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.show_less),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            supportingContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (item.imageBlob != null) {
                        val bitmap = remember(item.imageBlob) {
                            BitmapFactory.decodeByteArray(item.imageBlob, 0, item.imageBlob.size)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val supportText = remember(item.timestamp, appName, keywords) {
                            highlightText("$appName • ${formatTimestampShort(item.timestamp)}", keywords)
                        }
                        Text(
                            text = supportText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onToggleBookmark) {
                            Icon(
                                imageVector = if (item.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (item.isBookmarked) R.string.remove_bookmark else R.string.add_bookmark
                                ),
                                tint = if (item.isBookmarked) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (canOpen) {
                            IconButton(onClick = onOpenApp) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.open_app),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
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
        query.isNotEmpty() -> stringResource(R.string.empty_no_results, query)
        filter == "bookmarked" -> stringResource(R.string.empty_no_bookmarks)
        filter != null -> stringResource(R.string.empty_no_filter, filter)
        else -> stringResource(R.string.nothing_captured)
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

private fun groupByDate(
    items: List<CapturedItem>,
    todayLabel: String,
    yesterdayLabel: String,
    thisWeekLabel: String,
    olderLabel: String,
): List<Pair<String, List<CapturedItem>>> {
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
    if (todayItems.isNotEmpty()) groups.add(todayLabel to todayItems)
    if (yesterdayItems.isNotEmpty()) groups.add(yesterdayLabel to yesterdayItems)
    if (weekItems.isNotEmpty()) groups.add(thisWeekLabel to weekItems)
    if (olderItems.isNotEmpty()) groups.add(olderLabel to olderItems)
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
    "toast" -> Icons.Default.Warning
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
        val intent = metadata.deserializeToIntent()
        if (intent != null) {
            try {
                context.startActivity(intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
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

private fun highlightText(text: String, keywords: List<String>): AnnotatedString {
    if (keywords.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    val sorted = keywords.sortedByDescending { it.length }
    val lowerKeywords = sorted.map { it.lowercase() }
    val matches = mutableListOf<IntRange>()
    var i = 0
    while (i < lower.length && matches.size < 30) {
        for (k in lowerKeywords.indices) {
            val kw = lowerKeywords[k]
            if (i + kw.length <= lower.length && lower.regionMatches(i, kw, 0, kw.length)) {
                matches.add(i..<i + kw.length)
                i += kw.length - 1
                break
            }
        }
        i++
    }
    return buildAnnotatedString {
        var pos = 0
        for (m in matches) {
            if (m.first < pos) continue
            if (pos < m.first) append(text.substring(pos, m.first))
            withStyle(SpanStyle(background = Color(0x55FFD600))) {
                append(text.substring(m.first, m.last + 1))
            }
            pos = m.last + 1
        }
        if (pos < text.length) append(text.substring(pos))
    }
}

private fun formatTimestampShort(millis: Long): String {
    val zdt = java.time.Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault()).format(zdt)
}
