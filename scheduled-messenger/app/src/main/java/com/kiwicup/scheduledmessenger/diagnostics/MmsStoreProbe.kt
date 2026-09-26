package com.kiwicup.scheduledmessenger.diagnostics

import android.content.Context
import android.provider.Telephony

/**
 * A one-line picture of the newest rows in the phone's MMS store, for the app log.
 *
 * Picture messages fail in three places that look identical from the outside ("nothing
 * happened"), and the store tells them apart:
 * - a sent picture that was never saved leaves no `sent`/`outbox` row, so it cannot appear in
 *   the conversation even though the carrier took it;
 * - an incoming picture the phone was told about but could not download leaves a
 *   `notification` row (m_type 130) with no matching `retrieved` row (m_type 132);
 * - an incoming picture that never reached this app leaves nothing at all.
 *
 * Only ids, box, type, thread and age are logged — never message content or phone numbers.
 */
object MmsStoreProbe {

    fun snapshot(context: Context, limit: Int = 5): String = runCatching {
        val projection = arrayOf(Telephony.Mms._ID, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_TYPE, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE)
        val nowSeconds = System.currentTimeMillis() / 1000
        context.contentResolver.query(Telephony.Mms.CONTENT_URI, projection, null, null, "${Telephony.Mms._ID} DESC")?.use { c ->
            if (c.count == 0) return@use "mms store: empty"
            val rows = mutableListOf<String>()
            while (rows.size < limit && c.moveToNext()) {
                val age = nowSeconds - c.getLong(4)
                rows += "id=${c.getLong(0)} box=${box(c.getInt(1))} type=${type(c.getInt(2))} thread=${c.getLong(3)} age=${age}s"
            }
            "mms store (${c.count} rows, newest first): " + rows.joinToString(" | ")
        } ?: "mms store: query returned nothing"
    }.getOrElse { "mms store: unreadable (${it.javaClass.simpleName}: ${it.message})" }

    private fun box(value: Int) = when (value) {
        Telephony.Mms.MESSAGE_BOX_INBOX -> "inbox"
        Telephony.Mms.MESSAGE_BOX_SENT -> "sent"
        Telephony.Mms.MESSAGE_BOX_DRAFTS -> "drafts"
        Telephony.Mms.MESSAGE_BOX_OUTBOX -> "outbox"
        Telephony.Mms.MESSAGE_BOX_FAILED -> "failed"
        else -> "box$value"
    }

    // PDU message types (OMA MMS encapsulation): the three that matter here.
    private fun type(value: Int) = when (value) {
        128 -> "send-request"
        130 -> "notification(not yet downloaded)"
        132 -> "retrieved"
        else -> "type$value"
    }
}
