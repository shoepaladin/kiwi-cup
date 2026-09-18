package com.kiwicup.scheduledmessenger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kiwicup.scheduledmessenger.core.MessageStatus

/** A text the user asked us to send at [targetTimestamp]. */
@Entity(
    tableName = "scheduled_messages",
    indices = [Index(value = ["status", "targetTimestamp"])]
)
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** One address, or several comma separated for a group message. */
    val recipientAddress: String,
    val messageBody: String,
    /** Encoded with [com.kiwicup.scheduledmessenger.core.AttachmentCodec]; non-null forces MMS. */
    val attachments: String? = null,
    /** Epoch millis at which the message should be dispatched. */
    val targetTimestamp: Long,
    val status: MessageStatus = MessageStatus.PENDING,
    /** UUID of the WorkManager request currently responsible for this row; null until enqueued. */
    val workRequestId: String? = null,
    /** Conversation thread to attach the sent copy to, when known. */
    val threadId: Long? = null,
    /** Human-readable reason recorded when status becomes FAILED. */
    val failureReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
