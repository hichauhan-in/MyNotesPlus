package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R

/** A compact one-tap widget that opens a fresh note ready to type. */
class QuickCaptureWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_capture).apply {
                setOnClickPendingIntent(R.id.capture_root, quickActionPendingIntent(context, QuickAction.NOTE))
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
