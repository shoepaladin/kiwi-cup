package com.kiwicup.scheduledmessenger.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kiwicup.scheduledmessenger.core.AttachmentCodec
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.local.dao.ScheduledMessageDao
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.data.sms.MmsSender
import com.kiwicup.scheduledmessenger.data.sms.OutgoingMms
import com.kiwicup.scheduledmessenger.data.sms.SendResult
import com.kiwicup.scheduledmessenger.data.sms.SmsSender
import com.kiwicup.scheduledmessenger.data.system.SystemMessageStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sends one [ScheduledMessage].
 *
 * Contract with the database: the worker may only touch the radio after `claimForSending`
 * returned 1. Every exit path leaves the row in a terminal state or back in PENDING, never
 * stuck in SENDING. Plain texts to one person go out as SMS; pictures or several recipients go
 * out as MMS (which also carries its own copy into the phone's store).
 */
@HiltWorker
class ScheduledSmsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduledMessageDao: ScheduledMessageDao,
    private val smsMessageDao: SmsMessageDao,
    private val smsSender: SmsSender,
    private val mmsSender: MmsSender,
    private val systemStore: SystemMessageStore,
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

        val recipients = Recipients.decode(message.recipientAddress)
        val attachments = AttachmentCodec.decode(message.attachments)
        val useMms = attachments.isNotEmpty() || recipients.size > 1
        val outcome = if (useMms) {
            mmsSender.send(OutgoingMms(recipients, message.messageBody, attachments))
        } else {
            smsSender.send(message.recipientAddress, message.messageBody)
        }

        return when (outcome) {
            SendResult.Sent -> {
                val sentAt = timeSource.now()
                scheduledMessageDao.markSent(id, sentAt)
                if (!useMms) recordSentSms(message, sentAt)
                // MMS copies are written into the phone's store by the library; the importer picks them up.
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

    private suspend fun recordSentSms(message: ScheduledMessage, sentAt: Long) {
        val stored = systemStore.insertSentSms(message.recipientAddress, message.messageBody, sentAt)
        val resolvedThread = stored?.threadId
            ?: message.threadId
            ?: smsMessageDao.findThreadIdByAddress(message.recipientAddress)
            ?: smsMessageDao.nextThreadId()
        smsMessageDao.insert(
            SmsMessage(
                threadId = resolvedThread,
                address = message.recipientAddress,
                body = message.messageBody,
                timestamp = sentAt,
                status = SmsStatus.SENT,
                isIncoming = false,
                systemId = stored?.systemId
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
