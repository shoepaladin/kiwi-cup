package com.kiwicup.scheduledmessenger.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.kiwicup.scheduledmessenger.core.MessageStatus
import com.kiwicup.scheduledmessenger.data.local.dao.ScheduledMessageDao
import com.kiwicup.scheduledmessenger.data.local.entity.ScheduledMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledMessageDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var dao: ScheduledMessageDao
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        dao = dbRule.db.scheduledMessageDao()
    }

    private fun message(target: Long = now + 60_000L, status: MessageStatus = MessageStatus.PENDING) =
        ScheduledMessage(
            recipientAddress = "+15551234567",
            messageBody = "hello",
            targetTimestamp = target,
            status = status,
            createdAt = now
        )

    @Test
    fun insertAndReadBack() = runTest {
        val id = dao.insert(message())
        val stored = dao.getById(id)
        assertNotNull(stored)
        assertEquals("+15551234567", stored!!.recipientAddress)
        assertEquals(MessageStatus.PENDING, stored.status)
        assertNull(stored.workRequestId)
    }

    @Test
    fun updateAndDelete() = runTest {
        val id = dao.insert(message())
        val stored = dao.getById(id)!!
        dao.update(stored.copy(messageBody = "edited"))
        assertEquals("edited", dao.getById(id)!!.messageBody)
        assertEquals(1, dao.deleteById(id))
        assertNull(dao.getById(id))
    }

    @Test
    fun pendingQueriesAreOrderedByTargetTime() = runTest {
        dao.insert(message(target = now + 3000))
        dao.insert(message(target = now + 1000))
        dao.insert(message(target = now + 2000, status = MessageStatus.SENT))
        val pending = dao.getPending()
        assertEquals(listOf(now + 1000, now + 3000), pending.map { it.targetTimestamp })
    }

    @Test
    fun dueReturnsOnlyPendingAtOrBeforeNow() = runTest {
        dao.insert(message(target = now - 1))
        dao.insert(message(target = now))
        dao.insert(message(target = now + 1))
        dao.insert(message(target = now - 5, status = MessageStatus.FAILED))
        assertEquals(2, dao.getDue(now).size)
    }

    @Test
    fun claimIsGrantedExactlyOnce() = runTest {
        val id = dao.insert(message())
        assertEquals(1, dao.claimForSending(id, now))
        assertEquals(0, dao.claimForSending(id, now))
        assertEquals(MessageStatus.SENDING, dao.getById(id)!!.status)
        // A user cancel that arrives after the claim must lose.
        assertEquals(0, dao.cancel(id, now))
        assertEquals(1, dao.markSent(id, now))
        assertEquals(MessageStatus.SENT, dao.getById(id)!!.status)
    }

    @Test
    fun cancelBeatsLateWorker() = runTest {
        val id = dao.insert(message())
        assertEquals(1, dao.cancel(id, now))
        assertEquals(0, dao.claimForSending(id, now))
        assertEquals(MessageStatus.CANCELLED, dao.getById(id)!!.status)
    }

    @Test
    fun failedRecordsReasonAndCanBeRescheduled() = runTest {
        val id = dao.insert(message())
        dao.claimForSending(id, now)
        assertEquals(1, dao.markFailed(id, "no signal", now))
        val failed = dao.getById(id)!!
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals("no signal", failed.failureReason)
        assertEquals(0, dao.markSent(id, now))

        assertEquals(1, dao.reschedule(id, now + 99, now))
        val again = dao.getById(id)!!
        assertEquals(MessageStatus.PENDING, again.status)
        assertEquals(now + 99, again.targetTimestamp)
        assertNull(again.failureReason)
    }

    @Test
    fun releaseClaimReturnsToPending() = runTest {
        val id = dao.insert(message())
        dao.claimForSending(id, now)
        assertEquals(1, dao.releaseClaim(id, now))
        assertEquals(MessageStatus.PENDING, dao.getById(id)!!.status)
        assertEquals(0, dao.releaseClaim(id, now))
    }

    @Test
    fun editOnlyWhilePending() = runTest {
        val id = dao.insert(message())
        assertEquals(1, dao.editPending(id, "+1999", "new body", now + 5, now))
        val edited = dao.getById(id)!!
        assertEquals("+1999", edited.recipientAddress)
        assertEquals("new body", edited.messageBody)
        dao.claimForSending(id, now)
        assertEquals(0, dao.editPending(id, "+1000", "x", now, now))
    }

    @Test
    fun workRequestIdIsPersisted() = runTest {
        val id = dao.insert(message())
        assertEquals(1, dao.setWorkRequestId(id, "uuid-1", now))
        assertEquals("uuid-1", dao.getById(id)!!.workRequestId)
    }

    @Test
    fun pendingFlowEmitsOnChanges() = runTest {
        dao.observePending().test {
            assertEquals(0, awaitItem().size)
            val id = dao.insert(message())
            assertEquals(1, awaitItem().size)
            dao.cancel(id, now)
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun historyAndCounts() = runTest {
        val a = dao.insert(message())
        val b = dao.insert(message())
        dao.insert(message())
        dao.cancel(a, now)
        dao.claimForSending(b, now)
        dao.markSent(b, now)
        assertEquals(1, dao.countByStatus(MessageStatus.PENDING))
        assertEquals(1, dao.countByStatus(MessageStatus.SENT))
        dao.observeHistory().test {
            assertEquals(setOf(a, b), awaitItem().map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, dao.clearHistory())
        dao.observeAll().test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
