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
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
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

@Composable
fun StatisticsScreen(
    items: List<CapturedItem>,
    selectedType: String? = null,
    selectedApp: String? = null,
    dateRangeMs: Pair<Long, Long>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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

    var appDaily by remember { mutableStateOf<List<CaptureDao.DateCount>>(emptyList()) }
    var appTotal by remember { mutableStateOf(0) }
    var appToday by remember { mutableStateOf(0) }
    var appWeek by remember { mutableStateOf(0) }

    var showAllApps by remember { mutableStateOf(false) }
    var timelineTab by remember { mutableStateOf("daily") }

    LaunchedEffect(items.size, items.hashCode(), showAllApps, selectedType, selectedApp, dateRangeMs) {
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
        val rangeDays = dateRangeMs?.let { (s, e) -> ((e - s) / 86_400_000L).toInt() }
        val rangeStartMs = dateRangeMs?.first ?: 0L
        val rangeEndMs = dateRangeMs?.second ?: Long.MAX_VALUE

        val filtered = items.filter { item ->
            (selectedType == null || item.contentType == selectedType) &&
            (selectedApp == null || item.appPackage == selectedApp)
        }

        val ranged = if (dateRangeMs != null) filtered.filter { it.timestamp in rangeStartMs until rangeEndMs } else filtered

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

        val dailyLimit = rangeDays ?: 30
        topApps = ranged.groupBy { it.appPackage }
            .map { (pkg, group) -> CaptureDao.AppCount(pkg, group.size) }
            .sortedByDescending { it.count }
            .take(if (showAllApps) 15 else 5)
        typeBreakdown = ranged.groupBy { it.contentType }
            .map { (type, group) -> ContentTypeCount(type, group.size) }
        hourly = ranged.groupBy { toHour(it.timestamp) }
            .map { (hour, group) -> HourCount(hour, group.size) }
            .sortedBy { it.hour }

        if (selectedApp != null) {
            appTotal = ranged.size
            appToday = ranged.count { it.timestamp >= todayStart }
            appWeek = ranged.count { it.timestamp >= weekStart }
            appDaily = ranged.groupBy { simpleDate(it.timestamp) }
                .map { (date, group) -> CaptureDao.DateCount(date, group.size) }
                .sortedBy { it.date }
                .takeLast(dailyLimit)
        } else {
            dailyCounts = ranged.groupBy { simpleDate(it.timestamp) }
                .map { (date, group) -> CaptureDao.DateCount(date, group.size) }
                .sortedBy { it.date }
                .takeLast(dailyLimit)
        }

        dayOfWeekData = ranged.groupBy { toDow(it.timestamp) }
            .map { (dow, group) -> CaptureDao.DayOfWeekCount(dow, group.size) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        if (selectedApp != null) {
            val appName = resolveAppName2(context, selectedApp!!)
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            SummaryCards(appTotal, appToday, appWeek, yesterdayCount, prevWeekCount, dateRangeMs)
        } else {
            SummaryCards(totalCount, todayCount, weekCount, yesterdayCount, prevWeekCount, dateRangeMs)
        }
        Spacer(Modifier.height(10.dp))

        SectionHeader(
            icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            title = { Text(stringResource(R.string.statistics_top_apps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        )
        Spacer(Modifier.height(6.dp))
        TopAppsList(topApps, showAll = showAllApps, onShowAllChange = { showAllApps = it }, onAppClick = { })

        TimelineSection(
            timelineTab = timelineTab,
            onTabChange = { timelineTab = it },
            dailyChart = {
                val dailyData = if (selectedApp != null) appDaily else dailyCounts
                if (dailyData.isNotEmpty()) DailyChart(fillDailyGaps(dailyData, dateRangeMs), dateRangeMs)
                else NoDataText()
            },
            hourlyChart = {
                if (hourly.isNotEmpty()) HourlyChart(hourly)
                else NoDataText()
            },
            dowChart = {
                if (dayOfWeekData.any { it.count > 0 }) DayOfWeekCard(dayOfWeekData)
                else NoDataText()
            },
        )

        Spacer(Modifier.height(10.dp))
        SectionHeader(
            icon = { Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
            title = { Text(stringResource(R.string.statistics_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
        )
        Spacer(Modifier.height(6.dp))
        if (typeBreakdown.isEmpty()) {
            EmptyBreakdown(typeLabel(selectedType))
        } else {
            BreakdownDonutCard(typeBreakdown)
        }
    }
}

@Composable
private fun TimelineSection(
    timelineTab: String,
    onTabChange: (String) -> Unit,
    dailyChart: @Composable () -> Unit,
    hourlyChart: @Composable () -> Unit,
    dowChart: @Composable () -> Unit,
) {
    SectionHeader(
        icon = { Icon(Icons.Default.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
        title = { Text(stringResource(R.string.statistics_timeline), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
    )
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val chips = listOf("daily" to R.string.statistics_tab_daily, "hours" to R.string.statistics_tab_hours, "days" to R.string.statistics_tab_days)
        chips.forEach { (value, labelRes) ->
            val selected = timelineTab == value
            Surface(
                onClick = { onTabChange(value) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    when (timelineTab) {
        "daily" -> dailyChart()
        "hours" -> hourlyChart()
        "days" -> dowChart()
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
private fun SummaryCards(total: Int, today: Int, week: Int, yesterday: Int, prevWeek: Int, dateRangeMs: Pair<Long, Long>?) {
    val todayTrend = trendPercent(today, yesterday)
    val weekTrend = trendPercent(week, prevWeek)
    val avg = if (total == 0) 0 else {
        val days = dateRangeMs?.let { (s, e) -> ((e - s) / 86_400_000L).toInt() }
        if (days != null && days > 0) (total.toFloat() / days).roundToInt() else 0
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
            modifier = Modifier.fillMaxWidth().padding(8.dp),
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
private fun DailyChart(data: List<CaptureDao.DateCount>, dateRangeMs: Pair<Long, Long>?) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val todayLabel = stringResource(R.string.statistics_today_label)

    Card(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(12.dp),
        ) {
            val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
            val barWidth = size.width / data.size * 0.55f
            val gap = size.width / data.size * 0.45f / 2

            data.forEachIndexed { index, item ->
                val bottomMargin = 45f
                val barBaseline = size.height - bottomMargin
                val barHeight = (item.count.toFloat() / maxCount) * (size.height - bottomMargin)
                val x = index * (barWidth + gap * 2) + gap
                val y = barBaseline - barHeight

                val isLast = index == data.lastIndex
                val barColor = if (isLast) tertiary else primary

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(barColor, barColor.copy(alpha = 0.2f)),
                        startY = y,
                        endY = barBaseline,
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = 26f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    if (item.count > 0) {
                        val countY = if (barHeight > 32f) y - 6f
                                     else (y + barHeight / 2f + 9f).coerceAtMost(barBaseline - 6f)
                        paint.color = if (barHeight > 32f) onSurface.copy(alpha = 0.8f).hashCode()
                                      else onSurface.copy(alpha = 1f).hashCode()
                        drawText(
                            item.count.toString(),
                            x + barWidth / 2,
                            countY,
                            paint,
                        )
                    }
                    paint.apply {
                        textSize = 22f
                        isFakeBoldText = false
                        this.color = if (isLast) tertiary.copy(alpha = 0.9f).hashCode()
                                    else onSurface.copy(alpha = 0.5f).hashCode()
                    }
                    val label = if (isLast && dateRangeMs != null) todayLabel
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
                .height(160.dp)
                .padding(8.dp),
        ) {
            val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
            val totalSlots = 24
            val barWidth = size.width / totalSlots * 0.7f
            val gap = size.width / totalSlots * 0.3f / 2
            val dataMap = data.associate { it.hour to it.count }

            for (hour in 0 until totalSlots) {
                val count = dataMap[hour] ?: 0
                val bottomMargin = 30f
                val barBaseline = size.height - bottomMargin
                val barHeight = (count.toFloat() / maxCount) * (size.height - bottomMargin)
                val x = hour * (barWidth + gap * 2) + gap
                val y = barBaseline - barHeight

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
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    onAppClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val total = apps.sumOf { it.count }.coerceAtLeast(1)
    val medals = listOf("🥇", "🥈", "🥉")
    val displayApps = if (showAll) apps else apps.take(5)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            displayApps.forEachIndexed { index, app ->
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
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = medals.getOrElse(index) { "${index + 1}." },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(24.dp),
                    )
                    if (appIcon != null) {
                        Icon(
                            painter = BitmapPainter(appIcon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp).clip(CircleShape),
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(appName.take(1).uppercase(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(fraction).height(2.dp),
                            shape = RoundedCornerShape(1.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        ) {}
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = app.count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (!showAll && apps.size > 5) {
                Text(
                    text = stringResource(R.string.statistics_show_all, apps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowAllChange(true) }
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
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

    Card(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                for (dow in 0..6) {
                    val item = data.find { it.dow == dow }
                    val count = item?.count ?: 0
                    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                    val isMax = dow == maxDow
                    val barColor = dayColors[dow]

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = labels[dow],
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isMax) FontWeight.Bold else FontWeight.Normal,
                            color = if (isMax) barColor else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(38.dp),
                        )
                        Box(modifier = Modifier.weight(1f).height(12.dp)) {
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
}

@Composable
private fun fillDailyGaps(data: List<CaptureDao.DateCount>, dateRangeMs: Pair<Long, Long>?): List<CaptureDao.DateCount> {
    val days = dateRangeMs?.let { (s, e) -> ((e - s) / 86_400_000L).toInt() } ?: 30
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
        Column(modifier = Modifier.padding(12.dp)) {
            // Donut
            Box(
                modifier = Modifier.fillMaxWidth().height(130.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(110.dp)) {
                    var startAngle = -90f
                    val strokeWidth = 24f
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
