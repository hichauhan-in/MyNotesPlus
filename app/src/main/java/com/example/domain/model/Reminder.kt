package com.example.domain.model

import java.util.UUID

/** How often a reminder repeats after its first fire. */
enum class ReminderRepeat(val label: String) {
    NONE("Once"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
}

/**
 * A scheduled reminder. It can stand alone or be attached to a note ([noteId]); [body] carries the
 * specific thing to be reminded of (e.g. a line/section from a note) and shows in the notification.
 * Title/body are encrypted at rest just like note content.
 */
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String = "",
    /** The note this reminder opens when tapped, or null for a standalone reminder. */
    val noteId: String? = null,
    /** Epoch millis of the next time this reminder should fire. */
    val triggerAt: Long,
    val repeat: ReminderRepeat = ReminderRepeat.NONE,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Last time this reminder was created or changed - the tiebreaker for cloud last-write-wins. */
    val updatedAt: Long = System.currentTimeMillis(),
)
