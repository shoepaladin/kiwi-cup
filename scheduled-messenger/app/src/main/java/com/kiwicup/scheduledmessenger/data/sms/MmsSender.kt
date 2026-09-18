package com.kiwicup.scheduledmessenger.data.sms

import com.kiwicup.scheduledmessenger.core.Attachment

/** Everything needed to send a multimedia or group message. */
data class OutgoingMms(
    val recipients: List<String>,
    val body: String,
    val attachments: List<Attachment>
)

/** Seam over the MMS library so the worker can be tested with a fake. */
interface MmsSender {
    suspend fun send(message: OutgoingMms): SendResult
}
