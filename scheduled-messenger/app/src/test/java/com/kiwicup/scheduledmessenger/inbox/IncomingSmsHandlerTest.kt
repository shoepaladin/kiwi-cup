package com.kiwicup.scheduledmessenger.inbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.inbox.IncomingSms
import com.kiwicup.scheduledmessenger.data.inbox.IncomingSmsHandler
import com.kiwicup.scheduledmessenger.data.local.DatabaseTestRule
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingSmsHandlerTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    @Test
    fun joinsExistingThreadByAddress() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insert(SmsMessage(threadId = 42, address = "+15550001111", body = "earlier", timestamp = 1, status = SmsStatus.RECEIVED, systemId = 1))
        val handler = IncomingSmsHandler(dao)

        val id = handler.handle(IncomingSms("+15550001111", "new text", 2_000))

        assertTrue(id > 0)
        assertEquals(2, dao.countInThread(42))
    }

    @Test
    fun createsNewThreadForUnknownAddressAndDedupes() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        val handler = IncomingSmsHandler(dao)

        val first = handler.handle(IncomingSms("+15550009999", "hello", 5_000))
        val duplicate = handler.handle(IncomingSms("+15550009999", "hello", 5_000))

        assertTrue(first > 0)
        assertEquals(-1L, duplicate)
        val stored = dao.getById(first)!!
        assertEquals(1L, stored.threadId)
        assertEquals(1, dao.countInThread(1))
    }
}
