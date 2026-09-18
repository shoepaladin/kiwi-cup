package com.kiwicup.scheduledmessenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kiwicup.scheduledmessenger.core.SmsStatus

/** A message that belongs to a conversation thread (received, or sent by us). */
@Entity(
    tableName = "sms_messages",
    indices = [Index(value = ["threadId", "timestamp"])]
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
    @ColumnInfo(defaultValue = "1") val isIncoming: Boolean = true
)
