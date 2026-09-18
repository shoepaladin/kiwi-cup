package com.kiwicup.scheduledmessenger.core

import com.kiwicup.scheduledmessenger.core.MessageStatus.CANCELLED
import com.kiwicup.scheduledmessenger.core.MessageStatus.FAILED
import com.kiwicup.scheduledmessenger.core.MessageStatus.PENDING
import com.kiwicup.scheduledmessenger.core.MessageStatus.SENDING
import com.kiwicup.scheduledmessenger.core.MessageStatus.SENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTransitionsTest {

    @Test
    fun `pending can be claimed cancelled or failed`() {
        assertTrue(StatusTransitions.canTransition(PENDING, SENDING))
        assertTrue(StatusTransitions.canTransition(PENDING, CANCELLED))
        assertTrue(StatusTransitions.canTransition(PENDING, FAILED))
        assertFalse(StatusTransitions.canTransition(PENDING, SENT))
    }

    @Test
    fun `sending resolves to sent failed or back to pending for retry`() {
        assertTrue(StatusTransitions.canTransition(SENDING, SENT))
        assertTrue(StatusTransitions.canTransition(SENDING, FAILED))
        assertTrue(StatusTransitions.canTransition(SENDING, PENDING))
        assertFalse(StatusTransitions.canTransition(SENDING, CANCELLED))
    }

    @Test
    fun `sent is final`() {
        MessageStatus.values().forEach { target ->
            assertFalse("SENT -> $target must be illegal", StatusTransitions.canTransition(SENT, target))
        }
        assertTrue(SENT.isTerminal)
    }

    @Test
    fun `failed and cancelled can be rescheduled`() {
        assertTrue(StatusTransitions.canTransition(FAILED, PENDING))
        assertTrue(StatusTransitions.canTransition(CANCELLED, PENDING))
        assertFalse(StatusTransitions.canTransition(FAILED, SENT))
    }

    @Test
    fun `require throws on illegal transition and returns target on legal one`() {
        assertEquals(SENDING, StatusTransitions.require(PENDING, SENDING))
        assertThrows(IllegalStateException::class.java) { StatusTransitions.require(SENT, PENDING) }
    }

    @Test
    fun `only pending needs a work request`() {
        assertEquals(listOf(PENDING), MessageStatus.values().filter { it.needsWork })
    }
}
