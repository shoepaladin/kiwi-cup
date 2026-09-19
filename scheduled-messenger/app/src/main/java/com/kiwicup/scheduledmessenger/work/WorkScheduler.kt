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

/** What the repositories need from the scheduler; an interface so tests can substitute or defer it. */
interface SchedulerApi {
    /**
     * Returns the work id, or null when the policy says the target is too stale to run at all.
     * [replace] = false keeps an existing job for this message (used when re-arming after a
     * reboot or app start, so an in-flight send is never cancelled and restarted).
     */
    fun scheduleSms(messageId: Long, targetTimestamp: Long, replace: Boolean = true): UUID?
    fun cancelSms(messageId: Long)
    fun scheduleReminder(reminderId: Long, triggerTimestamp: Long, replace: Boolean = true): UUID
    fun cancelReminder(reminderId: Long)
}

/** Translates database rows into WorkManager requests. Pure glue; the policy lives in `core`. */
@Singleton
class WorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val policy: SchedulingPolicy,
    private val timeSource: TimeSource,
    private val exactAlarms: ExactAlarms
) : SchedulerApi {

    override fun scheduleSms(messageId: Long, targetTimestamp: Long, replace: Boolean): UUID? {
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
        val policyForExisting = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(WorkNames.scheduledSms(messageId), policyForExisting, request)
        // WorkManager alone may run minutes late in Doze; an exact alarm (when allowed) fires the
        // worker at the chosen minute and the delayed job above stays as the safety net.
        if (delay > 0) exactAlarms.scheduleSms(messageId, targetTimestamp)
        return request.id
    }

    /** Fired by the exact alarm: run the message's job now (the claim logic prevents double sends). */
    fun runSmsNow(messageId: Long) {
        val request = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
            .setInputData(workDataOf(ScheduledSmsWorker.KEY_MESSAGE_ID to messageId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WorkNames.TAG_SCHEDULED_SMS)
            .build()
        workManager.enqueueUniqueWork(WorkNames.scheduledSms(messageId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelSms(messageId: Long) {
        workManager.cancelUniqueWork(WorkNames.scheduledSms(messageId))
        exactAlarms.cancelSms(messageId)
    }

    /** Reminders never expire: a late reminder is still useful, so the delay is simply clamped to zero. */
    override fun scheduleReminder(reminderId: Long, triggerTimestamp: Long, replace: Boolean): UUID {
        val delay = policy.initialDelayMillis(timeSource.now(), triggerTimestamp)
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminderId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WorkNames.TAG_REMINDER)
            .build()
        val policyForExisting = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(WorkNames.reminder(reminderId), policyForExisting, request)
        if (delay > 0) exactAlarms.scheduleReminder(reminderId, triggerTimestamp)
        return request.id
    }

    fun runReminderNow(reminderId: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminderId))
            .addTag(WorkNames.TAG_REMINDER)
            .build()
        workManager.enqueueUniqueWork(WorkNames.reminder(reminderId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelReminder(reminderId: Long) {
        workManager.cancelUniqueWork(WorkNames.reminder(reminderId))
        exactAlarms.cancelReminder(reminderId)
    }

    companion object {
        const val RETRY_BACKOFF_SECONDS = 30L
    }
}
