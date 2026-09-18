package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingPolicyTest {

    private val hour = 60L * 60L * 1000L
    private val now = 1_700_000_000_000L
    private val policy = SchedulingPolicy(maxLatenessMillis = 6 * hour)

    @Test
    fun `delay is target minus now and never negative`() {
        assertEquals(2 * hour, policy.initialDelayMillis(now, now + 2 * hour))
        assertEquals(0L, policy.initialDelayMillis(now, now))
        assertEquals(0L, policy.initialDelayMillis(now, now - hour))
    }

    @Test
    fun `future target dispatches later with exact delay`() {
        val action = policy.recoveryAction(now, now + 90_000L)
        assertEquals(RecoveryAction.DispatchLater(90_000L), action)
    }

    @Test
    fun `slightly late target dispatches immediately`() {
        assertEquals(RecoveryAction.DispatchNow, policy.recoveryAction(now, now))
        assertEquals(RecoveryAction.DispatchNow, policy.recoveryAction(now, now - 5 * hour))
        assertEquals(RecoveryAction.DispatchNow, policy.recoveryAction(now, now - 6 * hour))
    }

    @Test
    fun `target older than the lateness window expires`() {
        assertEquals(RecoveryAction.Expire, policy.recoveryAction(now, now - 6 * hour - 1))
        assertTrue(policy.isExpired(now, now - 24 * hour))
        assertFalse(policy.isExpired(now, now - hour))
    }

    @Test
    fun `zero lateness window means anything in the past expires`() {
        val strict = SchedulingPolicy(maxLatenessMillis = 0)
        assertEquals(RecoveryAction.DispatchNow, strict.recoveryAction(now, now))
        assertEquals(RecoveryAction.Expire, strict.recoveryAction(now, now - 1))
    }

    @Test
    fun `negative lateness window is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SchedulingPolicy(maxLatenessMillis = -1) }
    }

    @Test
    fun `user may schedule now or later but not in the past`() {
        assertTrue(policy.isValidTarget(now, now))
        assertTrue(policy.isValidTarget(now, now + hour))
        assertTrue(policy.isValidTarget(now, now - 30_000L))
        assertFalse(policy.isValidTarget(now, now - 2 * 60_000L))
    }
}
