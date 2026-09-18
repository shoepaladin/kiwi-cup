package com.kiwicup.scheduledmessenger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kiwicup.scheduledmessenger.data.local.dao.ReminderDao
import com.kiwicup.scheduledmessenger.data.local.dao.ScheduledMessageDao
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage

@Database(
    entities = [SmsMessage::class, ScheduledMessage::class, Reminder::class],
    version = 1,
    // Schema export is switched on once the first shipped version needs a migration.
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "scheduled_messenger.db"
    }
}
