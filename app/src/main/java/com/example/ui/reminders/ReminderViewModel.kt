package com.example.ui.reminders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.reminders.ReminderScheduler
import com.example.di.AppContainer
import com.example.domain.model.Reminder
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel : ViewModel() {
    private val repository = AppContainer.reminderRepository!!
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private fun runOperation(context: Context, onDone: () -> Unit = {}, operation: suspend (Context) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        AppContainer.applicationScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            try {
                operation(context.applicationContext)
                WidgetUpdater.refreshAll(context.applicationContext)
                onDone()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) { _error.value = failure.message ?: "The reminder was not changed" }
            finally { _busy.value = false }
        }
    }

    val reminders: StateFlow<List<Reminder>> = repository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Save (create or update) a reminder and (re)arm or cancel its alarm to match. */
    fun save(context: Context, reminder: Reminder, onSaved: () -> Unit = {}) {
        runOperation(context, onSaved) { app ->
            require(reminder.effectiveAt > System.currentTimeMillis()) { "Choose a future reminder time" }
            reminder.noteId?.let { requireNotNull(AppContainer.noteRepository?.getNoteById(it)) { "Save the linked note first" } }
            repository.save(reminder)
            if (reminder.enabled) ReminderScheduler.schedule(app, reminder)
            else ReminderScheduler.cancel(app, reminder.id)
        }
    }

    fun setEnabled(context: Context, reminder: Reminder, enabled: Boolean) {
        runOperation(context) { app ->
            repository.setEnabled(reminder.id, enabled)
            if (enabled) repository.getById(reminder.id)?.let { ReminderScheduler.schedule(app, it) }
            else ReminderScheduler.cancel(app, reminder.id)
        }
    }

    fun complete(context: Context, reminder: Reminder) = runOperation(context) { app ->
        repository.performAction(app, reminder.id, reminder.effectiveAt, snooze = false)
    }

    fun snooze(context: Context, reminder: Reminder) = runOperation(context) { app ->
        repository.performAction(app, reminder.id, reminder.effectiveAt, snooze = true)
    }

    fun delete(context: Context, reminder: Reminder) {
        runOperation(context) { app ->
            repository.delete(reminder.id)
            ReminderScheduler.cancel(app, reminder.id)
            androidx.core.app.NotificationManagerCompat.from(app).cancel(reminder.id.hashCode())
        }
    }
}
