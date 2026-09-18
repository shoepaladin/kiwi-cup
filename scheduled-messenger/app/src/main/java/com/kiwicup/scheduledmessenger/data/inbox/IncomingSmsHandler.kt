package com.kiwicup.scheduledmessenger.data.inbox

import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import javax.inject.Inject
import javax.inject.Singleton

/** One received text, already reassembled from its parts. */
data class IncomingSms(val address: String, val body: String, val timestamp: Long)

/**
 * Stores a freshly received text so the conversation updates instantly, before the next
 * inbox import would pick it up. The row has no systemId; the importer's later copy of the
 * same message is de-duplicated by address, body and timestamp.
 */
@Singleton
class IncomingSmsHandler @Inject constructor(
    private val smsMessageDao: SmsMessageDao
) {
    suspend fun handle(sms: IncomingSms): Long {
        if (smsMessageDao.existsUnsynced(sms.address, sms.body, sms.timestamp)) return -1L
        val threadId = smsMessageDao.findThreadIdByAddress(sms.address) ?: smsMessageDao.nextThreadId()
        return smsMessageDao.insert(
            SmsMessage(
                threadId = threadId,
                address = sms.address,
                body = sms.body,
                timestamp = sms.timestamp,
                status = SmsStatus.RECEIVED,
                isIncoming = true
            )
        )
    }
}
