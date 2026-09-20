package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatchingTest {

    @Test
    fun `a substring anywhere in the name matches, not only a prefix`() {
        assertTrue(ContactMatching.matchesContactName("John Smith", "smith"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertTrue(ContactMatching.matchesContactName("John Smith", "SMITH"))
    }

    @Test
    fun `accents fold so josé matches jose`() {
        assertTrue(ContactMatching.matchesContactName("José García", "jose"))
        assertTrue(ContactMatching.matchesContactName("Jose Garcia", "josé"))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertFalse(ContactMatching.matchesContactName("Anyone", ""))
    }

    @Test
    fun `an unrelated name does not match`() {
        assertFalse(ContactMatching.matchesContactName("John Smith", "xyz"))
    }

    @Test
    fun `phone matching compares digits only, ignoring formatting on both sides`() {
        assertTrue(ContactMatching.matchesPhoneNumber("(555) 123-4567", "5551234567"))
        assertTrue(ContactMatching.matchesPhoneNumber("5551234567", "555-123-4567"))
    }

    @Test
    fun `phone matching is substring, so the last few digits find the contact`() {
        assertTrue(ContactMatching.matchesPhoneNumber("+15551234567", "4567"))
    }

    @Test
    fun `a query with no digits never matches a phone number`() {
        // Otherwise an empty digit string would be a substring of everything.
        assertFalse(ContactMatching.matchesPhoneNumber("5551234567", "abc"))
    }

    @Test
    fun `a non-matching digit run does not match`() {
        assertFalse(ContactMatching.matchesPhoneNumber("5551234567", "9999"))
    }
}
