package com.kiwicup.scheduledmessenger.data.repository

import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.AttachmentCodec
import com.kiwicup.scheduledmessenger.core.Recipients
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

    /**
     * [recipient] may list several numbers separated by commas for a group message.
     * A message with attachments may have an empty body.
     */
    fun validate(recipient: String, body: String, targetTimestamp: Long, attachments: List<Attachment> = emptyList()): ScheduleError? = when {
        Recipients.normalizeAll(recipient) == null -> ScheduleError.InvalidRecipient
        !SmsTextAnalyzer.isSendable(body) && attachments.isEmpty() -> ScheduleError.EmptyBody
        !policy.isValidTarget(timeSource.now(), targetTimestamp) -> ScheduleError.TimeInPast
        else -> null
    }

    /** Validates, persists and enqueues. Returns the new row id. */
    suspend fun schedule(
        recipient: String,
        body: String,
        targetTimestamp: Long,
        threadId: Long? = null,
        attachments: List<Attachment> = emptyList()
    ): Result<Long> {
        validate(recipient, body, targetTimestamp, attachments)?.let { return Result.failure(IllegalArgumentException(it.message)) }
        val now = timeSource.now()
        val id = dao.insert(
            ScheduledMessage(
                recipientAddress = Recipients.encode(Recipients.normalizeAll(recipient)!!),
                messageBody = body.trim(),
                attachments = AttachmentCodec.encode(attachments),
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
        val existing = dao.getById(id) ?: return Result.failure(IllegalStateException("Message not found"))
        validate(recipient, body, targetTimestamp, AttachmentCodec.decode(existing.attachments))?.let { return Result.failure(IllegalArgumentException(it.message)) }
        val updated = dao.editPending(id, Recipients.encode(Recipients.normalizeAll(recipient)!!), body.trim(), targetTimestamp, timeSource.now())
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

    /**
     * Used after reboot / app start: makes sure every PENDING row has a job, without touching
     * jobs that already exist (an in-flight send must never be cancelled and restarted).
     */
    suspend fun reenqueueAllPending(): Int {
        var count = 0
        dao.getPending().forEach { message ->
            if (enqueue(message.id, message.targetTimestamp, replace = false)) count++
        }
        return count
    }

    suspend fun clearHistory(): Int = dao.clearHistory()

    private suspend fun enqueue(id: Long, targetTimestamp: Long, replace: Boolean = true): Boolean {
        val workId = scheduler.scheduleSms(id, targetTimestamp, replace)
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
