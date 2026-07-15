package com.activitytrace.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.activitytrace.R
import com.activitytrace.model.CapturedItem
import com.activitytrace.store.CaptureDao
import com.activitytrace.store.CaptureDao.ContentTypeCount
import com.activitytrace.store.CaptureDao.HourCount
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private enum class DateRange {
    DAYS_7, DAYS_30, DAYS_90, ALL_TIME;

    val days: Int? get() = when (this) {
        DAYS_7 -> 7
        DAYS_30 -> 30
        DAYS_90 -> 90
        ALL_TIME -> null
    }
}

@Composable
fun StatisticsScreen(
    items: List<CapturedItem>,
    initialAppPackage: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedApp by remember { mutableStateOf(initialAppPackage) }
    var selectedRange by remember { mutableStateOf(DateRange.DAYS_7) }

    var totalCount by remember { mutableStateOf(0) }
    var todayCount by remember { mutableStateOf(0) }
    var weekCount by remember { mutableStateOf(0) }
    var yesterdayCount by remember { mutableStateOf(0) }
    var prevWeekCount by remember { mutableStateOf(0) }
    var topApps by remember { mutableStateOf<List<CaptureDao.AppCount>>(emptyList()) }
    var dailyCounts by remember { mutableStateOf<List<CaptureDao.DateCount>>(emptyList()) }
    var typeBreakdown by remember { mutableStateOf<List<ContentTypeCount>>(emptyList()) }
    var hourly by remember { mutableStateOf<List<HourCount>>(emptyList()) }
    var dayOfWeekData by remember { mutableStateOf<List<CaptureDao.DayOfWeekCount>>(emptyList()) }
    var bookmarkedTotal by remember { mutableStateOf(0) }
    var bookmarkedRecent by remember { mutableStateOf(0) }

    var appTypeBreakdown by remember { mutableStateOf<List<ContentTypeCount>>(emptyList()) }
    var appDaily by remember { mutableStateOf<List<CaptureDao.DateCount>>(emptyList()) }
    var appTotal by remember { mutableStateOf(0) }
    var appToday by remember { mutableStateOf(0) }
    var appWeek by remember { mutableStateOf(0) }

    var availableApps by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(items.size, items.hashCode(), selectedType, selectedApp, selectedRange) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val weekStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val prevWeekStart = cal.timeInMillis

        val yesterdayStart = todayStart - 86_400_000L
        val rangeDays = selectedRange.days
        val rangeStartMs = if (rangeDays != null) todayStart - rangeDays * 86_400_000L else 0L

        availableApps = items.map { it.appPackage }.distinct().sorted()

        val filtered = items.filter { item ->
            (selectedType == null || item.contentType == selectedType) &&
            (selectedApp == null || item.appPackage == selectedApp)
        }

        val ranged = if (rangeDays != null) filtered.filter { it.timestamp >= rangeStartMs } else filtered

        bookmarkedTotal = items.count { it.isBookmarked }
        bookmarkedRecent = items.count { it.isBookmarked && it.timestamp >= weekStart }

        val simpleDate: (Long) -> String = { ts ->
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
        val toHour: (Long) -> Int = { ts ->
            Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
        }
        val toDow: (Long) -> Int = { ts ->
            (Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK) + 6) % 7
        }

        totalCount = ranged.size
        todayCount = ranged.count { it.timestamp >= todayStart }
        weekCount = ranged.count { it.timestamp >= weekStart }
        yesterdayCount = ranged.count { it.timestamp in yesterdayStart until todayStart }
        prevWeekCount = ranged.count { it.timestamp in prevWeekStart until weekStart }

        if (selectedApp != null) {
            appTotal = ranged.size
            appToday = ranged.count { it.timestamp >= todayStart }
            appWeek = ranged.count { it.timestamp >= weekStart }
            val dailyLimit = rangeDays ?: 30
            appDaily = ranged.groupBy { simpleDate(it.timestamp) }
                .map { (date, group) -> CaptureDao.DateCount(date, group.size) }
                .sortedBy { it.date }
                .takeLast(dailyLimit)
            appTypeBreakdown = ranged.groupBy { it.contentType }
                .map { (type, group) -> ContentTypeCount(type, group.size) }
            hourly = ranged.groupBy { toHour(it.timestamp) }
                .map { (hour, group) -> HourCount(hour, group.size) }
                .sortedBy { it.hour }
        } else {
            val dailyLimit = rangeDays ?: 30
            dailyCounts = ranged.groupBy { simpleDate(it.timestamp) }
                .map { (date, group) -> CaptureDao.DateCount(date, group.size) }
                .sortedBy { it.date }
                .takeLast(dailyLimit)
            topApps = ranged.groupBy { it.appPackage }
                .map { (pkg, group) -> CaptureDao.AppCount(pkg, group.size) }
                .sortedByDescending { it.count }
                .take(15)
            typeBreakdown = ranged.groupBy { it.contentType }
                .map { (type, group) -> ContentTypeCount(type, group.size) }
            hourly = ranged.groupBy { toHour(it.timestamp) }
                .map { (hour, group) -> HourCount(hour, group.size) }
                .sortedBy { it.hour }
        }

        dayOfWeekData = ranged.groupBy { toDow(it.timestamp) }
            .map { (dow, group) -> CaptureDao.DayOfWeekCount(dow, group.size) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        FilterRow(
            selectedTypeLabel = typeLabel(selectedType),
            onTypeSelect = { selectedType = it },
            selectedAppLabel = if (selectedApp == null) stringResource(R.string.filter_all_apps)
                               else selectedApp!!.substringAfterLast('.'),
            onAppSelect = { selectedApp = it },
            availableApps = availableApps,
            selectedRange = selectedRange,
            onRangeSelect = { selectedRange = it },
        )
        Spacer(Modifier.height(12.dp))

        if (selectedApp == null && selectedType == null && hourly.isNotEmpty()) {
            InsightBanner(dayOfWeekData, hourly, typeBreakdown, topApps)
            Spacer(Modifier.height(16.dp))
        }

        if (selectedApp != null) {
            val appName = resolveAppName2(context, selectedApp!!)
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            SummaryCards(appTotal, appToday, appWeek, yesterdayCount, prevWeekCount, selectedRange)
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                icon = { Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = { Text(stringResource(R.string.statistics_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            )
            Spacer(Modifier.height(8.dp))
            if (appTypeBreakdown.isEmpty()) {
                EmptyBreakdown(typeLabel(selectedType))
            } else {
                BreakdownDonutCard(appTypeBreakdown)
            }
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                icon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = { Text(stringResource(R.string.statistics_day_of_week), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            )
            Spacer(Modifier.height(8.dp))
            if (dayOfWeekData.any { it.count > 0 }) {
                DayOfWeekCard(dayOfWeekData)
            } else {
                NoDataText()
            }
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                icon = { Icon(Icons.Default.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = { Text(stringResource(R.string.statistics_7day), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            )
            Spacer(Modifier.height(8.dp))
            if (appDaily.isNotEmpty()) {
                DailyChart(fillDailyGaps(appDaily, selectedRange), selectedRange)
            } else {
                NoDataText()
            }
        } else {
            SummaryCards(totalCount, todayCount, weekCount, yesterdayCount, prevWeekCount, selectedRange)
            Spacer(Modifier.height(16.dp))

            if (selectedType == null) {
                SectionHeader(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                    title = { Text(stringResource(R.string.statistics_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                )
                Spacer(Modifier.height(8.dp))
                if (typeBreakdown.isEmpty()) {
                    EmptyBreakdown(typeLabel(selectedType))
                } else {
                    BreakdownDonutCard(typeBreakdown)
                }
                Spacer(Modifier.height(16.dp))
            }

            SectionHeader(
                icon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = { Text(stringResource(R.string.statistics_day_of_week), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            )
            Spacer(Modifier.height(8.dp))
            if (dayOfWeekData.any { it.count > 0 }) {
                DayOfWeekCard(dayOfWeekData)
            } else {
                NoDataText()
            }
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                icon = { Icon(Icons.Default.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = { Text(stringResource(R.string.statistics_7day), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            )
            Spacer(Modifier.height(8.dp))
            if (dailyCounts.isNotEmpty()) {
                DailyChart(fillDailyGaps(dailyCounts, selectedRange), selectedRange)
            } else {
                NoDataText()
            }
        }

        if (hourly.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(
                icon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = { Text(stringResource(R.string.statistics_hourly), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            )
            Spacer(Modifier.height(8.dp))
            HourlyChart(hourly)
        }

        if (bookmarkedTotal > 0) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(
                icon = { Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = { Text(stringResource(R.string.statistics_bookmarked), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            )
            Spacer(Modifier.height(8.dp))
            BookmarkCard(bookmarkedTotal, bookmarkedRecent)
        }

        if (selectedApp == null && topApps.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHeader(
                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                title = { Text(stringResource(R.string.statistics_top_apps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
            )
            Spacer(Modifier.height(8.dp))
            TopAppsList(topApps, onAppClick = { selectedApp = it })
        }
    }
}

@Composable
private fun typeLabel(type: String?): String = when (type) {
    null -> stringResource(R.string.filter_all)
    "notification" -> stringResource(R.string.filter_notifications)
    "screen" -> stringResource(R.string.filter_accessibility)
    "toast" -> stringResource(R.string.filter_toast)
    "page" -> stringResource(R.string.filter_folders)
    else -> type
}

@Composable
private fun SectionHeader(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            title()
        }
    }
}

@Composable
private fun FilterRow(
    selectedTypeLabel: String,
    onTypeSelect: (String?) -> Unit,
    selectedAppLabel: String,
    onAppSelect: (String?) -> Unit,
    availableApps: List<String>,
    selectedRange: DateRange,
    onRangeSelect: (DateRange) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsFilterDropdown(
            label = selectedTypeLabel,
            options = listOf(
                null to stringResource(R.string.filter_all),
                "notification" to stringResource(R.string.filter_notifications),
                "screen" to stringResource(R.string.filter_accessibility),
                "toast" to stringResource(R.string.filter_toast),
                "page" to stringResource(R.string.filter_folders),
            ),
            onSelect = onTypeSelect,
            modifier = Modifier.weight(1f),
        )
        StatsFilterDropdown(
            label = selectedAppLabel,
            options = listOf(null to stringResource(R.string.filter_all_apps)) +
                availableApps.take(15).map { it to it.substringAfterLast('.') },
            onSelect = onAppSelect,
            modifier = Modifier.weight(1f),
        )
        StatsFilterDropdown(
            label = stringResource(
                when (selectedRange) {
                    DateRange.DAYS_7 -> R.string.statistics_7d
                    DateRange.DAYS_30 -> R.string.statistics_30d
                    DateRange.DAYS_90 -> R.string.statistics_90d
                    DateRange.ALL_TIME -> R.string.statistics_all_time
                }
            ),
            options = DateRange.entries.map { range ->
                range.name to stringResource(
                    when (range) {
                        DateRange.DAYS_7 -> R.string.statistics_7d
                        DateRange.DAYS_30 -> R.string.statistics_30d
                        DateRange.DAYS_90 -> R.string.statistics_90d
                        DateRange.ALL_TIME -> R.string.statistics_all_time
                    }
                )
            },
            onSelect = { value -> onRangeSelect(DateRange.valueOf(value!!)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatsFilterDropdown(
    label: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SummaryCards(total: Int, today: Int, week: Int, yesterday: Int, prevWeek: Int, range: DateRange) {
    val todayTrend = trendPercent(today, yesterday)
    val weekTrend = trendPercent(week, prevWeek)
    val avg = if (total == 0) 0 else {
        val days = when (range) {
            DateRange.DAYS_7 -> 7
            DateRange.DAYS_30 -> 30
            DateRange.DAYS_90 -> 90
            DateRange.ALL_TIME -> null
        }
        if (days != null) (total.toFloat() / days).roundToInt() else 0
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            value = total,
            label = stringResource(R.string.statistics_total),
            icon = {
                Icon(
                    Icons.Default.AllInclusive, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = today,
            label = stringResource(R.string.statistics_today),
            icon = {
                Icon(
                    Icons.Default.Today, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp),
                )
            },
            trend = todayTrend,
            trendLabel = stringResource(R.string.statistics_vs_yesterday),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = week,
            label = stringResource(R.string.statistics_this_week),
            icon = {
                Icon(
                    Icons.Default.DateRange, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp),
                )
            },
            trend = weekTrend,
            trendLabel = stringResource(R.string.statistics_vs_last_week),
            modifier = Modifier.weight(1f),
        )
        if (avg > 0) {
            StatCard(
                value = avg,
                label = stringResource(R.string.statistics_avg_per_day),
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(
    value: Int,
    label: String,
    icon: @Composable () -> Unit,
    trend: String? = null,
    trendLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 600),
    )

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon()
            Spacer(Modifier.height(4.dp))
            Text(
                text = animatedValue.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (trend != null && trendLabel != null) {
                Spacer(Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        trend.startsWith("↑") -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                        trend.startsWith("↓") -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        text = "$trend $trendLabel",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = when {
                            trend.startsWith("↑") -> MaterialTheme.colorScheme.tertiary
                            trend.startsWith("↓") -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

private fun trendPercent(current: Int, previous: Int): String? {
    if (previous <= 0) return if (current > 0) "↑—" else null
    val pct = ((current - previous).toFloat() / previous * 100).roundToInt()
    return when {
        pct > 0 -> "↑$pct%"
        pct < 0 -> "↓${-pct}%"
        else -> "—"
    }
}

@Composable
private fun BreakdownCard(data: List<ContentTypeCount>) {
    val total = data.sumOf { it.count }.coerceAtLeast(1)
    val colors = mapOf(
        "notification" to MaterialTheme.colorScheme.primary,
        "screen" to MaterialTheme.colorScheme.tertiary,
        "toast" to MaterialTheme.colorScheme.secondary,
        "page" to MaterialTheme.colorScheme.error,
    )
    val labels = mapOf(
        "notification" to stringResource(R.string.filter_notifications),
        "screen" to stringResource(R.string.filter_accessibility),
        "toast" to stringResource(R.string.filter_toast),
        "page" to stringResource(R.string.filter_folders),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            data.forEach { item ->
                val color = colors[item.contentType] ?: MaterialTheme.colorScheme.surfaceVariant
                val label = labels[item.contentType] ?: item.contentType
                val fraction = item.count.toFloat() / total

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = CircleShape,
                        color = color,
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${item.count}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .padding(vertical = 2.dp),
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 1.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(3.dp),
                        color = color.copy(alpha = 0.15f),
                    ) {}
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .fillMaxWidth(fraction),
                        shape = RoundedCornerShape(3.dp),
                        color = color.copy(alpha = 0.6f),
                    ) {}
                }
                }
            }
        }
    }
}

@Composable
private fun DailyChart(data: List<CaptureDao.DateCount>, range: DateRange) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val todayLabel = stringResource(R.string.statistics_today_label)

    Card(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp),
        ) {
            val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
            val barWidth = size.width / data.size * 0.55f
            val gap = size.width / data.size * 0.45f / 2

            data.forEachIndexed { index, item ->
                val barHeight = (item.count.toFloat() / maxCount) * (size.height - 50f)
                val x = index * (barWidth + gap * 2) + gap
                val y = size.height - barHeight

                val isLast = index == data.lastIndex
                val barColor = if (isLast) tertiary else primary

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(barColor, barColor.copy(alpha = 0.2f)),
                        startY = y,
                        endY = size.height,
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        this.color = onSurface.copy(alpha = 0.8f).hashCode()
                        textSize = 26f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    drawText(
                        item.count.toString(),
                        x + barWidth / 2,
                        y - 6f,
                        paint,
                    )
                    paint.apply {
                        textSize = 22f
                        isFakeBoldText = false
                        this.color = if (isLast) tertiary.copy(alpha = 0.9f).hashCode()
                                    else onSurface.copy(alpha = 0.5f).hashCode()
                    }
                    val label = if (isLast && range != DateRange.ALL_TIME) todayLabel
                                else item.date.substringAfterLast("-")
                    drawText(
                        label,
                        x + barWidth / 2,
                        size.height - 2f,
                        paint,
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyChart(data: List<HourCount>) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val peak = data.maxByOrNull { it.count }

    Card(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(12.dp),
        ) {
            val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
            val totalSlots = 24
            val barWidth = size.width / totalSlots * 0.7f
            val gap = size.width / totalSlots * 0.3f / 2
            val dataMap = data.associate { it.hour to it.count }

            for (hour in 0 until totalSlots) {
                val count = dataMap[hour] ?: 0
                val barHeight = (count.toFloat() / maxCount) * (size.height - 20f)
                val x = hour * (barWidth + gap * 2) + gap
                val y = size.height - barHeight

                val isPeak = peak != null && hour == peak.hour
                val barColor = if (isPeak) tertiary else primary
                val alpha = if (count > 0) 0.5f + 0.5f * (count.toFloat() / maxCount) else 0.08f

                drawRoundRect(
                    color = barColor.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(2f, 2f),
                )

                // peak marker dot
                if (isPeak && count > 0) {
                    drawCircle(
                        color = tertiary,
                        radius = 4f,
                        center = Offset(x + barWidth / 2, y - 8f),
                    )
                }

                if (hour % 6 == 0) {
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            hour.toString(),
                            x + barWidth / 2,
                            size.height - 2f,
                            android.graphics.Paint().apply {
                                color = onSurface.copy(alpha = 0.4f).hashCode()
                                textSize = 18f
                                textAlign = android.graphics.Paint.Align.CENTER
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopAppsList(
    apps: List<CaptureDao.AppCount>,
    onAppClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val total = apps.sumOf { it.count }.coerceAtLeast(1)
    val medals = listOf("🥇", "🥈", "🥉")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            apps.forEachIndexed { index, app ->
                val appName = remember(app.appPackage) { resolveAppName2(context, app.appPackage) }
                val appIcon = remember(app.appPackage) {
                    try {
                        context.packageManager.getApplicationIcon(app.appPackage)
                            .toBitmap2().asImageBitmap()
                    } catch (_: Exception) { null }
                }
                val fraction = app.count.toFloat() / total

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app.appPackage) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = medals.getOrElse(index) { "${index + 1}." },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(28.dp),
                    )
                    if (appIcon != null) {
                        Icon(
                            painter = BitmapPainter(appIcon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp).clip(CircleShape),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(appName.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(fraction).height(3.dp),
                            shape = RoundedCornerShape(2.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        ) {}
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = app.count.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayOfWeekCard(data: List<CaptureDao.DayOfWeekCount>) {
    val labels = listOf(
        stringResource(R.string.statistics_dow_sun),
        stringResource(R.string.statistics_dow_mon),
        stringResource(R.string.statistics_dow_tue),
        stringResource(R.string.statistics_dow_wed),
        stringResource(R.string.statistics_dow_thu),
        stringResource(R.string.statistics_dow_fri),
        stringResource(R.string.statistics_dow_sat),
    )
    val dayColors = listOf(
        Color(0xFFE53935), // Sun — red
        Color(0xFFFF8F00), // Mon — orange
        Color(0xFFFDD835), // Tue — yellow
        Color(0xFF43A047), // Wed — green
        Color(0xFF1E88E5), // Thu — blue
        Color(0xFF8E24AA), // Fri — purple
        Color(0xFFE53935), // Sat — red
    )
    val maxCount = data.maxOfOrNull { it.count } ?: return
    val maxDow = data.maxByOrNull { it.count }?.dow

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            for (dow in 0..6) {
                val item = data.find { it.dow == dow }
                val count = item?.count ?: 0
                val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                val isMax = dow == maxDow
                val barColor = dayColors[dow]

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = labels[dow],
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isMax) FontWeight.Bold else FontWeight.Normal,
                        color = if (isMax) barColor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(48.dp),
                    )
                    Box(modifier = Modifier.weight(1f).height(16.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(4.dp),
                            color = barColor.copy(alpha = 0.1f),
                        ) {}
                        Surface(
                            modifier = Modifier.fillMaxWidth(fraction).fillMaxSize(),
                            shape = RoundedCornerShape(4.dp),
                            color = if (count > 0) barColor.copy(alpha = 0.55f) else Color.Transparent,
                        ) {}
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isMax) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier.width(36.dp),
                        color = if (isMax) barColor else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isMax && count > 0) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = barColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(total: Int, recentWeek: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Bookmark, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = total.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (recentWeek > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "($recentWeek ${stringResource(R.string.statistics_this_week).lowercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun fillDailyGaps(data: List<CaptureDao.DateCount>, range: DateRange): List<CaptureDao.DateCount> {
    val days = range.days ?: 7
    val cal = Calendar.getInstance()
    val dataMap = data.associate { it.date to it.count }
    val result = mutableListOf<CaptureDao.DateCount>()

    for (i in (days - 1) downTo 0) {
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, -i)
        val date = String.format(
            Locale.US, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        result.add(CaptureDao.DateCount(date, dataMap[date] ?: 0))
    }
    return result
}

@Composable
private fun InsightBanner(
    dayOfWeekData: List<CaptureDao.DayOfWeekCount>,
    hourly: List<HourCount>,
    typeBreakdown: List<ContentTypeCount>,
    topApps: List<CaptureDao.AppCount>,
) {
    val context = LocalContext.current
    val dowLabels = listOf(
        stringResource(R.string.statistics_dow_sun),
        stringResource(R.string.statistics_dow_mon),
        stringResource(R.string.statistics_dow_tue),
        stringResource(R.string.statistics_dow_wed),
        stringResource(R.string.statistics_dow_thu),
        stringResource(R.string.statistics_dow_fri),
        stringResource(R.string.statistics_dow_sat),
    )
    val bestDay = dayOfWeekData.maxByOrNull { it.count }
    val peakHour = hourly.maxByOrNull { it.count }
    val topApp = topApps.firstOrNull()
    val topAppPct = if (topApp != null && typeBreakdown.isNotEmpty()) {
        val total = typeBreakdown.sumOf { it.count }
        if (total > 0) (topApp.count.toFloat() / total * 100).roundToInt() else null
    } else null

    val dayName = if (bestDay?.count ?: 0 > 0) dowLabels.getOrNull(bestDay!!.dow) else null
    val peakHourVal = peakHour?.hour
    val peakLabel = if (peakHourVal != null) stringResource(R.string.statistics_peak_hour, peakHourVal) else null
    val topLabel = topApp?.let { app ->
        val name = resolveAppName2(context, app.appPackage)
        if (topAppPct != null) "$name ($topAppPct%)" else name
    }

    val parts = listOfNotNull(
        dayName?.let { "Most captures on $it" },
        peakLabel,
        topLabel?.let { "Top: $it" },
    )
    if (parts.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        tonalElevation = 1.dp,
    ) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun BreakdownDonutCard(data: List<ContentTypeCount>) {
    val total = data.sumOf { it.count }.coerceAtLeast(1)
    val colors = mapOf(
        "notification" to MaterialTheme.colorScheme.primary,
        "screen" to MaterialTheme.colorScheme.tertiary,
        "toast" to MaterialTheme.colorScheme.secondary,
        "page" to MaterialTheme.colorScheme.error,
    )
    val labels = mapOf(
        "notification" to stringResource(R.string.filter_notifications),
        "screen" to stringResource(R.string.filter_accessibility),
        "toast" to stringResource(R.string.filter_toast),
        "page" to stringResource(R.string.filter_folders),
    )

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.hashCode()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Donut
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    var startAngle = -90f
                    val strokeWidth = 32f
                    data.forEach { item ->
                        val fraction = item.count.toFloat() / total
                        val sweep = fraction * 360f
                        val color = colors[item.contentType] ?: Color.Gray
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth),
                        )
                        startAngle += sweep
                    }
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            total.toString(),
                            size.width / 2,
                            size.height / 2 + 12f,
                            android.graphics.Paint().apply {
                                color = onSurfaceColor
                                textSize = 42f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Legend
            data.forEach { item ->
                val color = colors[item.contentType] ?: MaterialTheme.colorScheme.surfaceVariant
                val label = labels[item.contentType] ?: item.contentType
                val fraction = item.count.toFloat() / total

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = CircleShape,
                        color = color,
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(fraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${item.count}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBreakdown(label: String) {
    Text(
        text = stringResource(R.string.statistics_no_type, label),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoDataText() {
    Text(
        text = stringResource(R.string.statistics_no_data),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("DEPRECATION")
private fun resolveAppName2(context: Context, pkg: String): String {
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

private fun Drawable.toBitmap2(defaultSize: Int = 256): Bitmap = when (this) {
    is BitmapDrawable -> bitmap
    else -> {
        val bmp = Bitmap.createBitmap(defaultSize, defaultSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, defaultSize, defaultSize)
        draw(canvas)
        bmp
    }
}
