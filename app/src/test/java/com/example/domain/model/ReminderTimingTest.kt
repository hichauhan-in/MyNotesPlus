package com.example.domain.model

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimingTest {
    @Test fun deliveryDoesNotCompleteAOneShotTask() {
        val reminder = Reminder(title = "Pay bill", triggerAt = 100)
        val delivered = ReminderTiming.delivered(reminder, now = 200)
        assertTrue(delivered.enabled)
        assertFalse(delivered.isCompleted)
        assertEquals(100L, delivered.lastNotifiedAt)
        assertEquals(100L, delivered.effectiveAt)
    }

    @Test fun snoozeChangesOnlyTheAlertTimeAndRejectsStaleActions() {
        val reminder = Reminder(title = "Pay bill", triggerAt = 100)
        val snoozed = ReminderTiming.snooze(reminder, now = 200)
        assertEquals(900200L, snoozed.effectiveAt)
        assertEquals(100L, snoozed.triggerAt)
        assertNull(snoozed.completedAt)
        assertFalse(ReminderTiming.acceptsAction(snoozed, 100))
    }

    @Test fun completionIsIdempotentAndDisablesOneShot() {
        val reminder = ReminderTiming.complete(Reminder(title = "Pay bill", triggerAt = 100), now = 200)
        assertTrue(reminder.isCompleted)
        assertFalse(reminder.enabled)
        assertEquals(reminder, ReminderTiming.complete(reminder, now = 300))
    }

    @Test fun monthlyRepeatReturnsToTheOriginalDayAfterFebruary() {
        val anchor = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 31, 9, 0) }.timeInMillis
        val february = requireNotNull(ReminderTiming.nextOccurrence(anchor, ReminderRepeat.MONTHLY, anchor))
        val march = requireNotNull(ReminderTiming.nextOccurrence(anchor, ReminderRepeat.MONTHLY, february))
        val date = Calendar.getInstance().apply { timeInMillis = march }
        assertEquals(Calendar.MARCH, date.get(Calendar.MONTH))
        assertEquals(31, date.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun completingTheNotificationDoesNotSkipTheNextRepeat() {
        val reminder = Reminder(title = "Daily", triggerAt = 100, repeat = ReminderRepeat.DAILY)
        val delivered = ReminderTiming.delivered(reminder, now = 200)
        val done = ReminderTiming.complete(delivered, now = 300, occurrenceAt = 100)
        assertEquals(delivered.triggerAt, done.triggerAt)
        assertTrue(done.enabled)
        assertFalse(done.isCompleted)
    }
}