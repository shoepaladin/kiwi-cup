package com.kiwicup.scheduledmessenger.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.kiwicup.scheduledmessenger.core.SmsStatus
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.SmsMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsMessageDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var dao: SmsMessageDao
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        dao = dbRule.db.smsMessageDao()
    }

    private fun sms(thread: Long, ts: Long, body: String = "hi", incoming: Boolean = true) =
        SmsMessage(
            threadId = thread,
            address = "+1555000$thread",
            body = body,
            timestamp = ts,
            status = if (incoming) SmsStatus.RECEIVED else SmsStatus.SENT,
            isIncoming = incoming
        )

    @Test
    fun insertReadDelete() = runTest {
        val id = dao.insert(sms(1, now))
        val stored = dao.getById(id)!!
        assertEquals(SmsStatus.RECEIVED, stored.status)
        dao.delete(stored)
        assertNull(dao.getById(id))
    }

    @Test
    fun threadIsChronological() = runTest {
        dao.insertAll(listOf(sms(1, now + 2, "second"), sms(1, now + 1, "first"), sms(2, now, "other")))
        assertEquals(listOf("first", "second"), dao.getThread(1).map { it.body })
        assertEquals(2, dao.countInThread(1))
    }

    @Test
    fun summariesShowLatestPerThreadNewestFirst() = runTest {
        dao.insertAll(
            listOf(
                sms(1, now + 1, "old"), sms(1, now + 5, "latest-1"),
                sms(2, now + 3, "latest-2"), sms(2, now + 2, "older-2")
            )
        )
        dao.observeThreadSummaries().test {
            val summaries = awaitItem()
            assertEquals(listOf(1L, 2L), summaries.map { it.threadId })
            assertEquals(listOf("latest-1", "latest-2"), summaries.map { it.body })
            assertEquals(listOf(2, 2), summaries.map { it.messageCount })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun threadFlowEmitsAndDeleteThreadClears() = runTest {
        dao.observeThread(3).test {
            assertEquals(0, awaitItem().size)
            dao.insert(sms(3, now))
            assertEquals(1, awaitItem().size)
            assertEquals(1, dao.deleteThread(3))
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
