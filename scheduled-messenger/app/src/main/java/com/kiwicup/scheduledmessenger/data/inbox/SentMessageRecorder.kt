package com.kiwicup.scheduledmessenger.data.inbox

import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.data.system.SystemMessageStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a text this app just sent: into the phone's store when we are the default app, and
 * always into the local inbox so the conversation shows it immediately. When we are not the
 * default the platform writes its own copy; the importer later reconciles it with ours.
 */
@Singleton
class SentMessageRecorder @Inject constructor(
    private val smsMessageDao: SmsMessageDao,
    private val systemStore: SystemMessageStore,
    private val threads: ThreadResolver
) {
    suspend fun record(address: String, body: String, preferredThreadId: Long?, sentAt: Long): Long {
        val stored = systemStore.insertSentSms(address, body, sentAt)
        val threadId = stored?.threadId ?: preferredThreadId ?: threads.findOrCreate(address)
        if (stored != null) threads.mergeLocalInto(address, stored.threadId)
        return smsMessageDao.insert(
            SmsMessage(
                threadId = threadId,
                address = address,
                body = body,
                timestamp = sentAt,
                status = SmsStatus.SENT,
                isIncoming = false,
                systemId = stored?.systemId
            )
        )
    }
}
