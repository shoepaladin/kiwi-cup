package com.kiwicup.scheduledmessenger.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A self-reminder that fires a notification deep-linking into a conversation. */
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["isCompleted", "triggerTimestamp"]), Index(value = ["threadId"])]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    /** Optional id of the specific message the user long-pressed. */
    val messageId: Long? = null,
    val reminderText: String,
    /** Epoch millis at which the notification should be shown. */
    val triggerTimestamp: Long,
    val isCompleted: Boolean = false,
    val workRequestId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
