package com.activitytrace.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.activitytrace.R
import com.activitytrace.capture.AccessibilityCaptureService
import com.activitytrace.store.ActivityTraceDatabase
import kotlinx.coroutines.flow.emptyFlow

private fun isNotificationListenerGranted(context: Context): Boolean = try {
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
} catch (_: Exception) {
    false
}

private fun isAccessibilityServiceGranted(context: Context): Boolean = try {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )
    val serviceName = "${context.packageName}/${AccessibilityCaptureService::class.java.name}"
    enabledServices?.split(":")?.any { it.trim() == serviceName } == true
} catch (_: Exception) {
    false
}

/**
 * The "Capture" status card, shared by Settings and the onboarding welcome
 * screen so both always show the same state and actions: per-source status
 * rows, blocked-apps count, and (when anything is turned off) the enable
 * buttons plus the Restricted Settings note.
 */
@Composable
fun CaptureStatusSection(context: Context) {
    var notificationGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            notificationGranted = isNotificationListenerGranted(context)
            accessibilityGranted = isAccessibilityServiceGranted(context)
        }
    }

    val blockedAppsFlow = remember {
        runCatching { ActivityTraceDatabase.getInstance(context).blockedAppDao().blockedAppsFlow() }
            .getOrNull() ?: emptyFlow()
    }
    val blockedApps by blockedAppsFlow.collectAsState(initial = emptyList())
    val capturePartial = !(notificationGranted && accessibilityGranted)

    Text(
        text = stringResource(R.string.capture_status_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusRow(
                label = stringResource(R.string.capture_screen_activity),
                enabled = accessibilityGranted,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
            Spacer(Modifier.height(4.dp))
            StatusRow(
                label = stringResource(R.string.capture_notifications),
                enabled = notificationGranted,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.capture_blocked_count,
                    blockedApps.size,
                    blockedApps.size,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (capturePartial) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.capture_partial_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!notificationGranted) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.enable_notifications))
                    }
                }
                if (!accessibilityGranted) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.enable_accessibility))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.restricted_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = CircleShape,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        ) {}
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = if (enabled) stringResource(R.string.capture_enabled)
                   else stringResource(R.string.capture_disabled),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
        )
    }
}