package com.trackgas.app.reminders

import android.content.Context
import android.content.SharedPreferences

interface ReminderStore {
    fun lastSubmissionMillis(): Long?
    fun setLastSubmissionMillis(timestampMillis: Long)
    fun clearLastSubmissionMillis()
    fun permissionDeclined(): Boolean
    fun setPermissionDeclined(declined: Boolean)
}

class SharedPreferencesReminderStore(context: Context) : ReminderStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun lastSubmissionMillis(): Long? =
        if (preferences.contains(KEY_LAST_SUBMISSION)) preferences.getLong(KEY_LAST_SUBMISSION, 0L) else null

    override fun setLastSubmissionMillis(timestampMillis: Long) {
        preferences.edit().putLong(KEY_LAST_SUBMISSION, timestampMillis).apply()
    }

    override fun clearLastSubmissionMillis() {
        preferences.edit().remove(KEY_LAST_SUBMISSION).apply()
    }

    override fun permissionDeclined(): Boolean = preferences.getBoolean(KEY_PERMISSION_DECLINED, false)

    override fun setPermissionDeclined(declined: Boolean) {
        preferences.edit().putBoolean(KEY_PERMISSION_DECLINED, declined).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "reading_reminder"
        const val KEY_LAST_SUBMISSION = "last_submission_millis"
        const val KEY_PERMISSION_DECLINED = "notification_permission_declined"
    }
}
