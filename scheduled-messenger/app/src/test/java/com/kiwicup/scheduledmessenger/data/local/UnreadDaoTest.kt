package com.kiwicup.scheduledmessenger.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnreadDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private fun text(id: Long, thread: Long, ts: Long, read: Boolean, incoming: Boolean = true) = SmsMessage(
        id = id, threadId = thread, address = "+1555000$thread", body = "m$id", timestamp = ts,
        status = if (incoming) SmsStatus.RECEIVED else SmsStatus.SENT, isIncoming = incoming, isRead = read
    )

    @Test
    fun conversationListCountsUnreadPerConversation() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insertAll(listOf(text(1, 7, 100, read = false), text(2, 7, 200, read = false), text(3, 8, 300, read = true)))

        val summaries = dao.observeThreadSummaries().first().associateBy { it.threadId }
        assertEquals(2, summaries.getValue(7).unreadCount)
        assertTrue(summaries.getValue(7).isUnread)
        assertFalse(summaries.getValue(8).isUnread)
    }

    @Test
    fun openingAConversationReadsAllOfItAndOnlyIt() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insertAll(listOf(text(1, 7, 100, read = false), text(2, 8, 200, read = false)))

        dao.markThreadRead(7)

        assertNull(dao.firstUnreadId(7))
        assertEquals(listOf(2L), dao.unreadIds(8))
    }

    @Test
    fun markingOneMessageUnreadChangesOnlyThatMessage() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insertAll(listOf(text(1, 7, 100, read = true), text(2, 7, 200, read = true), text(3, 7, 300, read = true)))

        dao.setRead(2, false)

        assertEquals(listOf(2L), dao.unreadIds(7))
        assertEquals(1, dao.observeThreadSummaries().first().single().unreadCount)
    }

    @Test
    fun theNewLineGoesAtTheOldestUnreadMessage() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insertAll(listOf(text(1, 7, 100, read = true), text(2, 7, 200, read = false), text(3, 7, 300, read = false)))

        assertEquals(2L, dao.firstUnreadId(7))
    }

    @Test
    fun markReadClearsExactlyTheGivenMessages() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insertAll(listOf(text(1, 7, 100, read = false), text(2, 7, 200, read = false)))

        dao.markRead(listOf(2L))

        assertEquals(listOf(1L), dao.unreadIds(7))
    }
}
