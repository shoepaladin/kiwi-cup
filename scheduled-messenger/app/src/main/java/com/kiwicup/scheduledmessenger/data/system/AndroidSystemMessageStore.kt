package com.kiwicup.scheduledmessenger.data.system

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSystemMessageStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemMessageStore {

    override fun isDefaultSmsApp(): Boolean = DefaultSmsApp.isDefault(context)

    override fun insertReceivedSms(address: String, body: String, timestamp: Long): StoredSms? =
        insert(address, body, timestamp, Telephony.Sms.MESSAGE_TYPE_INBOX, read = 0)

    override fun insertSentSms(address: String, body: String, timestamp: Long): StoredSms? =
        insert(address, body, timestamp, Telephony.Sms.MESSAGE_TYPE_SENT, read = 1)

    override fun markThreadRead(threadId: Long) {
        if (!isDefaultSmsApp()) return
        runCatching {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
            context.contentResolver.update(Telephony.Mms.CONTENT_URI, values, "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
        }
    }

    private fun insert(address: String, body: String, timestamp: Long, type: Int, read: Int): StoredSms? {
        if (!isDefaultSmsApp()) return null
        return runCatching {
            val threadId = Telephony.Threads.getOrCreateThreadId(context, address)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                // Stock apps store receipt time in DATE and the carrier's timestamp in DATE_SENT.
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, read)
                // SEEN = 1: this app posts its own notification, so the system need not flag it as new.
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, type)
                put(Telephony.Sms.THREAD_ID, threadId)
            }
            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) ?: return null
            StoredSms(systemId = uri.lastPathSegment!!.toLong(), threadId = threadId)
        }.getOrNull()
    }
}
