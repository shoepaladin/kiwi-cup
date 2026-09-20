package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PossibleNumbersTest {

    @Test
    fun `a ten digit US number is possible`() {
        assertTrue(PossibleNumbers.isPossible("5551234567", "US"))
    }

    @Test
    fun `a couple of digits is not a number yet`() {
        assertFalse(PossibleNumbers.isPossible("12", "US"))
    }

    @Test
    fun `letters never parse as a number`() {
        assertFalse(PossibleNumbers.isPossible("notanumber", "US"))
    }

    @Test
    fun `formatting renders the number the way a person recognises it`() {
        assertEquals("(555) 123-4567", PossibleNumbers.format("5551234567", "US"))
    }

    @Test
    fun `an unparsable query falls back to itself rather than throwing`() {
        assertEquals("notanumber", PossibleNumbers.format("notanumber", "US"))
    }

    @Test
    fun `a real number offers a new-number suggestion`() {
        val suggestion = PossibleNumbers.suggestionFor("5551234567", "US", alreadySelected = emptySet())
        assertNotNull(suggestion)
        assertEquals("5551234567", suggestion!!.address)
        assertTrue(suggestion.label.contains("(555) 123-4567"))
    }

    @Test
    fun `too short a query offers no suggestion`() {
        assertNull(PossibleNumbers.suggestionFor("12", "US", alreadySelected = emptySet()))
    }

    @Test
    fun `a number already chosen is not offered again`() {
        assertNull(PossibleNumbers.suggestionFor("5551234567", "US", alreadySelected = setOf("5551234567")))
    }
}
