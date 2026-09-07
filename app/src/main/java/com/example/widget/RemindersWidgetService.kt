package com.example.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.MainActivity
import com.example.R
import com.example.di.AppContainer
import com.example.domain.model.Reminder
import com.example.domain.model.ReminderRepeat
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Locale

/** Supplies the reminder rows for [RemindersWidget]'s list. */
class RemindersWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        RemindersRemoteViewsFactory(applicationContext)
}

private class RemindersRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private val timeFormat = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault())
    private var items: List<Reminder> = emptyList()

    override fun onCreate() {}

    // Runs on a binder thread, so a short blocking DB read is fine here.
    override fun onDataSetChanged() {
        AppContainer.init(context)
        val now = System.currentTimeMillis()
        val all = runBlocking {
            val hideContent = runCatching { AppContainer.settingsRepository?.snapshot()?.appLockEnabled != false }.getOrDefault(true)
            val reminders = AppContainer.reminderRepository?.enabledReminders() ?: emptyList()
            if (hideContent) reminders.map { it.copy(title = "Private reminder", body = "") } else reminders
        }
        items = all
            .filter { it.repeat != ReminderRepeat.NONE || it.triggerAt >= now }
            .sortedBy { it.triggerAt }
            .take(25)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_reminder_item)
        val reminder = items.getOrNull(position) ?: return views
        views.setTextViewText(R.id.item_title, reminder.title.ifBlank { "Reminder" })
        val repeatSuffix = if (reminder.repeat != ReminderRepeat.NONE) " · ${reminder.repeat.label}" else ""
        views.setTextViewText(R.id.item_time, timeFormat.format(reminder.triggerAt) + repeatSuffix)
        val fillIn = Intent().apply {
            if (reminder.noteId != null) putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, reminder.noteId)
            else putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
