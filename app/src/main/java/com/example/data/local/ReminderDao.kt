package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRestoredReminders(reminders: List<ReminderEntity>)

    @Query("SELECT * FROM reminders ORDER BY triggerAt ASC")
    fun getAll(): Flow<List<ReminderEntity>>

    /** One-shot read of every reminder (used by cloud sync to build the merge set). */
    @Query("SELECT * FROM reminders")
    suspend fun getAllOnce(): List<ReminderEntity>

    /** Emits on every write to the reminders table - a lightweight "something changed" signal. */
    @Query("SELECT COUNT(*) FROM reminders")
    fun changeSignal(): Flow<Int>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabledOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity)

    @Query("UPDATE reminders SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE reminders SET triggerAt = :triggerAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTriggerAt(id: String, triggerAt: Long, updatedAt: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)
}
