package com.kiwicup.scheduledmessenger.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kiwicup.scheduledmessenger.data.local.dao.ReminderDao
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.notifications.ReminderNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Shows the reminder notification exactly once.
 *
 * If notifications are blocked the reminder is left active rather than completed, so the
 * queue screen still surfaces it as overdue instead of silently swallowing it.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminderDao: ReminderDao,
    private val smsMessageDao: SmsMessageDao,
    private val notifier: ReminderNotifier
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_REMINDER_ID, -1L)
        if (id < 0) return Result.failure()

        val reminder = reminderDao.getById(id) ?: return Result.success()
        if (reminder.isCompleted) return Result.success()

        if (!notifier.canNotify()) {
            return Result.failure()
        }

        // Guarded update: only the first worker to flip the flag posts the notification.
        if (reminderDao.markCompleted(id) != 1) return Result.success()

        val title = smsMessageDao.getThread(reminder.threadId).firstOrNull()?.address
        notifier.notify(reminder, title)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminderId"
    }
}
