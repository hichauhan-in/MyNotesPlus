package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room row for a reminder. Title/body are stored encrypted (like notes); the rest is metadata. */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val encryptedTitle: String,
    val encryptedBody: String,
    val noteId: String?,
    val triggerAt: Long,
    /** ReminderRepeat name (NONE / DAILY / WEEKLY / MONTHLY). */
    val repeat: String,
    val enabled: Boolean,
    val createdAt: Long,
    /** Last create/change time - used as the last-write-wins tiebreaker during cloud sync. */
    val updatedAt: Long = 0L,
)
