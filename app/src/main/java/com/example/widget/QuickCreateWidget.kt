package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R

/** A 4-button widget to quickly create a note, checklist, expense tracker or board. */
class QuickCreateWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_create).apply {
                setOnClickPendingIntent(R.id.btn_note, quickActionPendingIntent(context, QuickAction.NOTE))
                setOnClickPendingIntent(R.id.btn_checklist, quickActionPendingIntent(context, QuickAction.CHECKLIST))
                setOnClickPendingIntent(R.id.btn_expense, quickActionPendingIntent(context, QuickAction.EXPENSE))
                setOnClickPendingIntent(R.id.btn_board, quickActionPendingIntent(context, QuickAction.BOARD))
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
