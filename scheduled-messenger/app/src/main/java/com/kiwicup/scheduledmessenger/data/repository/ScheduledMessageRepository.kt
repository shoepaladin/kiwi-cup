package com.kiwicup.scheduledmessenger.data.repository

import com.kiwicup.scheduledmessenger.core.RecipientValidator
import com.kiwicup.scheduledmessenger.core.SchedulingPolicy
import com.kiwicup.scheduledmessenger.core.SmsTextAnalyzer
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.local.dao.ScheduledMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.work.SchedulerApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

sealed class ScheduleError(val message: String) {
    object InvalidRecipient : ScheduleError("Enter a valid phone number")
    object EmptyBody : ScheduleError("Message cannot be empty")
    object TimeInPast : ScheduleError("Pick a time in the future")
}

/** Single entry point for creating, editing and cancelling scheduled texts. */
@Singleton
class ScheduledMessageRepository @Inject constructor(
    private val dao: ScheduledMessageDao,
    private val scheduler: SchedulerApi,
    private val policy: SchedulingPolicy,
    private val timeSource: TimeSource
) {
    fun observeAll(): Flow<List<ScheduledMessage>> = dao.observeAll()
    fun observePending(): Flow<List<ScheduledMessage>> = dao.observePending()
    fun observeHistory(): Flow<List<ScheduledMessage>> = dao.observeHistory()
    fun observeById(id: Long): Flow<ScheduledMessage?> = dao.observeById(id)
    suspend fun getById(id: Long): ScheduledMessage? = dao.getById(id)

    fun validate(recipient: String, body: String, targetTimestamp: Long): ScheduleError? = when {
        !RecipientValidator.isValid(recipient) -> ScheduleError.InvalidRecipient
        !SmsTextAnalyzer.isSendable(body) -> ScheduleError.EmptyBody
        !policy.isValidTarget(timeSource.now(), targetTimestamp) -> ScheduleError.TimeInPast
        else -> null
    }

    /** Validates, persists and enqueues. Returns the new row id. */
    suspend fun schedule(
        recipient: String,
        body: String,
        targetTimestamp: Long,
        threadId: Long? = null
    ): Result<Long> {
        validate(recipient, body, targetTimestamp)?.let { return Result.failure(IllegalArgumentException(it.message)) }
        val now = timeSource.now()
        val id = dao.insert(
            ScheduledMessage(
                recipientAddress = RecipientValidator.normalize(recipient),
                messageBody = body.trim(),
                targetTimestamp = targetTimestamp,
                threadId = threadId,
                createdAt = now,
                updatedAt = now
            )
        )
        enqueue(id, targetTimestamp)
        return Result.success(id)
    }

    /** Cancels the pending row. Returns false when a worker already claimed it. */
    suspend fun cancel(id: Long): Boolean {
        val cancelled = dao.cancel(id, timeSource.now()) == 1
        if (cancelled) scheduler.cancelSms(id)
        return cancelled
    }

    suspend fun delete(id: Long) {
        scheduler.cancelSms(id)
        dao.deleteById(id)
    }

    /** Edits a still-pending message and re-arms its job. */
    suspend fun edit(id: Long, recipient: String, body: String, targetTimestamp: Long): Result<Unit> {
        validate(recipient, body, targetTimestamp)?.let { return Result.failure(IllegalArgumentException(it.message)) }
        val updated = dao.editPending(id, RecipientValidator.normalize(recipient), body.trim(), targetTimestamp, timeSource.now())
        if (updated != 1) return Result.failure(IllegalStateException("Message is no longer pending"))
        enqueue(id, targetTimestamp)
        return Result.success(Unit)
    }

    /** Puts a FAILED or CANCELLED message back in the queue at a new time. */
    suspend fun reschedule(id: Long, targetTimestamp: Long): Result<Unit> {
        if (!policy.isValidTarget(timeSource.now(), targetTimestamp)) {
            return Result.failure(IllegalArgumentException(ScheduleError.TimeInPast.message))
        }
        val updated = dao.reschedule(id, targetTimestamp, timeSource.now())
        if (updated != 1) return Result.failure(IllegalStateException("Only failed or cancelled messages can be rescheduled"))
        enqueue(id, targetTimestamp)
        return Result.success(Unit)
    }

    /** Used after reboot / app update: re-arms every PENDING row. Returns how many were re-armed. */
    suspend fun reenqueueAllPending(): Int {
        var count = 0
        dao.getPending().forEach { message ->
            if (enqueue(message.id, message.targetTimestamp)) count++
        }
        return count
    }

    suspend fun clearHistory(): Int = dao.clearHistory()

    private suspend fun enqueue(id: Long, targetTimestamp: Long): Boolean {
        val workId = scheduler.scheduleSms(id, targetTimestamp)
        return if (workId == null) {
            dao.markFailed(id, REASON_MISSED, timeSource.now())
            false
        } else {
            dao.setWorkRequestId(id, workId.toString(), timeSource.now())
            true
        }
    }

    companion object {
        const val REASON_MISSED = "Missed: scheduled time passed while the device was off"
    }
}
