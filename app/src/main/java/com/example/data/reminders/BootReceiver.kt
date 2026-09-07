package com.example.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-arms all enabled reminders after a reboot (AlarmManager alarms are cleared on boot). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppContainer.init(appContext)
                val repo = AppContainer.reminderRepository ?: return@launch
                repo.enabledReminders().forEach { ReminderScheduler.schedule(appContext, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
