package com.kiwicup.scheduledmessenger.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kiwicup.scheduledmessenger.data.repository.ReminderRepository
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Re-enqueues every PENDING message and active reminder. Safe to run any number of times. */
@HiltWorker
class RearmWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduledMessages: ScheduledMessageRepository,
    private val reminders: ReminderRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val messages = scheduledMessages.reenqueueAllPending()
        val notes = reminders.reenqueueAllActive()
        return Result.success(workDataOf(KEY_MESSAGES to messages, KEY_REMINDERS to notes))
    }

    companion object {
        const val UNIQUE_NAME = "rearm-after-boot"
        const val KEY_MESSAGES = "messages"
        const val KEY_REMINDERS = "reminders"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RearmWorker>().build()
            )
        }
    }
}
