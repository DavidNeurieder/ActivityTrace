package com.activitytrace.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.activitytrace.R
import com.activitytrace.model.BlockedApp
import com.activitytrace.store.ActivityTraceDatabase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ActivityTraceDatabase.getInstance(context) }
    val blockedApps by db.blockedAppDao().blockedAppsFlow().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_apps_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_description),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (blockedApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_blocked_apps),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(blockedApps, key = { it.appPackage }) { app ->
                    BlockedAppRow(
                        app = app,
                        onUnblock = {
                            scope.launch {
                                db.blockedAppDao().delete(app.appPackage)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedAppRow(
    app: BlockedApp,
    onUnblock: () -> Unit,
) {
    val context = LocalContext.current
    val appName = remember(app.appPackage) { resolveAppName(context, app.appPackage) }
    val appIcon = remember(app.appPackage) {
        try {
            context.packageManager.getApplicationIcon(app.appPackage)
                .toBitmap().asImageBitmap()
        } catch (_: Exception) { null }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (appIcon != null) {
                Icon(
                    painter = BitmapPainter(appIcon),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
            } else {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = appName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.appPackage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onUnblock) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_blocked_app),
                )
            }
        }
    }
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

private fun Drawable.toBitmap(defaultSize: Int = 256): Bitmap = when (this) {
    is BitmapDrawable -> bitmap
    else -> {
        val bmp = Bitmap.createBitmap(defaultSize, defaultSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, defaultSize, defaultSize)
        draw(canvas)
        bmp
    }
}
