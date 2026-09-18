package com.kiwicup.scheduledmessenger.data.inbox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies conversations from the phone's SMS store into Room so the app has an inbox.
 *
 * Incremental: only rows with an `_id` greater than the highest one already imported are read,
 * so repeated calls (every app start) are cheap. The phone's `thread_id` becomes our threadId,
 * which keeps texts this app sends in the same conversation via the address lookup in the worker.
 */
@Singleton
class SmsInboxImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsMessageDao: SmsMessageDao
) {
    data class Result(val imported: Int, val skipped: Boolean)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun importNew(): Result = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext Result(0, skipped = true)
        val since = smsMessageDao.maxSystemId() ?: 0L
        val rows = readSince(since)
        if (rows.isEmpty()) return@withContext Result(0, skipped = false)
        val inserted = smsMessageDao.insertIgnoring(rows).count { it != -1L }
        Result(inserted, skipped = false)
    }

    private fun readSince(minExclusiveId: Long): List<SmsMessage> {
        val projection = arrayOf(COL_ID, COL_THREAD, COL_ADDRESS, COL_BODY, COL_DATE, COL_TYPE)
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "$COL_ID > ?",
            arrayOf(minExclusiveId.toString()),
            "$COL_ID ASC"
        ) ?: return emptyList()
        val out = ArrayList<SmsMessage>()
        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(COL_ID)
            val iThread = c.getColumnIndexOrThrow(COL_THREAD)
            val iAddress = c.getColumnIndexOrThrow(COL_ADDRESS)
            val iBody = c.getColumnIndexOrThrow(COL_BODY)
            val iDate = c.getColumnIndexOrThrow(COL_DATE)
            val iType = c.getColumnIndexOrThrow(COL_TYPE)
            while (c.moveToNext()) {
                val type = c.getInt(iType)
                val address = c.getString(iAddress) ?: continue
                out += SmsMessage(
                    threadId = c.getLong(iThread),
                    address = address,
                    body = c.getString(iBody) ?: "",
                    timestamp = c.getLong(iDate),
                    status = statusFor(type),
                    isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    systemId = c.getLong(iId)
                )
            }
        }
        return out
    }

    private fun statusFor(type: Int): SmsStatus = when (type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> SmsStatus.RECEIVED
        Telephony.Sms.MESSAGE_TYPE_FAILED -> SmsStatus.FAILED
        else -> SmsStatus.SENT
    }

    companion object {
        const val COL_ID = Telephony.Sms._ID
        const val COL_THREAD = Telephony.Sms.THREAD_ID
        const val COL_ADDRESS = Telephony.Sms.ADDRESS
        const val COL_BODY = Telephony.Sms.BODY
        const val COL_DATE = Telephony.Sms.DATE
        const val COL_TYPE = Telephony.Sms.TYPE
    }
}
