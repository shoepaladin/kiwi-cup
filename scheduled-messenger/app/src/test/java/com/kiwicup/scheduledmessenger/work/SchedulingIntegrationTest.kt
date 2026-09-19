package com.kiwicup.scheduledmessenger.work

import android.Manifest
import android.app.Application
import android.content.Context
import android.util.Log
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
import com.kiwicup.scheduledmessenger.data.repository.ReminderRepository
import com.kiwicup.scheduledmessenger.data.repository.ScheduledMessageRepository
import com.kiwicup.scheduledmessenger.data.sms.SendResult
import com.kiwicup.scheduledmessenger.notifications.ReminderNotifier
import com.kiwicup.scheduledmessenger.testing.FakeSmsSender
import com.kiwicup.scheduledmessenger.testing.FixedTimeSource
import com.kiwicup.scheduledmessenger.testing.TestWorkerFactory
import com.kiwicup.scheduledmessenger.work.ExactAlarms
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

/**
 * End-to-end: repository -> WorkManager (test mode) -> worker -> database.
 * The test driver stands in for the passage of time by declaring initial delays met.
 */
@RunWith(AndroidJUnit4::class)
class SchedulingIntegrationTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val hour = 60L * 60L * 1000L
    private val clock = FixedTimeSource(1_700_000_000_000L)
    private val sender = FakeSmsSender()
    private val policy = SchedulingPolicy(maxLatenessMillis = 6 * hour)
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkScheduler
    private lateinit var messages: ScheduledMessageRepository
    private lateinit var reminders: ReminderRepository

    @Before
    fun setUp() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.POST_NOTIFICATIONS)
        val notifier = ReminderNotifier(context)
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(TestWorkerFactory(dbRule.db, sender, clock, notifier))
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkScheduler(workManager, policy, clock, ExactAlarms(context))
        messages = ScheduledMessageRepository(dbRule.db.scheduledMessageDao(), scheduler, policy, clock)
        reminders = ReminderRepository(dbRule.db.reminderDao(), scheduler, notifier, clock)
    }

    private fun workInfo(id: UUID): WorkInfo? = workManager.getWorkInfoById(id).get()

    private fun uniqueWorkInfo(name: String): WorkInfo? =
        workManager.getWorkInfosForUniqueWork(name).get().firstOrNull()

    private suspend fun awaitState(id: UUID, expected: WorkInfo.State) {
        withTimeout(10_000) {
            while (workInfo(id)?.state != expected) delay(20)
        }
    }

    @Test
    fun scheduleEnqueuesDelayedWorkAndSendsWhenDelayIsMet() = runBlocking {
        val target = clock.now() + 2 * hour
        val id = messages.schedule("+1 555 000 2222", "happy birthday!", target).getOrThrow()

        val row = dbRule.db.scheduledMessageDao().getById(id)!!
        assertEquals(MessageStatus.PENDING, row.status)
        assertNotNull(row.workRequestId)
        val workId = UUID.fromString(row.workRequestId)
        assertEquals(WorkInfo.State.ENQUEUED, workInfo(workId)!!.state)
        assertTrue(sender.calls.isEmpty())

        // Time passes.
        clock.current = target
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(workId)
        awaitState(workId, WorkInfo.State.SUCCEEDED)

        assertEquals(MessageStatus.SENT, dbRule.db.scheduledMessageDao().getById(id)!!.status)
        assertEquals(listOf(FakeSmsSender.Call("+15550002222", "happy birthday!")), sender.calls)
    }

    @Test
    fun cancelRemovesWorkAndMarksCancelled() = runBlocking {
        val id = messages.schedule("+15550003333", "ping", clock.now() + hour).getOrThrow()
        val workId = UUID.fromString(dbRule.db.scheduledMessageDao().getById(id)!!.workRequestId)

        assertTrue(messages.cancel(id))
        assertEquals(WorkInfo.State.CANCELLED, workInfo(workId)!!.state)
        assertEquals(MessageStatus.CANCELLED, dbRule.db.scheduledMessageDao().getById(id)!!.status)
        assertFalse(messages.cancel(id))
    }

    @Test
    fun validationRejectsBadInput() = runBlocking {
        assertTrue(messages.schedule("abc", "hi", clock.now() + hour).isFailure)
        assertTrue(messages.schedule("+15550003333", "   ", clock.now() + hour).isFailure)
        assertTrue(messages.schedule("+15550003333", "hi", clock.now() - hour).isFailure)
        assertEquals(0, dbRule.db.scheduledMessageDao().getPending().size)
    }

    @Test
    fun rebootReplayDispatchesSlightlyLateMessagesAndExpiresStaleOnes() = runBlocking {
        val dao = dbRule.db.scheduledMessageDao()
        val late = messages.schedule("+15550004444", "late but fine", clock.now() + hour).getOrThrow()
        val stale = messages.schedule("+15550005555", "too old", clock.now() + hour).getOrThrow()
        // Pretend the device was off: one message is 3h late, the other 10h late.
        dao.update(dao.getById(stale)!!.copy(targetTimestamp = clock.now() - 10 * hour))
        dao.update(dao.getById(late)!!.copy(targetTimestamp = clock.now() - 3 * hour))

        val rearmed = messages.reenqueueAllPending()

        assertEquals(1, rearmed)
        val staleRow = dao.getById(stale)!!
        assertEquals(MessageStatus.FAILED, staleRow.status)
        assertEquals(ScheduledMessageRepository.REASON_MISSED, staleRow.failureReason)

        val lateWorkId = UUID.fromString(dao.getById(late)!!.workRequestId)
        awaitState(lateWorkId, WorkInfo.State.SUCCEEDED)
        assertEquals(MessageStatus.SENT, dao.getById(late)!!.status)
        assertEquals(listOf("+15550004444"), sender.calls.map { it.address })
    }

    @Test
    fun rescheduleAfterFailureReplacesWorkUnderSameUniqueName() = runBlocking {
        sender.nextResult = SendResult.PermanentFailure("rejected")
        val id = messages.schedule("+15550006666", "retry me", clock.now() + hour).getOrThrow()
        val firstWork = UUID.fromString(dbRule.db.scheduledMessageDao().getById(id)!!.workRequestId)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(firstWork)
        awaitState(firstWork, WorkInfo.State.FAILED)
        assertEquals(MessageStatus.FAILED, dbRule.db.scheduledMessageDao().getById(id)!!.status)

        sender.nextResult = SendResult.Sent
        assertTrue(messages.reschedule(id, clock.now() + 2 * hour).isSuccess)
        val secondWork = UUID.fromString(dbRule.db.scheduledMessageDao().getById(id)!!.workRequestId)
        assertTrue(firstWork != secondWork)
        assertEquals(secondWork, uniqueWorkInfo(WorkNames.scheduledSms(id))!!.id)
        assertEquals(MessageStatus.PENDING, dbRule.db.scheduledMessageDao().getById(id)!!.status)
    }

    @Test
    fun editReplacesWorkAndKeepsSingleJob() = runBlocking {
        val id = messages.schedule("+15550007777", "v1", clock.now() + hour).getOrThrow()
        assertTrue(messages.edit(id, "+15550007777", "v2", clock.now() + 3 * hour).isSuccess)
        val row = dbRule.db.scheduledMessageDao().getById(id)!!
        assertEquals("v2", row.messageBody)
        assertEquals(1, workManager.getWorkInfosForUniqueWork(WorkNames.scheduledSms(id)).get().size)
        assertEquals(UUID.fromString(row.workRequestId), uniqueWorkInfo(WorkNames.scheduledSms(id))!!.id)
    }

    @Test
    fun reminderFiresWhenDelayIsMetAndCompleteCancelsWork() = runBlocking {
        val id = reminders.create(threadId = 5, messageId = 1, text = "call back", triggerTimestamp = clock.now() + hour).getOrThrow()
        val workId = UUID.fromString(dbRule.db.reminderDao().getById(id)!!.workRequestId)
        assertEquals(WorkInfo.State.ENQUEUED, workInfo(workId)!!.state)

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(workId)
        awaitState(workId, WorkInfo.State.SUCCEEDED)
        assertTrue(dbRule.db.reminderDao().getById(id)!!.isCompleted)

        val second = reminders.create(threadId = 5, messageId = null, text = "again", triggerTimestamp = clock.now() + hour).getOrThrow()
        val secondWork = UUID.fromString(dbRule.db.reminderDao().getById(second)!!.workRequestId)
        reminders.complete(second)
        assertEquals(WorkInfo.State.CANCELLED, workInfo(secondWork)!!.state)
        assertTrue(dbRule.db.reminderDao().getById(second)!!.isCompleted)
    }

    @Test
    fun reenqueueAllActiveRearmsEveryOpenReminder() = runBlocking {
        val a = reminders.create(1, null, "a", clock.now() + hour).getOrThrow()
        val b = reminders.create(2, null, "b", clock.now() + 2 * hour).getOrThrow()
        val done = reminders.create(3, null, "c", clock.now() + hour).getOrThrow()
        reminders.complete(done)
        val firstWorkA = UUID.fromString(dbRule.db.reminderDao().getById(a)!!.workRequestId)

        assertEquals(2, reminders.reenqueueAllActive())

        // Re-arming after a restart keeps the existing job (never cancels one that may be running).
        val secondWorkA = UUID.fromString(dbRule.db.reminderDao().getById(a)!!.workRequestId)
        assertEquals(firstWorkA, secondWorkA)
        assertEquals(WorkInfo.State.ENQUEUED, uniqueWorkInfo(WorkNames.reminder(a))!!.state)
        assertEquals(WorkInfo.State.ENQUEUED, uniqueWorkInfo(WorkNames.reminder(b))!!.state)
        assertNull(uniqueWorkInfo(WorkNames.reminder(done))?.takeIf { it.state == WorkInfo.State.ENQUEUED })
    }
}
