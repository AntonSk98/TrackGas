package com.trackgas.app.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    @Test
    fun reminderIsNotDueBeforeTwentyFourHours() {
        assertFalse(ReminderPolicy.isDue(1_000L, 1_000L + ReminderPolicy.REMINDER_DELAY_MILLIS - 1))
    }

    @Test
    fun reminderIsDueAtTwentyFourHours() {
        assertTrue(ReminderPolicy.isDue(1_000L, 1_000L + ReminderPolicy.REMINDER_DELAY_MILLIS))
    }

    @Test
    fun reminderIsNotDueWhenClockMovesBackwards() {
        assertFalse(ReminderPolicy.isDue(2_000L, 1_000L))
    }
}
