package com.trackgas.app.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

interface ReminderScheduler {
    fun schedule()
    fun cancel()
}

class WorkManagerReminderScheduler(context: Context) : ReminderScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(ReminderPolicy.REMINDER_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(REMINDER_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(REMINDER_WORK_NAME)
    }
}

internal const val REMINDER_WORK_NAME = "daily-reading-reminder"
