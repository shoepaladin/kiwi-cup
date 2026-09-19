package com.kiwicup.scheduledmessenger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kiwicup.scheduledmessenger.data.local.dao.ConversationStyleDao
import com.kiwicup.scheduledmessenger.data.local.dao.ReminderDao
import com.kiwicup.scheduledmessenger.data.local.dao.ScheduledMessageDao
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.ConversationStyle
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage

@Database(
    entities = [SmsMessage::class, ScheduledMessage::class, Reminder::class, ConversationStyle::class],
    version = 1,
    // Version 1 is the first installed schema. Before the first schema change: turn on exportSchema,
    // commit schemas/, add a Migration and drop the destructive fallback in DatabaseModule.
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun reminderDao(): ReminderDao
    abstract fun conversationStyleDao(): ConversationStyleDao

    companion object {
        const val NAME = "scheduled_messenger.db"
    }
}
