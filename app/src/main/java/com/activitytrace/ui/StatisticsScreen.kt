package com.activitytrace.ui

import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.activitytrace.R
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.CaptureDao
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dao = remember {
        try {
            ActivityTraceDatabase.getInstance(context).captureDao()
        } catch (_: Exception) { null }
    }

    var totalCount by remember { mutableStateOf(0) }
    var todayCount by remember { mutableStateOf(0) }
    var weekCount by remember { mutableStateOf(0) }
    var topApps by remember { mutableStateOf<List<CaptureDao.AppCount>>(emptyList()) }
    var dailyCounts by remember { mutableStateOf<List<CaptureDao.DateCount>>(emptyList()) }

    LaunchedEffect(dao) {
        if (dao == null) return@LaunchedEffect
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val weekStart = cal.timeInMillis

        totalCount = dao.totalCount()
        todayCount = dao.countSince(todayStart)
        weekCount = dao.countSince(weekStart)
        topApps = dao.topApps()
        dailyCounts = dao.dailyCounts()
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
            SummaryCards(totalCount, todayCount, weekCount)
            Spacer(Modifier.height(16.dp))
            if (dailyCounts.size >= 2) {
                Text(
                    text = stringResource(R.string.statistics_7day),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                DailyChart(dailyCounts.take(7).reversed())
            } else {
                Text(
                    text = stringResource(R.string.statistics_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            if (topApps.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.statistics_top_apps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                TopAppsList(topApps)
            }
        }
    }
}

@Composable
private fun SummaryCards(total: Int, today: Int, week: Int) {
    val cards = listOf(
        stringResource(R.string.statistics_total) to total.toString(),
        stringResource(R.string.statistics_today) to today.toString(),
        stringResource(R.string.statistics_this_week) to week.toString(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cards.forEach { (label, value) ->
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
private fun DailyChart(data: List<CaptureDao.DateCount>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp),
        ) {
            val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
            val barWidth = size.width / data.size * 0.6f
            val gap = size.width / data.size * 0.4f / 2

            data.forEachIndexed { index, item ->
                val barHeight = (item.count.toFloat() / maxCount) * (size.height - 40f)
                val x = index * (barWidth + gap * 2) + gap
                val y = size.height - barHeight

                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )

                drawContext.canvas.nativeCanvas.apply {
                    val dateLabel = item.date.substringAfterLast("-")
                    drawText(
                        dateLabel,
                        x + barWidth / 2,
                        size.height - 4f,
                        android.graphics.Paint().apply {
                            color = onSurface.copy(alpha = 0.6f).hashCode()
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopAppsList(apps: List<CaptureDao.AppCount>) {
    val maxCount = apps.maxOf { it.count }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            apps.forEachIndexed { index, app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text = app.appPackage.substringAfterLast('.'),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = app.count.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(40.dp),
                    )
                }
            }
        }
    }
}
