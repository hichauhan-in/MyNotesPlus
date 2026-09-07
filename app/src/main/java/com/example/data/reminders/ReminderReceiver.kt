package com.example.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.AppContainer
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a reminder alarm goes off: posts the notification and reschedules if it repeats. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppContainer.init(appContext)
                val repo = AppContainer.reminderRepository ?: return@launch
                val expectedAt = if (intent.hasExtra(EXTRA_AT)) intent.getLongExtra(EXTRA_AT, 0) else null
                repo.fire(appContext, id, expectedAt)
                WidgetUpdater.refreshAll(appContext)
            } catch (failure: Exception) {
                android.util.Log.w("MyNotesReminders", "Could not deliver reminder", failure)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.reminder.FIRE"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_AT = "reminder_occurrence"
    }
}
