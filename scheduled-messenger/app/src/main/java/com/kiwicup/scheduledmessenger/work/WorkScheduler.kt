package com.kiwicup.scheduledmessenger.work

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kiwicup.scheduledmessenger.core.RecoveryAction
import com.kiwicup.scheduledmessenger.core.SchedulingPolicy
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.core.WorkNames
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Translates database rows into WorkManager requests. Pure glue; the policy lives in `core`. */
@Singleton
class WorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val policy: SchedulingPolicy,
    private val timeSource: TimeSource
) {

    /** Returns the work id, or null when the policy says the target is too stale to run at all. */
    fun scheduleSms(messageId: Long, targetTimestamp: Long): UUID? {
        val delay = when (val action = policy.recoveryAction(timeSource.now(), targetTimestamp)) {
            is RecoveryAction.DispatchLater -> action.delayMillis
            RecoveryAction.DispatchNow -> 0L
            RecoveryAction.Expire -> return null
        }
        val request = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
            .setInputData(workDataOf(ScheduledSmsWorker.KEY_MESSAGE_ID to messageId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WorkNames.TAG_SCHEDULED_SMS)
            .build()
        workManager.enqueueUniqueWork(WorkNames.scheduledSms(messageId), ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    fun cancelSms(messageId: Long) {
        workManager.cancelUniqueWork(WorkNames.scheduledSms(messageId))
    }

    /** Reminders never expire: a late reminder is still useful, so the delay is simply clamped to zero. */
    fun scheduleReminder(reminderId: Long, triggerTimestamp: Long): UUID {
        val delay = policy.initialDelayMillis(timeSource.now(), triggerTimestamp)
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminderId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WorkNames.TAG_REMINDER)
            .build()
        workManager.enqueueUniqueWork(WorkNames.reminder(reminderId), ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    fun cancelReminder(reminderId: Long) {
        workManager.cancelUniqueWork(WorkNames.reminder(reminderId))
    }

    companion object {
        const val RETRY_BACKOFF_SECONDS = 30L
    }
}
