package com.kiwicup.scheduledmessenger.testing

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.inbox.SentMessageRecorder
import com.kiwicup.scheduledmessenger.data.inbox.SmsInboxImporter
import com.kiwicup.scheduledmessenger.data.inbox.ThreadResolver
import com.kiwicup.scheduledmessenger.data.local.AppDatabase
import com.kiwicup.scheduledmessenger.data.sms.MmsSender
import com.kiwicup.scheduledmessenger.data.sms.SmsSender
import com.kiwicup.scheduledmessenger.data.system.SystemMessageStore
import com.kiwicup.scheduledmessenger.notifications.ReminderNotifier
import com.kiwicup.scheduledmessenger.data.repository.ReminderRepository
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.work.RearmWorker
import com.kiwicup.scheduledmessenger.work.ReminderWorker
import com.kiwicup.scheduledmessenger.work.ScheduledSmsWorker

/** Stands in for Hilt's worker factory in tests. */
class TestWorkerFactory(
    private val db: AppDatabase,
    private val smsSender: SmsSender,
    private val timeSource: TimeSource,
    private val notifier: ReminderNotifier,
    private val scheduledMessages: ScheduledMessageRepository? = null,
    private val reminders: ReminderRepository? = null,
    private val mmsSender: MmsSender = FakeMmsSender(),
    private val systemStore: SystemMessageStore = FakeSystemMessageStore()
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            ScheduledSmsWorker::class.java.name -> ScheduledSmsWorker(
                appContext, workerParameters, db.scheduledMessageDao(), db.smsMessageDao(), smsSender, mmsSender,
                SentMessageRecorder(db.smsMessageDao(), systemStore, ThreadResolver(db.smsMessageDao())),
                SmsInboxImporter(appContext, db.smsMessageDao(), ThreadResolver(db.smsMessageDao())), timeSource
            )
            ReminderWorker::class.java.name -> ReminderWorker(
                appContext, workerParameters, db.reminderDao(), db.smsMessageDao(), notifier
            )
            RearmWorker::class.java.name -> RearmWorker(
                appContext, workerParameters, checkNotNull(scheduledMessages), checkNotNull(reminders)
            )
            else -> null
        }
}
