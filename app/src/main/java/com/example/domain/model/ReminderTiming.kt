package com.example.domain.model

import java.util.Calendar

internal object ReminderTiming {
    fun nextOccurrence(anchorAt: Long, repeat: ReminderRepeat, after: Long): Long? {
        if (repeat == ReminderRepeat.NONE) return null
        val candidate = Calendar.getInstance().apply { timeInMillis = anchorAt }
        val day = candidate.get(Calendar.DAY_OF_MONTH)
        val current = Calendar.getInstance().apply { timeInMillis = after }
        when (repeat) {
            ReminderRepeat.MONTHLY -> {
                val months = ((current.get(Calendar.YEAR) - candidate.get(Calendar.YEAR)) * 12 + current.get(Calendar.MONTH) - candidate.get(Calendar.MONTH)).coerceAtLeast(0)
                candidate.add(Calendar.MONTH, months)
                candidate.set(Calendar.DAY_OF_MONTH, minOf(day, candidate.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            ReminderRepeat.DAILY, ReminderRepeat.WEEKLY -> {
                val period = if (repeat == ReminderRepeat.DAILY) 86_400_000L else 604_800_000L
                val jumps = ((after - anchorAt).coerceAtLeast(0) / period - 1).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                candidate.add(if (repeat == ReminderRepeat.DAILY) Calendar.DAY_OF_YEAR else Calendar.WEEK_OF_YEAR, jumps)
            }
            ReminderRepeat.NONE -> Unit
        }
        while (candidate.timeInMillis <= after) {
            when (repeat) {
                ReminderRepeat.DAILY -> candidate.add(Calendar.DAY_OF_YEAR, 1)
                ReminderRepeat.WEEKLY -> candidate.add(Calendar.WEEK_OF_YEAR, 1)
                ReminderRepeat.MONTHLY -> {
                    candidate.set(Calendar.DAY_OF_MONTH, 1)
                    candidate.add(Calendar.MONTH, 1)
                    candidate.set(Calendar.DAY_OF_MONTH, minOf(day, candidate.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                ReminderRepeat.NONE -> Unit
            }
        }
        return candidate.timeInMillis
    }

    fun delivered(reminder: Reminder, now: Long): Reminder {
        val next = if (reminder.repeat == ReminderRepeat.NONE) reminder.triggerAt
        else nextOccurrence(reminder.repeatAnchorAt, reminder.repeat, now) ?: reminder.triggerAt
        return reminder.copy(triggerAt = next, lastNotifiedAt = reminder.effectiveAt, snoozedUntil = null, updatedAt = now)
    }

    fun complete(reminder: Reminder, now: Long, occurrenceAt: Long = reminder.effectiveAt): Reminder {
        if (reminder.isCompleted) return reminder
        val next = if (reminder.repeat == ReminderRepeat.NONE) null else {
            if (occurrenceAt < reminder.triggerAt && reminder.triggerAt > now) reminder.triggerAt
            else nextOccurrence(reminder.repeatAnchorAt, reminder.repeat, maxOf(now, reminder.triggerAt))
        }
        return reminder.copy(enabled = next != null, completedAt = now, triggerAt = next ?: reminder.triggerAt,
            lastNotifiedAt = null, snoozedUntil = null, updatedAt = now)
    }

    fun snooze(reminder: Reminder, now: Long): Reminder {
        require(!reminder.isCompleted) { "This reminder is completed" }
        return reminder.copy(enabled = true, snoozedUntil = Math.addExact(now, 15 * 60_000L), lastNotifiedAt = null, updatedAt = now)
    }

    fun acceptsAction(reminder: Reminder, occurrenceAt: Long): Boolean = reminder.enabled && !reminder.isCompleted &&
        (occurrenceAt == reminder.effectiveAt || (occurrenceAt == reminder.lastNotifiedAt && (reminder.completedAt ?: Long.MIN_VALUE) < occurrenceAt))
}