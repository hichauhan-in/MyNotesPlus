package com.example.data.repository

import android.content.Context
import com.example.data.local.ReminderDao
import com.example.data.local.ReminderEntity
import com.example.data.reminders.ReminderScheduler
import com.example.data.security.EncryptionManager
import com.example.domain.model.Reminder
import com.example.domain.model.ReminderRepeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Stores reminders, encrypting their title/body at rest just like note content. */
class ReminderRepository(private val dao: ReminderDao) {

    val reminders: Flow<List<Reminder>> = dao.getAll()
        .map { list -> list.map { it.toReminder() } }
        .flowOn(Dispatchers.Default)

    /** Emits whenever the reminders table changes - a trigger for cloud sync. */
    fun changeSignal(): Flow<Int> = dao.changeSignal()

    suspend fun getById(id: String): Reminder? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toReminder()
    }

    suspend fun enabledReminders(): List<Reminder> = withContext(Dispatchers.IO) {
        dao.getEnabledOnce().map { it.toReminder() }
    }

    suspend fun save(reminder: Reminder): Unit = withContext(Dispatchers.IO) {
        // Stamp the change so a newer edit always wins the cloud last-write-wins merge.
        dao.upsert(reminder.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    suspend fun setTriggerAt(id: String, triggerAt: Long): Unit = withContext(Dispatchers.IO) {
        dao.setTriggerAt(id, triggerAt, System.currentTimeMillis())
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun deleteForNote(noteId: String): Unit = withContext(Dispatchers.IO) {
        dao.deleteForNote(noteId)
    }

    /** Every reminder (decrypted) - used by cloud sync to build the merge set. */
    suspend fun allForSync(): List<Reminder> = withContext(Dispatchers.IO) {
        dao.getAllOnce().map { it.toReminder() }
    }

    /**
     * Applies the result of a cloud sync: writes [upserts] (new / changed reminders pulled from
     * Drive) and removes [deleteIds] (reminders deleted on another device). Each pulled reminder is
     * armed via [ReminderScheduler.armSynced], which schedules only **future** alarms and never
     * fires a stale past slot - a past one-shot settles as completed (disabled), a past repeating
     * one resumes at its next future occurrence. The (possibly normalized) reminder is what gets
     * stored, so a device that just learned about an old reminder never spams a notification. Runs
     * on IO.
     */
    suspend fun applySyncedSet(
        context: Context,
        upserts: List<Reminder>,
        deleteIds: Collection<String>,
    ): Unit = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        deleteIds.forEach { id ->
            dao.deleteById(id)
            ReminderScheduler.cancel(appContext, id)
        }
        upserts.forEach { reminder ->
            // armSynced (re)schedules a future alarm, or normalizes a past reminder to a completed /
            // advanced state; we persist exactly what it returns (keeping the merged updatedAt).
            val armed = ReminderScheduler.armSynced(appContext, reminder)
            dao.upsert(armed.toEntity())
        }
    }

    private fun ReminderEntity.toReminder(): Reminder = Reminder(
        id = id,
        title = EncryptionManager.decryptOrNull(encryptedTitle) ?: "",
        body = EncryptionManager.decryptOrNull(encryptedBody) ?: "",
        noteId = noteId,
        triggerAt = triggerAt,
        repeat = runCatching { ReminderRepeat.valueOf(repeat) }.getOrDefault(ReminderRepeat.NONE),
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = if (updatedAt != 0L) updatedAt else createdAt,
    )

    private fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
        id = id,
        encryptedTitle = EncryptionManager.encrypt(title),
        encryptedBody = EncryptionManager.encrypt(body),
        noteId = noteId,
        triggerAt = triggerAt,
        repeat = repeat.name,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
