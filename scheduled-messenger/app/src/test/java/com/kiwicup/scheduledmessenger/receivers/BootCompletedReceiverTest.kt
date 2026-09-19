package com.kiwicup.scheduledmessenger.receivers

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.kiwicup.scheduledmessenger.core.MessageStatus
import com.kiwicup.scheduledmessenger.core.SchedulingPolicy
import com.kiwicup.scheduledmessenger.core.WorkNames
import com.kiwicup.scheduledmessenger.data.local.DatabaseTestRule
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.data.repository.ReminderRepository
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.notifications.ReminderNotifier
import com.kiwicup.scheduledmessenger.testing.FakeSmsSender
import com.kiwicup.scheduledmessenger.testing.FixedTimeSource
import com.kiwicup.scheduledmessenger.testing.TestWorkerFactory
import com.kiwicup.scheduledmessenger.work.ExactAlarms
import com.kiwicup.scheduledmessenger.work.RearmWorker
import com.kiwicup.scheduledmessenger.work.WorkScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Boot -> receiver -> RearmWorker -> repositories -> WorkManager, end to end in test mode. */
@RunWith(AndroidJUnit4::class)
class BootCompletedReceiverTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val hour = 3_600_000L
    private val clock = FixedTimeSource(1_700_000_000_000L)
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val policy = SchedulingPolicy()
        val notifier = ReminderNotifier(context)
        // The scheduler needs WorkManager, which needs the factory, which needs the repositories:
        // resolve the cycle by looking WorkManager up lazily through a holder.
        lateinit var scheduler: WorkScheduler
        val messagesRepo = ScheduledMessageRepository(
            dbRule.db.scheduledMessageDao(), LazyScheduler { scheduler }, policy, clock
        )
        val remindersRepo = ReminderRepository(dbRule.db.reminderDao(), LazyScheduler { scheduler }, notifier, clock)
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(TestWorkerFactory(dbRule.db, FakeSmsSender(), clock, notifier, messagesRepo, remindersRepo))
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkScheduler(workManager, policy, clock, ExactAlarms(context))
    }

    private suspend fun awaitRearmFinished() {
        withTimeout(10_000) {
            while (workManager.getWorkInfosForUniqueWork(RearmWorker.UNIQUE_NAME).get().firstOrNull()?.state != WorkInfo.State.SUCCEEDED) {
                delay(20)
            }
        }
    }

    @Test
    fun bootReenqueuesPendingMessagesAndActiveReminders() = runBlocking {
        val dao = dbRule.db.scheduledMessageDao()
        val pending = dao.insert(ScheduledMessage(recipientAddress = "+15550001111", messageBody = "a", targetTimestamp = clock.now() + hour, createdAt = clock.now()))
        val sent = dao.insert(ScheduledMessage(recipientAddress = "+15550002222", messageBody = "b", targetTimestamp = clock.now() - hour, status = MessageStatus.SENT, createdAt = clock.now()))
        val reminder = dbRule.db.reminderDao().insert(Reminder(threadId = 1, reminderText = "r", triggerTimestamp = clock.now() + hour))

        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        awaitRearmFinished()

        val info = workManager.getWorkInfosForUniqueWork(RearmWorker.UNIQUE_NAME).get().first()
        assertEquals(1, info.outputData.getInt(RearmWorker.KEY_MESSAGES, -1))
        assertEquals(1, info.outputData.getInt(RearmWorker.KEY_REMINDERS, -1))
        assertNotNull(dao.getById(pending)!!.workRequestId)
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfosForUniqueWork(WorkNames.scheduledSms(pending)).get().first().state)
        assertTrue(workManager.getWorkInfosForUniqueWork(WorkNames.scheduledSms(sent)).get().isEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfosForUniqueWork(WorkNames.reminder(reminder)).get().first().state)
    }

    @Test
    fun unrelatedBroadcastIsIgnored() {
        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BATTERY_LOW))
        assertTrue(workManager.getWorkInfosForUniqueWork(RearmWorker.UNIQUE_NAME).get().isEmpty())
    }
}

/** Defers WorkScheduler resolution until after WorkManager is initialised. */
private class LazyScheduler(private val provider: () -> WorkScheduler) : com.kiwicup.scheduledmessenger.work.SchedulerApi {
    override fun scheduleSms(messageId: Long, targetTimestamp: Long, replace: Boolean) = provider().scheduleSms(messageId, targetTimestamp, replace)
    override fun cancelSms(messageId: Long) = provider().cancelSms(messageId)
    override fun scheduleReminder(reminderId: Long, triggerTimestamp: Long, replace: Boolean) = provider().scheduleReminder(reminderId, triggerTimestamp, replace)
    override fun cancelReminder(reminderId: Long) = provider().cancelReminder(reminderId)
}
