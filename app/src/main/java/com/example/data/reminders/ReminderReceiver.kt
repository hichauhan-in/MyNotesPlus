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
                val reminder = repo.getById(id) ?: return@launch
                if (!reminder.enabled) return@launch
                NotificationHelper.notify(appContext, reminder)
                val next = ReminderScheduler.nextOccurrence(reminder.triggerAt, reminder.repeat)
                if (next != null) {
                    repo.setTriggerAt(id, next)
                    ReminderScheduler.schedule(appContext, reminder.copy(triggerAt = next))
                } else {
                    // One-shot: mark done so it isn't re-armed on the next reboot.
                    repo.setEnabled(id, false)
                }
                WidgetUpdater.refreshAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.reminder.FIRE"
        const val EXTRA_ID = "reminder_id"
    }
}
