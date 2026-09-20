package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSuggestionsTest {

    private val contacts = listOf(
        Contact(lookupKey = "1", displayName = "John Smith", phoneNumber = "5551112222"),
        Contact(lookupKey = "2", displayName = "Jane Smith", phoneNumber = "5553334444"),
        Contact(lookupKey = "3", displayName = "Bob Jones", phoneNumber = "5555556666")
    )

    @Test
    fun `a blank query offers nothing, so the dropdown does not open on an empty field`() {
        assertEquals(emptyList<ContactSuggestion>(), ContactSuggestions.forQuery(contacts, "", emptySet(), "US"))
    }

    @Test
    fun `matching contacts come back as person suggestions`() {
        val results = ContactSuggestions.forQuery(contacts, "smith", emptySet(), "US")
        assertEquals(setOf("5551112222", "5553334444"), results.map { it.address }.toSet())
        assertTrue(results.all { it is PersonSuggestion })
    }

    @Test
    fun `a person suggestion carries the display name on its own, not folded into the label`() {
        // The label is free-text for the dropdown row; the name needs its own field so a caller
        // can use it for the chip without parsing formatting back out of a display string.
        val result = ContactSuggestions.forQuery(contacts, "john", emptySet(), "US").single() as PersonSuggestion
        assertEquals("John Smith", result.displayName)
    }

    @Test
    fun `an already-selected contact is not suggested again`() {
        val results = ContactSuggestions.forQuery(contacts, "smith", setOf("5551112222"), "US")
        assertEquals(listOf("5553334444"), results.map { it.address })
    }

    @Test
    fun `a name match that starts with the query sorts before one that only contains it`() {
        val withPrefixMatch = contacts + Contact("4", "Smithsonian Fan", "5559998888")
        val results = ContactSuggestions.forQuery(withPrefixMatch, "smith", emptySet(), "US")
        assertEquals("Smithsonian Fan", (results.first() as PersonSuggestion).displayName)
    }

    @Test
    fun `a real phone number leads the list ahead of any name matches`() {
        // Someone typing a number wants to know it will go through, not scroll past name hits.
        val phoneLikeContacts = contacts + Contact("5", "5551234567 Spam", "5559990000")
        val results = ContactSuggestions.forQuery(phoneLikeContacts, "5551234567", emptySet(), "US")
        assertTrue(results.first() is NewNumberSuggestion)
        assertEquals("5551234567", results.first().address)
    }

    @Test
    fun `matching by digits finds the contact without typing their name`() {
        // "1112222" is itself a plausible 7-digit NANP number, so it legitimately earns its own
        // new-number suggestion too — the assertion only needs the contact match to be present.
        val results = ContactSuggestions.forQuery(contacts, "1112222", emptySet(), "US")
        assertTrue(results.any { it is PersonSuggestion && it.address == "5551112222" })
    }

    @Test
    fun `a query matching a contact's exact number does not also duplicate it as a new number`() {
        // Both would carry the same address: a real duplicate-key hazard for a UI list keyed by
        // address, and confusing besides — the number already belongs to someone.
        val results = ContactSuggestions.forQuery(contacts, "5551112222", emptySet(), "US")
        assertEquals(listOf("5551112222"), results.map { it.address })
        assertTrue(results.single() is PersonSuggestion)
    }

    @Test
    fun `no match and no possible number returns an empty list`() {
        assertEquals(emptyList<ContactSuggestion>(), ContactSuggestions.forQuery(contacts, "xyz", emptySet(), "US"))
    }
}
