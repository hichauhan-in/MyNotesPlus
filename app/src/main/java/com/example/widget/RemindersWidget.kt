package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

/** A collection widget listing the user's upcoming reminders, with a header + "add" shortcut. */
class RemindersWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_reminders)

            val serviceIntent = Intent(context, RemindersWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                // Make the adapter intent unique per widget id so each keeps its own data.
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.reminders_list, serviceIntent)
            views.setEmptyView(R.id.reminders_list, R.id.reminders_empty)
            views.setOnClickPendingIntent(R.id.reminders_header, openRemindersPendingIntent(context))
            views.setOnClickPendingIntent(R.id.reminders_add, openRemindersPendingIntent(context))
            views.setPendingIntentTemplate(R.id.reminders_list, itemTemplatePendingIntent(context))

            appWidgetManager.updateAppWidget(id, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.reminders_list)
        }
    }

    private fun openRemindersPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.action.OPEN_REMINDERS"
            putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        return PendingIntent.getActivity(
            context,
            201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Template for list-item taps; each row supplies a fill-in intent (open note vs open list). */
    private fun itemTemplatePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.action.REMINDER_ITEM"
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        return PendingIntent.getActivity(
            context,
            202,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
