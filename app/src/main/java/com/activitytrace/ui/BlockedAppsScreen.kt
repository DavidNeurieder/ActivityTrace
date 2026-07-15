package com.activitytrace.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.activitytrace.R
import com.activitytrace.capture.DEFAULT_BLOCKED
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
    var showPicker by remember { mutableStateOf(false) }

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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPicker = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_blocked_app))
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        val userBlocked = blockedApps.filter { it.appPackage !in DEFAULT_BLOCKED }
        val defaultBlocked = blockedApps.filter { it.appPackage in DEFAULT_BLOCKED }

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
                if (userBlocked.isNotEmpty()) {
                    item(key = "header_user") {
                        SectionLabel(stringResource(R.string.blocked_apps_manually_blocked))
                    }
                    items(userBlocked, key = { it.appPackage + "_user" }) { app ->
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
                if (defaultBlocked.isNotEmpty()) {
                    item(key = "header_default") {
                        SectionLabel(stringResource(R.string.blocked_apps_system_noise))
                    }
                    items(defaultBlocked, key = { it.appPackage + "_default" }) { app ->
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

    if (showPicker) {
        AppPickerDialog(
            blockedPackages = blockedApps.map { it.appPackage }.toSet(),
            onBlock = { pkg ->
                scope.launch {
                    db.blockedAppDao().insert(BlockedApp(pkg))
                }
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AppPickerDialog(
    blockedPackages: Set<String>,
    onBlock: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val allApps = remember {
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getInstalledApplications(0)
        }
        apps.filter { it.packageName !in blockedPackages }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { ai ->
            val label = pm.getApplicationLabel(ai).toString().lowercase()
            label.contains(searchQuery.lowercase()) ||
                ai.packageName.lowercase().contains(searchQuery.lowercase())
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxSize(0.85f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.select_app_to_block),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { ai ->
                        val label = pm.getApplicationLabel(ai).toString()
                        val icon = remember(ai.packageName) {
                            try {
                                pm.getApplicationIcon(ai.packageName)
                                    .toBitmap().asImageBitmap()
                            } catch (_: Exception) { null }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBlock(ai.packageName); onDismiss() }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (icon != null) {
                                Icon(
                                    painter = BitmapPainter(icon),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = ai.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
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
