package com.kiwicup.scheduledmessenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kiwicup.scheduledmessenger.core.SmsStatus

/** A message that belongs to a conversation thread (received, or sent by us). */
@Entity(
    tableName = "sms_messages",
    indices = [
        Index(value = ["threadId", "timestamp"]),
        Index(value = ["systemId"], unique = true),
        Index(value = ["mmsSystemId"], unique = true)
    ]
)
data class SmsMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    /** Phone number of the other party. */
    val address: String,
    val body: String,
    /** Epoch millis the message was sent or received. */
    val timestamp: Long,
    val status: SmsStatus,
    @ColumnInfo(defaultValue = "1") val isIncoming: Boolean = true,
    /** `_id` of the row in the phone's SMS store when imported from it; null for messages this app sent. */
    val systemId: Long? = null,
    /** True for multimedia / group messages. */
    @ColumnInfo(defaultValue = "0") val isMms: Boolean = false,
    /** `_id` in the phone's MMS store. */
    val mmsSystemId: Long? = null,
    /** Encoded with [com.kiwicup.scheduledmessenger.core.AttachmentCodec]. */
    val attachments: String? = null,
    /** Every other party in a group conversation, comma separated; null for one-to-one. */
    val recipients: String? = null
)
