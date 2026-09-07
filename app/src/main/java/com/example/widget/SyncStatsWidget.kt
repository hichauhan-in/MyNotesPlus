package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A 2x2 widget showing the live note count and the Drive backup status. No note content is shown. */
class SyncStatsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Reading the DB + settings is quick but off the main thread; keep the broadcast alive.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val count = runCatching { AppContainer.noteRepository?.activeNoteCount() ?: 0 }.getOrDefault(0)
            val settings = runCatching { AppContainer.settingsRepository?.snapshot() }.getOrNull()
            val status = when {
                settings == null || settings.driveAccountEmail == null ->
                    context.getString(R.string.widget_stats_syncoff)
                !settings.recoveryConfigured ->
                    context.getString(R.string.widget_stats_notbacked)
                settings.lastSyncedAt > 0L ->
                    context.getString(R.string.widget_stats_synced)
                else ->
                    context.getString(R.string.widget_stats_notbacked)
            }
            try {
                appWidgetIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_sync_stats).apply {
                        setTextViewText(R.id.stats_count, count.toString())
                        setTextViewText(R.id.stats_status, status)
                        setOnClickPendingIntent(R.id.stats_root, openAppPendingIntent(context))
                    }
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
