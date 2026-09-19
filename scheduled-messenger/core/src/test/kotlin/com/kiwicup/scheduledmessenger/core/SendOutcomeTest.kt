package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendOutcomeTest {

    @Test
    fun `a message sent now is not part of the schedule`() {
        // The reported bug: texting a new contact listed the text as a scheduled message. Send-now
        // reads the clock before the row is built, so its target lands at or before createdAt.
        assertFalse(wasScheduledForLater(targetTimestamp = 1_000, createdAt = 1_000))
        assertFalse(wasScheduledForLater(targetTimestamp = 1_000, createdAt = 1_005))
    }

    @Test
    fun `a message queued for later is part of the schedule`() {
        assertTrue(wasScheduledForLater(targetTimestamp = 1_000 + 60_000, createdAt = 1_000))
    }

    @Test
    fun `one millisecond ahead still counts as scheduled`() {
        // The boundary belongs to the schedule: anything genuinely in the future was asked for,
        // however close, and hiding it would lose a message the user expects to see queued.
        assertTrue(wasScheduledForLater(targetTimestamp = 1_001, createdAt = 1_000))
    }

    @Test
    fun `a text sent immediately stays out of the schedule`() {
        assertFalse(belongsInSchedule(targetTimestamp = 1_000, createdAt = 1_000, failed = false))
    }

    @Test
    fun `a failed immediate send is still listed`() {
        // Otherwise it vanishes entirely: the conversation has no failed state yet, so the
        // schedule is the only place a failure can be seen.
        assertTrue(belongsInSchedule(targetTimestamp = 1_000, createdAt = 1_000, failed = true))
    }

    @Test
    fun `a message queued for later is listed either way`() {
        assertTrue(belongsInSchedule(targetTimestamp = 61_000, createdAt = 1_000, failed = false))
        assertTrue(belongsInSchedule(targetTimestamp = 61_000, createdAt = 1_000, failed = true))
    }

    @Test
    fun `the two outcomes are distinct`() {
        // They route differently — one to the conversation, one to the schedule — so collapsing
        // them is what produced the bug in the first place.
        assertTrue(SendOutcome.SENT_NOW != SendOutcome.SCHEDULED)
    }
}
