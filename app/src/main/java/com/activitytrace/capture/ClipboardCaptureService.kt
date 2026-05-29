package com.activitytrace.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ClipboardCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastClipText: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
            if (clip == lastClipText) return@OnPrimaryClipChangedListener
            lastClipText = clip
            scope.launch {
                CaptureIngestor.ingest(
                    text = clip,
                    appPackage = "clipboard",
                    contentType = "clipboard",
                )
            }
        }
        clipboard.addPrimaryClipChangedListener(listener)
        clipboardListener = listener
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardListener?.let {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.removePrimaryClipChangedListener(it)
        }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Clipboard Monitor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notification for clipboard monitoring service"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Activity Trace")
            .setContentText("Monitoring clipboard")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .setSilent(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "clipboard_monitor"
        private const val NOTIFICATION_ID = 1001
    }
}
