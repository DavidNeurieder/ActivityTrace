package com.activitytrace.capture

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class ActivityTraceNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "ActivityTraceNL"

        /**
         * Value of the deprecated [Notification.EXTRA_LARGE_ICON]. The extras
         * key is still the only public way to read the large icon that another
         * app attached to its notification.
         */
        private const val EXTRA_LARGE_ICON = "android.largeIcon"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing) return
        val extras = sbn.notification.extras ?: return
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
        val summaryText = extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString()
        if (text == null && title == null && bigText == null && subText == null && summaryText == null) return
        val fullText = listOfNotNull(title, text, subText, bigText, summaryText).joinToString(" — ")
        val imageBlob = extractImage(sbn)
        scope.launch {
            try {
                CaptureIngestor.ingest(
                    text = fullText,
                    appPackage = sbn.packageName,
                    appName = CaptureIngestor.resolveAppName(this@ActivityTraceNotificationListener, sbn.packageName),
                    contentType = "notification",
                    category = sbn.notification.category,
                    timestamp = sbn.postTime,
                    imageBlob = imageBlob,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest notification", e)
            }
        }
    }

    private fun extractImage(sbn: StatusBarNotification): ByteArray? {
        try {
            val notification = sbn.notification
            val extras = notification.extras ?: return null
            BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE, Bitmap::class.java)
                ?.let { return bitmapToBytes(it) }
            BundleCompat.getParcelable(extras, EXTRA_LARGE_ICON, Bitmap::class.java)
                ?.let { return bitmapToBytes(it) }
            notification.getLargeIcon()
                ?.loadDrawable(this)
                ?.toBitmap()
                ?.let { return bitmapToBytes(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract image", e)
        }
        return null
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray? {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 80, stream)
        val bytes = stream.toByteArray()
        stream.close()
        if (bytes.size > 500_000) return null
        return bytes
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
