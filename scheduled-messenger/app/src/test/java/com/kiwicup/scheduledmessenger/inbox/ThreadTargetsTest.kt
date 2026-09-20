package com.kiwicup.scheduledmessenger.inbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.inbox.SentMessageRecorder
import com.kiwicup.scheduledmessenger.data.inbox.ThreadResolver
import com.kiwicup.scheduledmessenger.data.inbox.ThreadTargets
import com.kiwicup.scheduledmessenger.data.local.DatabaseTestRule
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import com.kiwicup.scheduledmessenger.testing.FakeSystemMessageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadTargetsTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    @Test
    fun anExistingConversationIsReused() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insert(SmsMessage(threadId = 7, address = "+15550001111", body = "hi", timestamp = 1, status = SmsStatus.RECEIVED))
        val targets = ThreadTargets(FakeSystemMessageStore(isDefault = false), ThreadResolver(dao))

        assertEquals(7L, targets.forRecipients("+15550001111"))
    }

    @Test
    fun aNewConversationGetsALocalIdWhenWeAreNotTheDefaultApp() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        val targets = ThreadTargets(FakeSystemMessageStore(isDefault = false), ThreadResolver(dao))

        assertTrue(targets.forRecipients("+15550009999")!! < 0)
    }

    @Test
    fun theIdResolvedBeforeSendingIsTheIdTheMessageIsRecordedUnder() = runBlocking {
        // This is the whole point of resolving up front: the compose screen navigates to this id,
        // so if the recorder later picked a different one the user would land on an empty thread.
        val dao = dbRule.db.smsMessageDao()
        val store = FakeSystemMessageStore(isDefault = true)
        val resolver = ThreadResolver(dao)
        val targets = ThreadTargets(store, resolver)

        val predicted = targets.forRecipients("+15550001111")
        SentMessageRecorder(dao, store, resolver).record("+15550001111", "hello", predicted, sentAt = 10)

        assertEquals(predicted, dao.getThread(predicted!!).single().threadId)
    }

    @Test
    fun theSameHoldsWhenWeAreNotTheDefaultApp() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        val store = FakeSystemMessageStore(isDefault = false)
        val resolver = ThreadResolver(dao)
        val targets = ThreadTargets(store, resolver)

        val predicted = targets.forRecipients("+15550001111")
        SentMessageRecorder(dao, store, resolver).record("+15550001111", "hello", predicted, sentAt = 10)

        assertEquals(predicted, dao.getThread(predicted!!).single().threadId)
    }

    @Test
    fun aGroupMessageHasNoThreadYet() = runBlocking {
        // It goes out as MMS and is picked up by the importer, which is what allocates its
        // thread, so there is nothing to navigate to at send time.
        val targets = ThreadTargets(FakeSystemMessageStore(isDefault = true), ThreadResolver(dbRule.db.smsMessageDao()))

        assertNull(targets.forRecipients("+15550001111,+15550002222"))
    }
}
