package com.kiwicup.scheduledmessenger.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.kiwicup.scheduledmessenger.data.local.DatabaseTestRule
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import com.kiwicup.scheduledmessenger.notifications.DeepLinks
import com.kiwicup.scheduledmessenger.notifications.ReminderNotifier
import com.kiwicup.scheduledmessenger.testing.FakeSmsSender
import com.kiwicup.scheduledmessenger.testing.FixedTimeSource
import com.kiwicup.scheduledmessenger.testing.TestWorkerFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var factory: TestWorkerFactory
    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        factory = TestWorkerFactory(dbRule.db, FakeSmsSender(), FixedTimeSource(0L), ReminderNotifier(context))
    }

    private suspend fun insertReminder(): Long = dbRule.db.reminderDao().insert(
        Reminder(threadId = 7, messageId = 3, reminderText = "Reply about dinner", triggerTimestamp = 0L)
    )

    private fun buildWorker(id: Long): ReminderWorker =
        TestListenableWorkerBuilder<ReminderWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to id))
            .build()

    @Test
    fun postsNotificationWithDeepLinkAndCompletesReminder() = runBlocking {
        val id = insertReminder()
        val result = buildWorker(id).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(dbRule.db.reminderDao().getById(id)!!.isCompleted)

        val shadowManager = Shadows.shadowOf(notificationManager)
        assertEquals(1, shadowManager.size())
        val notification = shadowManager.getNotification(ReminderNotifier.notificationId(id))
        val shadowNotification = Shadows.shadowOf(notification)
        assertEquals("Reply about dinner", shadowNotification.contentText.toString())

        val launched = Shadows.shadowOf(notification.contentIntent).savedIntent
        assertEquals(DeepLinks.ACTION_OPEN_THREAD, launched.action)
        assertEquals(7L, launched.getLongExtra(DeepLinks.EXTRA_THREAD_ID, -1))
        assertEquals(id, launched.getLongExtra(DeepLinks.EXTRA_REMINDER_ID, -1))
    }

    @Test
    fun secondRunDoesNotNotifyAgain() = runBlocking {
        val id = insertReminder()
        buildWorker(id).doWork()
        val result = buildWorker(id).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, Shadows.shadowOf(notificationManager).size())
    }

    @Test
    fun blockedNotificationsLeaveReminderActive() = runBlocking {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val id = insertReminder()
        val result = buildWorker(id).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertFalse(dbRule.db.reminderDao().getById(id)!!.isCompleted)
        assertEquals(0, Shadows.shadowOf(notificationManager).size())
    }

    @Test
    fun unknownReminderIsANoOp() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), buildWorker(404L).doWork())
        assertEquals(0, Shadows.shadowOf(notificationManager).size())
    }
}
