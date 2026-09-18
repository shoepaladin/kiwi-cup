package com.kiwicup.scheduledmessenger.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.kiwicup.scheduledmessenger.data.local.dao.ReminderDao
import com.kiwicup.scheduledmessenger.data.local.entity.Reminder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDaoTest {

    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var dao: ReminderDao
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        dao = dbRule.db.reminderDao()
    }

    private fun reminder(threadId: Long = 1, trigger: Long = now + 60_000L, completed: Boolean = false) =
        Reminder(threadId = threadId, reminderText = "reply to Sam", triggerTimestamp = trigger, isCompleted = completed, createdAt = now)

    @Test
    fun insertReadUpdateDelete() = runTest {
        val id = dao.insert(reminder())
        val stored = dao.getById(id)!!
        assertEquals("reply to Sam", stored.reminderText)
        assertFalse(stored.isCompleted)
        dao.update(stored.copy(reminderText = "call Sam"))
        assertEquals("call Sam", dao.getById(id)!!.reminderText)
        assertEquals(1, dao.deleteById(id))
        assertNull(dao.getById(id))
    }

    @Test
    fun activeIsOrderedAndExcludesCompleted() = runTest {
        dao.insert(reminder(trigger = now + 30))
        dao.insert(reminder(trigger = now + 10))
        dao.insert(reminder(trigger = now + 20, completed = true))
        assertEquals(listOf(now + 10, now + 30), dao.getActive().map { it.triggerTimestamp })
        assertEquals(2, dao.countActive())
    }

    @Test
    fun markCompletedFiresOnlyOnce() = runTest {
        val id = dao.insert(reminder())
        assertEquals(1, dao.markCompleted(id))
        assertEquals(0, dao.markCompleted(id))
        assertTrue(dao.getById(id)!!.isCompleted)
    }

    @Test
    fun editReactivatesAndClearsWork() = runTest {
        val id = dao.insert(reminder())
        dao.setWorkRequestId(id, "w1")
        dao.markCompleted(id)
        assertEquals(1, dao.edit(id, "new text", now + 500))
        val edited = dao.getById(id)!!
        assertFalse(edited.isCompleted)
        assertNull(edited.workRequestId)
        assertEquals(now + 500, edited.triggerTimestamp)
    }

    @Test
    fun perThreadFlowTracksChanges() = runTest {
        dao.observeActiveForThread(7).test {
            assertEquals(0, awaitItem().size)
            dao.insert(reminder(threadId = 7))
            dao.insert(reminder(threadId = 8))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearCompletedRemovesOnlyDone() = runTest {
        dao.insert(reminder(completed = true))
        dao.insert(reminder())
        assertEquals(1, dao.clearCompleted())
        dao.observeAll().test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
