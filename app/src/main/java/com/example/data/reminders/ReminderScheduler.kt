package com.example.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.domain.model.Reminder
import com.example.domain.model.ReminderRepeat
import java.util.Calendar

/** Schedules / cancels reminder alarms via [AlarmManager]. */
object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        if (!reminder.enabled || reminder.isCompleted || reminder.lastNotifiedAt == reminder.effectiveAt) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, reminder.id, reminder.effectiveAt)
        val at = reminder.effectiveAt
        try {
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // No exact-alarm permission: an inexact alarm still fires (within a Doze window).
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    fun cancel(context: Context, reminderId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, reminderId))
    }

    /**
     * Arms a reminder that just arrived from cloud sync, returning it normalized to a consistent
     * stored state. Crucially it never fires a **stale, already-past** slot just because another
     * device learned about the reminder late:
     *  - Disabled (e.g. a completed one-shot) -> alarm cancelled, returned unchanged (stays done).
     *  - Future -> scheduled normally (this device will fire it at the right time, like any other).
     *  - Past one-shot -> **not fired**; returned completed (`enabled = false`) so it settles as done
     *    everywhere instead of spamming a notification the moment it's synced.
     *  - Past repeating -> advanced to its next **future** occurrence and scheduled there, so it
     *    resumes at the next slot instead of firing a backlog.
     * The bumped `updatedAt` on a changed reminder lets that normalized state win the next merge and
     * propagate to the other devices. The caller persists the returned reminder.
     */
    fun armSynced(context: Context, reminder: Reminder): Reminder {
        if (!reminder.enabled) {
            cancel(context, reminder.id)
            return reminder
        }
        val now = System.currentTimeMillis()
        if (reminder.effectiveAt > now) {
            schedule(context, reminder)
            return reminder
        }
        // Past due at the moment we learned about it via sync - do not fire it immediately.
        return if (reminder.repeat == ReminderRepeat.NONE) {
            cancel(context, reminder.id)
            reminder
        } else {
            val next = nextOccurrence(reminder.repeatAnchorAt, reminder.repeat, now)
            if (next == null) {
                cancel(context, reminder.id)
                reminder.copy(enabled = false, updatedAt = now)
            } else {
                val advanced = reminder.copy(triggerAt = next, updatedAt = now)
                schedule(context, advanced)
                advanced
            }
        }
    }

    private fun canExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    /** Whether exact alarms are currently allowed (drives the Settings "precise reminders" hint). */
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return canExact(am)
    }

    /** The next fire time strictly after [from] for a repeating reminder, or null for a one-shot. */
    fun nextOccurrence(triggerAt: Long, repeat: ReminderRepeat, from: Long = System.currentTimeMillis()): Long? {
        return com.example.domain.model.ReminderTiming.nextOccurrence(triggerAt, repeat, from)
    }

    private fun pendingIntent(context: Context, id: String, expectedAt: Long? = null): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            // A unique data Uri keeps each reminder's PendingIntent distinct.
            data = Uri.parse("mynotes://reminder/$id")
            putExtra(ReminderReceiver.EXTRA_ID, id)
            if (expectedAt != null) putExtra(ReminderReceiver.EXTRA_AT, expectedAt)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
