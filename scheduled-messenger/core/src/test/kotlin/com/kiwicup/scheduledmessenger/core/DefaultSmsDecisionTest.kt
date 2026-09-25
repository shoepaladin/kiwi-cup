package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSmsDecisionTest {

    private val us = "com.kiwicup.scheduledmessenger"

    @Test
    fun `the role decides even when the older call reports no default at all`() {
        // The device report: user made this the default, getDefaultSmsPackage said otherwise.
        val readings = DefaultSmsDecision.Readings(roleHeld = true, systemDefaultPackage = null, ourPackage = us)
        assertTrue(readings.isDefault)
        assertTrue(readings.disagree)
    }

    @Test
    fun `the role saying no wins over the older call saying yes`() {
        // A false "yes" makes the receiver drop SMS_RECEIVED and lose texts, so never guess yes.
        val readings = DefaultSmsDecision.Readings(roleHeld = false, systemDefaultPackage = us, ourPackage = us)
        assertFalse(readings.isDefault)
    }

    @Test
    fun `without a role manager the older call is used`() {
        assertTrue(DefaultSmsDecision.Readings(roleHeld = null, systemDefaultPackage = us, ourPackage = us).isDefault)
        assertFalse(DefaultSmsDecision.Readings(roleHeld = null, systemDefaultPackage = "com.other", ourPackage = us).isDefault)
    }

    @Test
    fun `agreement is not reported as a disagreement`() {
        assertFalse(DefaultSmsDecision.Readings(roleHeld = true, systemDefaultPackage = us, ourPackage = us).disagree)
        assertFalse(DefaultSmsDecision.Readings(roleHeld = null, systemDefaultPackage = null, ourPackage = us).disagree)
    }
}
