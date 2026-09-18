package com.kiwicup.scheduledmessenger.inbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.inbox.ThreadResolver
import com.kiwicup.scheduledmessenger.data.local.DatabaseTestRule
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadResolverTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private fun row(thread: Long, address: String, ts: Long = 1) =
        SmsMessage(threadId = thread, address = address, body = "x", timestamp = ts, status = SmsStatus.RECEIVED)

    @Test
    fun matchesFormattedNumbersAndCountryCodeVariants() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insert(row(7, "+1 (555) 000-1111"))
        val resolver = ThreadResolver(dao)

        assertEquals(7L, resolver.find("15550001111"))
        assertEquals(7L, resolver.find("+15550001111"))
        assertEquals(7L, resolver.find("5550001111"))
        assertNull(resolver.find("5550009999"))
    }

    @Test
    fun localThreadsAreNegativeAndDescend() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        val resolver = ThreadResolver(dao)
        val first = resolver.findOrCreate("+15550002222")
        dao.insert(row(first, "+15550002222"))
        val second = resolver.findOrCreate("+15550003333")
        assertEquals(-1L, first)
        assertEquals(-2L, second)
        assertEquals(first, resolver.findOrCreate("(555) 000-2222"))
    }

    @Test
    fun mergeMovesLocalRowsIntoSystemThread() = runBlocking {
        val dao = dbRule.db.smsMessageDao()
        dao.insert(row(-3, "5550004444", ts = 1))
        dao.insert(row(-3, "5550004444", ts = 2))
        dao.insert(row(12, "+15550004444", ts = 3))
        ThreadResolver(dao).mergeLocalInto("+1 555 000 4444", 12)
        assertEquals(0, dao.countInThread(-3))
        assertEquals(3, dao.countInThread(12))
    }
}
