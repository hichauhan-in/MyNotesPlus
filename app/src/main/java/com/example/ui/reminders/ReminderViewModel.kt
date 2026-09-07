package com.example.ui.reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.reminders.ReminderScheduler
import com.example.di.AppContainer
import com.example.domain.model.Reminder
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel : ViewModel() {
    private val repository = AppContainer.reminderRepository!!

    val reminders: StateFlow<List<Reminder>> = repository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Save (create or update) a reminder and (re)arm or cancel its alarm to match. */
    fun save(context: Context, reminder: Reminder) {
        val app = context.applicationContext
        viewModelScope.launch {
            repository.save(reminder)
            if (reminder.enabled) ReminderScheduler.schedule(app, reminder)
            else ReminderScheduler.cancel(app, reminder.id)
            WidgetUpdater.refreshAll(app)
        }
    }

    fun setEnabled(context: Context, reminder: Reminder, enabled: Boolean) {
        val app = context.applicationContext
        viewModelScope.launch {
            repository.setEnabled(reminder.id, enabled)
            if (enabled) ReminderScheduler.schedule(app, reminder.copy(enabled = true))
            else ReminderScheduler.cancel(app, reminder.id)
            WidgetUpdater.refreshAll(app)
        }
    }

    fun delete(context: Context, reminder: Reminder) {
        val app = context.applicationContext
        viewModelScope.launch {
            repository.delete(reminder.id)
            ReminderScheduler.cancel(app, reminder.id)
            WidgetUpdater.refreshAll(app)
        }
    }
}
