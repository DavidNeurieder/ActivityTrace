package com.activitytrace.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.activitytrace.MainActivity
import com.activitytrace.R
import com.activitytrace.store.ActivityTraceDatabase
import kotlinx.coroutines.runBlocking

class NotificationWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            try {
                val db = ActivityTraceDatabase.getInstance(context)
                val recentItems = runBlocking {
                    db.captureDao().getAllItems().take(10)
                }
                val count = recentItems.size
                views.setTextViewText(R.id.widget_count, count.toString())
                val text = if (recentItems.isNotEmpty()) {
                    recentItems.joinToString("\n") { it.text.take(80) }
                } else {
                    context.getString(R.string.nothing_captured)
                }
                views.setTextViewText(R.id.widget_text, text.take(500))
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_count, "0")
                views.setTextViewText(R.id.widget_text, "")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
