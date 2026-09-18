package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientValidatorTest {

    @Test
    fun `formatting characters are stripped`() {
        assertEquals("5551234567", RecipientValidator.normalize(" (555) 123-4567 "))
        assertEquals("+14155552671", RecipientValidator.normalize("+1 415.555.2671"))
    }

    @Test
    fun `international short code and local numbers are valid`() {
        assertTrue(RecipientValidator.isValid("+44 20 7946 0958"))
        assertTrue(RecipientValidator.isValid("12345"))
        assertTrue(RecipientValidator.isValid("555-123-4567"))
    }

    @Test
    fun `junk is rejected`() {
        assertFalse(RecipientValidator.isValid(""))
        assertFalse(RecipientValidator.isValid("12"))
        assertFalse(RecipientValidator.isValid("call me"))
        assertFalse(RecipientValidator.isValid("++123456"))
        assertFalse(RecipientValidator.isValid("1234567890123456"))
        assertNull(RecipientValidator.normalizeOrNull("abc"))
        assertEquals("123456", RecipientValidator.normalizeOrNull("123 456"))
    }
}
