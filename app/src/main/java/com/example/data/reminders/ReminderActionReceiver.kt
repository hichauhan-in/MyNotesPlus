package com.example.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DONE && intent.action != SNOOZE) return
        val id = intent.getStringExtra(ReminderReceiver.EXTRA_ID) ?: return
        if (!intent.hasExtra(ReminderReceiver.EXTRA_AT)) return
        val at = intent.getLongExtra(ReminderReceiver.EXTRA_AT, 0)
        val pending = goAsync()
        val app = context.applicationContext
        AppContainer.applicationScope.launch(Dispatchers.IO) {
            try {
                AppContainer.init(app)
                if (AppContainer.settingsRepository?.snapshot()?.appLockEnabled != false) return@launch
                AppContainer.reminderRepository?.performAction(app, id, at, intent.action == SNOOZE)
            } catch (_: Exception) {
                android.util.Log.w("MyNotesReminders", "Reminder action was not applied")
            } finally { pending.finish() }
        }
    }

    companion object {
        const val DONE = "com.example.reminder.DONE"
        const val SNOOZE = "com.example.reminder.SNOOZE"
    }
}