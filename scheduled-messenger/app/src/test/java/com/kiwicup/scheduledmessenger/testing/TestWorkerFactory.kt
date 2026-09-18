package com.kiwicup.scheduledmessenger.testing

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.local.AppDatabase
import com.kiwicup.scheduledmessenger.data.sms.SmsSender
import com.kiwicup.scheduledmessenger.notifications.ReminderNotifier
import com.kiwicup.scheduledmessenger.work.ReminderWorker
import com.kiwicup.scheduledmessenger.work.ScheduledSmsWorker

/** Stands in for Hilt's worker factory in tests. */
class TestWorkerFactory(
    private val db: AppDatabase,
    private val smsSender: SmsSender,
    private val timeSource: TimeSource,
    private val notifier: ReminderNotifier
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            ScheduledSmsWorker::class.java.name -> ScheduledSmsWorker(
                appContext, workerParameters, db.scheduledMessageDao(), db.smsMessageDao(), smsSender, timeSource
            )
            ReminderWorker::class.java.name -> ReminderWorker(
                appContext, workerParameters, db.reminderDao(), db.smsMessageDao(), notifier
            )
            else -> null
        }
}
