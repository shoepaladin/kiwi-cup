package com.kiwicup.scheduledmessenger.data.inbox

import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.data.system.SystemMessageStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One received text, already reassembled from its parts. */
data class IncomingSms(val address: String, val body: String, val timestamp: Long)

/**
 * Stores a freshly received text so the conversation updates instantly.
 *
 * As the default SMS app we are the only writer to the phone's store, so the row is written
 * there first and its ids are reused. As a non-default app the phone's store already has it;
 * we keep a local row and the next import de-duplicates by address, body and timestamp.
 */
@Singleton
class IncomingSmsHandler @Inject constructor(
    private val smsMessageDao: SmsMessageDao,
    private val systemStore: SystemMessageStore,
    private val threads: ThreadResolver
) {
    private val lock = Mutex()

    /** Returns the Room row id, or -1 when the message was already known. */
    suspend fun handle(sms: IncomingSms): Long = lock.withLock {
        if (threads.findUnsynced(sms.address, sms.body, sms.timestamp) != null) return@withLock -1L
        val stored = systemStore.insertReceivedSms(sms.address, sms.body, sms.timestamp)
        val threadId = stored?.threadId ?: threads.findOrCreate(sms.address)
        if (stored != null) threads.mergeLocalInto(sms.address, stored.threadId)
        return smsMessageDao.insert(
            SmsMessage(
                threadId = threadId,
                address = sms.address,
                body = sms.body,
                timestamp = sms.timestamp,
                status = SmsStatus.RECEIVED,
                isIncoming = true,
                systemId = stored?.systemId,
                // Always unread on arrival. If the conversation is open on screen, ThreadViewModel
                // marks it read as it lands; deciding that here would tie the data layer to what
                // the UI happens to be showing.
                isRead = false
            )
        )
    }
}
