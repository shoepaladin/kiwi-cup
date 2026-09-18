package com.kiwicup.scheduledmessenger.work

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.kiwicup.scheduledmessenger.core.MessageStatus
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.DatabaseTestRule
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import com.kiwicup.scheduledmessenger.data.sms.SendResult
import com.kiwicup.scheduledmessenger.notifications.ReminderNotifier
import com.kiwicup.scheduledmessenger.testing.FakeMmsSender
import com.kiwicup.scheduledmessenger.testing.FakeSmsSender
import com.kiwicup.scheduledmessenger.testing.FakeSystemMessageStore
import com.kiwicup.scheduledmessenger.testing.FixedTimeSource
import com.kiwicup.scheduledmessenger.testing.TestWorkerFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class ScheduledSmsWorkerTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sender = FakeSmsSender()
    private val mmsSender = FakeMmsSender()
    private val systemStore = FakeSystemMessageStore()
    private val clock = FixedTimeSource(1_700_000_000_000L)
    private lateinit var factory: TestWorkerFactory

    @Before
    fun setUp() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.SEND_SMS)
        factory = TestWorkerFactory(dbRule.db, sender, clock, ReminderNotifier(context), mmsSender = mmsSender, systemStore = systemStore)
    }

    private suspend fun insertPending(
        threadId: Long? = null,
        recipient: String = "+15550001111",
        attachments: String? = null
    ): Long = dbRule.db.scheduledMessageDao().insert(
        ScheduledMessage(
            recipientAddress = recipient,
            messageBody = "see you at 6",
            attachments = attachments,
            targetTimestamp = clock.now(),
            threadId = threadId,
            createdAt = clock.now()
        )
    )

    @Test
    fun groupMessageGoesOutAsMms() = runBlocking {
        val id = insertPending(recipient = "+15550001111,+15550002222")
        val result = buildWorker(id).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(sender.calls.isEmpty())
        assertEquals(1, mmsSender.calls.size)
        assertEquals(listOf("+15550001111", "+15550002222"), mmsSender.calls[0].recipients)
        assertEquals(MessageStatus.SENT, dbRule.db.scheduledMessageDao().getById(id)!!.status)
        // The library writes the MMS into the phone's store; no local SMS copy is fabricated.
        assertEquals(0, dbRule.db.smsMessageDao().countInThread(1L))
    }

    @Test
    fun pictureGoesOutAsMmsEvenToOnePerson() = runBlocking {
        val id = insertPending(attachments = "file:///data/x.jpg|image/jpeg")
        buildWorker(id).doWork()
        assertEquals(1, mmsSender.calls.size)
        assertEquals("image/jpeg", mmsSender.calls[0].attachments.single().mimeType)
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun mmsFailureWhenNotDefaultIsRecorded() = runBlocking {
        mmsSender.nextResult = SendResult.PermanentFailure("needs default")
        val id = insertPending(recipient = "+15550001111,+15550002222")
        val result = buildWorker(id).doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals("needs default", dbRule.db.scheduledMessageDao().getById(id)!!.failureReason)
    }

    @Test
    fun asDefaultAppSentSmsIsWrittenToSystemStore() = runBlocking {
        systemStore.isDefault = true
        val id = insertPending()
        buildWorker(id).doWork()
        assertEquals(1, systemStore.rows.size)
        assertTrue(systemStore.rows[0].sent)
        val copy = dbRule.db.smsMessageDao().getThread(500L).single()
        assertEquals(1000L, copy.systemId)
        assertEquals(MessageStatus.SENT, dbRule.db.scheduledMessageDao().getById(id)!!.status)
    }

    private fun buildWorker(id: Long, attempt: Int = 0): ScheduledSmsWorker =
        TestListenableWorkerBuilder<ScheduledSmsWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ScheduledSmsWorker.KEY_MESSAGE_ID to id))
            .setRunAttemptCount(attempt)
            .build()

    @Test
    fun successfulSendMarksSentAndRecordsThreadCopy() = runBlocking {
        val id = insertPending()
        val result = buildWorker(id).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(MessageStatus.SENT, dbRule.db.scheduledMessageDao().getById(id)!!.status)
        assertEquals(listOf(FakeSmsSender.Call("+15550001111", "see you at 6")), sender.calls)

        val copies = dbRule.db.smsMessageDao().getThread(1L)
        assertEquals(1, copies.size)
        assertEquals(SmsStatus.SENT, copies[0].status)
        assertFalse(copies[0].isIncoming)
        assertEquals("see you at 6", copies[0].body)
    }

    @Test
    fun sentCopyJoinsExistingThreadForSameAddress() = runBlocking {
        dbRule.db.smsMessageDao().insert(
            com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage(
                threadId = 42, address = "+15550001111", body = "hi", timestamp = 1, status = SmsStatus.RECEIVED
            )
        )
        val id = insertPending()
        buildWorker(id).doWork()
        assertEquals(2, dbRule.db.smsMessageDao().getThread(42L).size)
    }

    @Test
    fun permanentFailureMarksFailedWithReason() = runBlocking {
        sender.nextResult = SendResult.PermanentFailure("bad number")
        val id = insertPending()
        val result = buildWorker(id).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        val row = dbRule.db.scheduledMessageDao().getById(id)!!
        assertEquals(MessageStatus.FAILED, row.status)
        assertEquals("bad number", row.failureReason)
    }

    @Test
    fun transientFailureReleasesClaimAndRetries() = runBlocking {
        sender.nextResult = SendResult.TransientFailure("no service")
        val id = insertPending()
        val result = buildWorker(id, attempt = 0).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(MessageStatus.PENDING, dbRule.db.scheduledMessageDao().getById(id)!!.status)
    }

    @Test
    fun transientFailureOnLastAttemptGivesUp() = runBlocking {
        sender.nextResult = SendResult.TransientFailure("no service")
        val id = insertPending()
        val result = buildWorker(id, attempt = ScheduledSmsWorker.MAX_ATTEMPTS - 1).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        val row = dbRule.db.scheduledMessageDao().getById(id)!!
        assertEquals(MessageStatus.FAILED, row.status)
        assertTrue(row.failureReason!!.contains("gave up"))
    }

    @Test
    fun cancelledMessageIsNeverSent() = runBlocking {
        val id = insertPending()
        dbRule.db.scheduledMessageDao().cancel(id, clock.now())
        val result = buildWorker(id).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(sender.calls.isEmpty())
        assertEquals(MessageStatus.CANCELLED, dbRule.db.scheduledMessageDao().getById(id)!!.status)
    }

    @Test
    fun deletedMessageIsANoOp() = runBlocking {
        val result = buildWorker(9_999L).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(sender.calls.isEmpty())
    }

    @Test
    fun missingPermissionFailsWithoutTouchingRadio() = runBlocking {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(Manifest.permission.SEND_SMS)
        val id = insertPending()
        val result = buildWorker(id).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertTrue(sender.calls.isEmpty())
        val row = dbRule.db.scheduledMessageDao().getById(id)!!
        assertEquals(MessageStatus.FAILED, row.status)
        assertNotNull(row.failureReason)
        assertEquals(ScheduledSmsWorker.REASON_NO_PERMISSION, row.failureReason)
    }
}
