package com.trackgas.app.reminders

object ReminderPolicy {
    const val REMINDER_DELAY_MILLIS = 24 * 60 * 60 * 1000L

    fun isDue(lastSubmissionMillis: Long, nowMillis: Long): Boolean =
        nowMillis >= lastSubmissionMillis &&
            nowMillis - lastSubmissionMillis >= REMINDER_DELAY_MILLIS
}
