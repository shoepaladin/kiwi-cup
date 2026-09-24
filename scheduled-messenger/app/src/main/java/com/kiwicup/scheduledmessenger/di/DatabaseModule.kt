package com.kiwicup.scheduledmessenger.di

import android.content.Context
import androidx.room.Room
import com.kiwicup.scheduledmessenger.data.local.AppDatabase
import com.kiwicup.scheduledmessenger.data.local.Migrations
import com.kiwicup.scheduledmessenger.data.local.dao.ConversationStyleDao
import com.kiwicup.scheduledmessenger.data.local.dao.ReminderDao
import com.kiwicup.scheduledmessenger.data.local.dao.ScheduledMessageDao
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            // Only for downgrades (sideloading an older build over a newer one). An upgrade with a
            // missing migration now crashes on open instead of silently deleting every scheduled
            // message — loud and fixable beats quiet and unrecoverable.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideSmsMessageDao(db: AppDatabase): SmsMessageDao = db.smsMessageDao()

    @Provides
    fun provideScheduledMessageDao(db: AppDatabase): ScheduledMessageDao = db.scheduledMessageDao()

    @Provides
    fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideConversationStyleDao(db: AppDatabase): ConversationStyleDao = db.conversationStyleDao()
}
