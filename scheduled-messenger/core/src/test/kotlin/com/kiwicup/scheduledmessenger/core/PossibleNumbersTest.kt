package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PossibleNumbersTest {

    private val recognizer = LibPhoneNumberRecognizer()

    @Test
    fun `a ten digit US number is possible`() {
        assertTrue(recognizer.isPossible("5551234567", "US"))
    }

    @Test
    fun `a couple of digits is not a number yet`() {
        assertFalse(recognizer.isPossible("12", "US"))
    }

    @Test
    fun `letters never parse as a number`() {
        assertFalse(recognizer.isPossible("notanumber", "US"))
    }

    @Test
    fun `formatting renders the number the way a person recognises it`() {
        assertEquals("(555) 123-4567", recognizer.format("5551234567", "US"))
    }

    @Test
    fun `an unparsable query falls back to itself rather than throwing`() {
        assertEquals("notanumber", recognizer.format("notanumber", "US"))
    }

    @Test
    fun `a real number offers a new-number suggestion`() {
        val suggestion = PossibleNumbers.suggestionFor(recognizer, "5551234567", "US", alreadySelected = emptySet())
        assertNotNull(suggestion)
        assertEquals("5551234567", suggestion!!.address)
        assertTrue(suggestion.label.contains("(555) 123-4567"))
    }

    @Test
    fun `too short a query offers no suggestion`() {
        assertNull(PossibleNumbers.suggestionFor(recognizer, "12", "US", alreadySelected = emptySet()))
    }

    @Test
    fun `a number already chosen is not offered again`() {
        assertNull(PossibleNumbers.suggestionFor(recognizer, "5551234567", "US", alreadySelected = setOf("5551234567")))
    }
}
