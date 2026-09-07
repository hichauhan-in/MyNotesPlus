package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.MainActivity

/** Quick-action values understood by [MainActivity] - each opens the editor with a note type. */
internal object QuickAction {
    const val NOTE = "note"
    const val CHECKLIST = "checklist"
    const val EXPENSE = "expense"
    const val BOARD = "board"
}

/** A PendingIntent that launches the app and immediately creates a note of [action]'s type. */
internal fun quickActionPendingIntent(context: Context, action: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        // A unique action string keeps each button's PendingIntent (and its extras) distinct.
        this.action = "com.example.action.QUICK_$action"
        putExtra(MainActivity.EXTRA_QUICK_ACTION, action)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
    }
    return PendingIntent.getActivity(
        context,
        action.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** A PendingIntent that simply opens the app on its home screen. */
internal fun openAppPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
    }
    return PendingIntent.getActivity(
        context,
        100,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Asks every installed MyNotes widget to redraw (e.g. after the note count or sync state changes). */
object WidgetUpdater {
    fun refreshAll(context: Context) {
        val ctx = context.applicationContext
        val manager = AppWidgetManager.getInstance(ctx)
        val providers = listOf(
            QuickCreateWidget::class.java,
            QuickCaptureWidget::class.java,
            SyncStatsWidget::class.java,
            RemindersWidget::class.java,
        )
        providers.forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) {
                val intent = Intent(ctx, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                ctx.sendBroadcast(intent)
            }
        }
    }
}
