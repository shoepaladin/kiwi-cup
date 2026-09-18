package com.kiwicup.scheduledmessenger.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.local.dao.ScheduledMessageDao
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.data.sms.SendResult
import com.kiwicup.scheduledmessenger.data.sms.SmsSender
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sends one [com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage].
 *
 * Contract with the database: the worker may only touch the radio after `claimForSending`
 * returned 1. Every exit path leaves the row in a terminal state or back in PENDING, never
 * stuck in SENDING.
 */
@HiltWorker
class ScheduledSmsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduledMessageDao: ScheduledMessageDao,
    private val smsMessageDao: SmsMessageDao,
    private val smsSender: SmsSender,
    private val timeSource: TimeSource
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_MESSAGE_ID, -1L)
        if (id < 0) return Result.failure()

        val message = scheduledMessageDao.getById(id) ?: return Result.success() // deleted meanwhile
        val now = timeSource.now()

        if (!hasSendPermission()) {
            scheduledMessageDao.markFailed(id, REASON_NO_PERMISSION, now)
            return Result.failure()
        }

        if (scheduledMessageDao.claimForSending(id, now) != 1) {
            // Cancelled, already sent, or another worker owns it. Nothing to do.
            return Result.success()
        }

        return when (val outcome = smsSender.send(message.recipientAddress, message.messageBody)) {
            SendResult.Sent -> {
                val sentAt = timeSource.now()
                scheduledMessageDao.markSent(id, sentAt)
                recordSentCopy(message.recipientAddress, message.messageBody, message.threadId, sentAt)
                Result.success()
            }
            is SendResult.PermanentFailure -> {
                scheduledMessageDao.markFailed(id, outcome.reason, timeSource.now())
                Result.failure()
            }
            is SendResult.TransientFailure -> {
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                    scheduledMessageDao.markFailed(id, "${outcome.reason} (gave up after $MAX_ATTEMPTS attempts)", timeSource.now())
                    Result.failure()
                } else {
                    scheduledMessageDao.releaseClaim(id, timeSource.now())
                    Result.retry()
                }
            }
        }
    }

    private suspend fun recordSentCopy(address: String, body: String, threadId: Long?, sentAt: Long) {
        val resolvedThread = threadId
            ?: smsMessageDao.findThreadIdByAddress(address)
            ?: smsMessageDao.nextThreadId()
        smsMessageDao.insert(
            SmsMessage(
                threadId = resolvedThread,
                address = address,
                body = body,
                timestamp = sentAt,
                status = SmsStatus.SENT,
                isIncoming = false
            )
        )
    }

    private fun hasSendPermission(): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val KEY_MESSAGE_ID = "messageId"
        const val MAX_ATTEMPTS = 3
        const val REASON_NO_PERMISSION = "SEND_SMS permission not granted"
    }
}
