package com.activitytrace.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.activitytrace.R
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.CaptureDao
import com.activitytrace.store.CaptureDao.ContentTypeCount
import com.activitytrace.store.CaptureDao.HourCount
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val db = remember { ActivityTraceDatabase.getInstance(context) }
    val dao = remember { db.captureDao() }

    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedApp by remember { mutableStateOf<String?>(null) }

    var totalCount by remember { mutableStateOf(0) }
    var todayCount by remember { mutableStateOf(0) }
    var weekCount by remember { mutableStateOf(0) }
    var topApps by remember { mutableStateOf<List<CaptureDao.AppCount>>(emptyList()) }
    var dailyCounts by remember { mutableStateOf<List<CaptureDao.DateCount>>(emptyList()) }
    var typeBreakdown by remember { mutableStateOf<List<ContentTypeCount>>(emptyList()) }
    var hourly by remember { mutableStateOf<List<HourCount>>(emptyList()) }

    var appTypeBreakdown by remember { mutableStateOf<List<ContentTypeCount>>(emptyList()) }
    var appDaily by remember { mutableStateOf<List<CaptureDao.DateCount>>(emptyList()) }
    var appTotal by remember { mutableStateOf(0) }
    var appToday by remember { mutableStateOf(0) }
    var appWeek by remember { mutableStateOf(0) }

    var availableApps by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(selectedType, selectedApp) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val weekStart = cal.timeInMillis

        availableApps = dao.topApps().map { it.appPackage }

        if (selectedApp != null) {
            appTotal = dao.totalCountByApp(selectedApp!!, selectedType)
            appToday = dao.countSinceByApp(selectedApp!!, todayStart, selectedType)
            appWeek = dao.countSinceByApp(selectedApp!!, weekStart, selectedType)
            appDaily = dao.dailyCountsByApp(selectedApp!!, selectedType)
            appTypeBreakdown = dao.contentTypeBreakdown(selectedApp!!)
            hourly = dao.hourlyCounts(selectedType, selectedApp)
        } else {
            totalCount = dao.totalCount(selectedType)
            todayCount = dao.countSince(todayStart, selectedType)
            weekCount = dao.countSince(weekStart, selectedType)
            topApps = dao.topApps(selectedType)
            dailyCounts = dao.dailyCounts(selectedType)
            typeBreakdown = dao.contentTypeBreakdown()
            hourly = dao.hourlyCounts(selectedType, null)
        }
    }

    @Composable
    fun typeLabel(type: String?): String = when (type) {
        null -> stringResource(R.string.filter_all)
        "notification" -> stringResource(R.string.filter_notifications)
        "screen" -> stringResource(R.string.filter_accessibility)
        "toast" -> stringResource(R.string.filter_toast)
        "page" -> stringResource(R.string.filter_folders)
        else -> type
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
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
            FilterRow(
                selectedTypeLabel = typeLabel(selectedType),
                onTypeSelect = { selectedType = it },
                selectedAppLabel = if (selectedApp == null) stringResource(R.string.filter_all_apps)
                                   else selectedApp!!.substringAfterLast('.'),
                onAppSelect = { selectedApp = it },
                availableApps = availableApps,
            )
            Spacer(Modifier.height(16.dp))

            if (selectedApp != null) {
                val appName = resolveAppName2(context, selectedApp!!)
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                SummaryCards(appTotal, appToday, appWeek)
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.statistics_breakdown),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                if (appTypeBreakdown.isEmpty()) {
                    EmptyBreakdown(typeLabel(selectedType))
                } else {
                    BreakdownCard(appTypeBreakdown)
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.statistics_7day),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                if (appDaily.size >= 2) {
                    DailyChart(appDaily.take(7).reversed())
                } else {
                    NoDataText()
                }
            } else {
                SummaryCards(totalCount, todayCount, weekCount)
                Spacer(Modifier.height(16.dp))

                if (selectedType == null) {
                    Text(
                        text = stringResource(R.string.statistics_breakdown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (typeBreakdown.isEmpty()) {
                        EmptyBreakdown(typeLabel(selectedType))
                    } else {
                        BreakdownCard(typeBreakdown)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    text = stringResource(R.string.statistics_7day),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                if (dailyCounts.size >= 2) {
                    DailyChart(dailyCounts.take(7).reversed())
                } else {
                    NoDataText()
                }
            }

            if (hourly.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.statistics_hourly),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                HourlyChart(hourly)
            }

            if (selectedApp == null && topApps.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.statistics_top_apps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                TopAppsList(topApps, onAppClick = { selectedApp = it })
            }
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
private fun SummaryCards(total: Int, today: Int, week: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            stringResource(R.string.statistics_total) to total.toString(),
            stringResource(R.string.statistics_today) to today.toString(),
            stringResource(R.string.statistics_this_week) to week.toString(),
        ).forEach { (label, value) ->
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
private fun DailyChart(data: List<CaptureDao.DateCount>) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface

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

                val color = if (index == data.lastIndex) tertiary else primary

                drawRoundRect(
                    color = color,
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
                        this.color = onSurface.copy(alpha = 0.5f).hashCode()
                    }
                    val label = item.date.substringAfterLast("-")
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
    val onSurface = MaterialTheme.colorScheme.onSurface

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

                val alpha = if (count > 0) 0.5f + 0.5f * (count.toFloat() / maxCount) else 0.1f
                drawRoundRect(
                    color = primary.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(2f, 2f),
                )

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app.appPackage) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
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
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
