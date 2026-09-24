package com.kiwicup.scheduledmessenger.data.inbox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.kiwicup.scheduledmessenger.core.Attachment
import com.kiwicup.scheduledmessenger.core.AttachmentCodec
import com.kiwicup.scheduledmessenger.core.Recipients
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies conversations from the phone's SMS and MMS stores into Room so the app has an inbox.
 *
 * Incremental: only rows with an `_id` greater than the highest one already imported are read
 * (separately for SMS and MMS), so repeated calls are cheap. The phone's `thread_id` becomes our
 * threadId, which keeps texts this app sends in the same conversation.
 */
@Singleton
class SmsInboxImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsMessageDao: SmsMessageDao,
    private val threads: ThreadResolver
) {
    data class Result(val imported: Int, val skipped: Boolean)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun importNew(): Result = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext Result(0, skipped = true)
        val rows = readSmsSince(smsMessageDao.maxSystemId() ?: 0L) + readMmsSince(smsMessageDao.maxMmsSystemId() ?: 0L)
        if (rows.isEmpty()) return@withContext Result(0, skipped = false)
        // Texts this app sent or received while NOT the default already exist locally without
        // system ids; adopt the phone's row instead of showing the message twice.
        val (reconciled, fresh) = rows.partition { row ->
            val local = threads.findUnsynced(row.address, row.body, row.timestamp) ?: return@partition false
            smsMessageDao.update(local.copy(systemId = row.systemId, mmsSystemId = row.mmsSystemId, threadId = row.threadId, timestamp = row.timestamp, status = row.status))
            true
        }
        val inserted = if (fresh.isEmpty()) 0 else smsMessageDao.insertIgnoring(fresh).count { it != -1L }
        // Conversations that existed only locally now have a real thread on the phone: fold them in.
        rows.distinctBy { it.threadId }.forEach { threads.mergeLocalInto(it.address, it.threadId) }
        Result(inserted + reconciled.size, skipped = false)
    }

    // ---- SMS ----
    private fun readSmsSince(minExclusiveId: Long): List<SmsMessage> {
        val projection = arrayOf(COL_ID, COL_THREAD, COL_ADDRESS, COL_BODY, COL_DATE, COL_TYPE, COL_READ)
        val cursor = runCatching {
            context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, "$COL_ID > ?", arrayOf(minExclusiveId.toString()), "$COL_ID ASC")
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<SmsMessage>()
        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(COL_ID)
            val iThread = c.getColumnIndexOrThrow(COL_THREAD)
            val iAddress = c.getColumnIndexOrThrow(COL_ADDRESS)
            val iBody = c.getColumnIndexOrThrow(COL_BODY)
            val iDate = c.getColumnIndexOrThrow(COL_DATE)
            val iType = c.getColumnIndexOrThrow(COL_TYPE)
            val iRead = c.getColumnIndex(COL_READ)
            while (c.moveToNext()) {
                val type = c.getInt(iType)
                val incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                val address = c.getString(iAddress) ?: continue
                out += SmsMessage(
                    threadId = c.getLong(iThread),
                    address = address,
                    body = c.getString(iBody) ?: "",
                    timestamp = c.getLong(iDate),
                    status = smsStatusFor(type),
                    isIncoming = incoming,
                    systemId = c.getLong(iId),
                    isRead = isReadRow(incoming, c, iRead)
                )
            }
        }
        return out
    }

    /**
     * Only incoming messages can be unread; the phone's store leaves READ at 0 on some sent rows,
     * and those must not light up the conversation. A provider that does not return the column at
     * all (some OEM stores, and this project's test fakes) is treated as read — importing a whole
     * history as unread is worse than missing an unread flag.
     */
    private fun isReadRow(incoming: Boolean, c: android.database.Cursor, readIndex: Int): Boolean =
        !incoming || readIndex < 0 || c.getInt(readIndex) != 0

    private fun smsStatusFor(type: Int): SmsStatus = when (type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> SmsStatus.RECEIVED
        Telephony.Sms.MESSAGE_TYPE_FAILED -> SmsStatus.FAILED
        else -> SmsStatus.SENT
    }

    // ---- MMS ----
    private fun readMmsSince(minExclusiveId: Long): List<SmsMessage> {
        val projection = arrayOf(COL_ID, COL_THREAD, MMS_DATE, MMS_BOX, MMS_READ)
        val cursor = runCatching {
            context.contentResolver.query(Telephony.Mms.CONTENT_URI, projection, "$COL_ID > ?", arrayOf(minExclusiveId.toString()), "$COL_ID ASC")
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<SmsMessage>()
        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(COL_ID)
            val iThread = c.getColumnIndexOrThrow(COL_THREAD)
            val iDate = c.getColumnIndexOrThrow(MMS_DATE)
            val iBox = c.getColumnIndexOrThrow(MMS_BOX)
            val iRead = c.getColumnIndex(MMS_READ)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val box = c.getInt(iBox)
                val incoming = box == Telephony.Mms.MESSAGE_BOX_INBOX
                val threadId = c.getLong(iThread)
                val (from, to) = readAddresses(id)
                val (text, attachments) = readParts(id)
                val address = (if (incoming) from else to.firstOrNull()) ?: from ?: to.firstOrNull() ?: continue
                // The phone's canonical recipient list for the thread excludes our own number;
                // the message's own address rows include it, so prefer the former for group replies.
                val others = readThreadRecipients(threadId)?.takeIf { it.size > 1 } ?: (listOfNotNull(from) + to).distinct()
                out += SmsMessage(
                    threadId = threadId,
                    address = address,
                    body = text,
                    timestamp = c.getLong(iDate) * 1000L, // MMS dates are stored in seconds
                    status = when (box) {
                        Telephony.Mms.MESSAGE_BOX_INBOX -> SmsStatus.RECEIVED
                        Telephony.Mms.MESSAGE_BOX_FAILED -> SmsStatus.FAILED
                        else -> SmsStatus.SENT
                    },
                    isIncoming = incoming,
                    isMms = true,
                    mmsSystemId = id,
                    attachments = AttachmentCodec.encode(attachments),
                    recipients = if (others.size > 1) Recipients.encode(others) else null,
                    isRead = isReadRow(incoming, c, iRead)
                )
            }
        }
        return out
    }

    /** Returns (sender, recipients) from the message's address table. */
    private fun readAddresses(mmsId: Long): Pair<String?, List<String>> {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val cursor = runCatching {
            context.contentResolver.query(uri, arrayOf(ADDR_ADDRESS, ADDR_TYPE), null, null, null)
        }.getOrNull() ?: return null to emptyList()
        var from: String? = null
        val to = ArrayList<String>()
        cursor.use { c ->
            val iAddr = c.getColumnIndexOrThrow(ADDR_ADDRESS)
            val iType = c.getColumnIndexOrThrow(ADDR_TYPE)
            while (c.moveToNext()) {
                val addr = c.getString(iAddr)?.takeIf { it.isNotBlank() && it != INSERT_ADDRESS_TOKEN } ?: continue
                when (c.getInt(iType)) {
                    ADDR_TYPE_FROM -> from = addr
                    ADDR_TYPE_TO, ADDR_TYPE_CC, ADDR_TYPE_BCC -> to += addr
                }
            }
        }
        return from to to
    }

    /** Canonical participants of a thread (excludes this phone's own number); null when unavailable. */
    private fun readThreadRecipients(threadId: Long): List<String>? {
        val conv = runCatching {
            context.contentResolver.query(CONVERSATIONS_URI, arrayOf("recipient_ids"), "_id = ?", arrayOf(threadId.toString()), null)
        }.getOrNull() ?: return null
        val ids = conv.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.split(' ')?.filter { it.isNotBlank() } ?: return null
        val addresses = ArrayList<String>()
        ids.forEach { id ->
            runCatching {
                context.contentResolver.query(CANONICAL_ADDRESSES_URI, arrayOf("address"), "_id = ?", arrayOf(id), null)
            }.getOrNull()?.use { c -> if (c.moveToFirst()) c.getString(0)?.let(addresses::add) }
        }
        return addresses.takeIf { it.isNotEmpty() }
    }

    /** Returns (text, media parts). */
    private fun readParts(mmsId: Long): Pair<String, List<Attachment>> {
        val cursor = runCatching {
            context.contentResolver.query(MMS_PART_URI, arrayOf(COL_ID, PART_CT, PART_TEXT), "$PART_MID = ?", arrayOf(mmsId.toString()), null)
        }.getOrNull() ?: return "" to emptyList()
        val text = StringBuilder()
        val media = ArrayList<Attachment>()
        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(COL_ID)
            val iCt = c.getColumnIndexOrThrow(PART_CT)
            val iText = c.getColumnIndexOrThrow(PART_TEXT)
            while (c.moveToNext()) {
                val ct = c.getString(iCt) ?: continue
                when {
                    ct == "text/plain" -> c.getString(iText)?.let { text.append(it) }
                    ct.startsWith("image/") || ct.startsWith("video/") || ct.startsWith("audio/") ->
                        media += Attachment("content://mms/part/${c.getLong(iId)}", ct)
                }
            }
        }
        return text.toString() to media
    }

    companion object {
        const val COL_ID = Telephony.Sms._ID
        const val COL_THREAD = Telephony.Sms.THREAD_ID
        const val COL_ADDRESS = Telephony.Sms.ADDRESS
        const val COL_BODY = Telephony.Sms.BODY
        const val COL_DATE = Telephony.Sms.DATE
        const val COL_TYPE = Telephony.Sms.TYPE
        const val MMS_DATE = Telephony.Mms.DATE
        const val MMS_BOX = Telephony.Mms.MESSAGE_BOX
        const val COL_READ = Telephony.Sms.READ
        const val MMS_READ = Telephony.Mms.READ
        const val ADDR_ADDRESS = "address"
        const val ADDR_TYPE = "type"
        const val ADDR_TYPE_FROM = 137
        const val ADDR_TYPE_TO = 151
        const val ADDR_TYPE_CC = 130
        const val ADDR_TYPE_BCC = 129
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"
        const val PART_CT = "ct"
        const val PART_TEXT = "text"
        const val PART_MID = "mid"
        val MMS_PART_URI: Uri = Uri.parse("content://mms/part")
        val CONVERSATIONS_URI: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val CANONICAL_ADDRESSES_URI: Uri = Uri.parse("content://mms-sms/canonical-addresses")
    }
}
