package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSuggestionsTest {

    private val recognizer = LibPhoneNumberRecognizer()

    private val contacts = listOf(
        Contact(lookupKey = "1", displayName = "John Smith", phoneNumber = "5551112222"),
        Contact(lookupKey = "2", displayName = "Jane Smith", phoneNumber = "5553334444"),
        Contact(lookupKey = "3", displayName = "Bob Jones", phoneNumber = "5555556666")
    )

    @Test
    fun `a blank query offers nothing, so the dropdown does not open on an empty field`() {
        assertEquals(emptyList<ContactSuggestion>(), ContactSuggestions.forQuery(ContactIndex(contacts), recognizer, "", emptySet(), "US"))
    }

    @Test
    fun `matching contacts come back as person suggestions`() {
        val results = ContactSuggestions.forQuery(ContactIndex(contacts), recognizer, "smith", emptySet(), "US")
        assertEquals(setOf("5551112222", "5553334444"), results.map { it.address }.toSet())
        assertTrue(results.all { it is PersonSuggestion })
    }

    @Test
    fun `a person suggestion carries the display name on its own, not folded into the label`() {
        // The label is free-text for the dropdown row; the name needs its own field so a caller
        // can use it for the chip without parsing formatting back out of a display string.
        val result = ContactSuggestions.forQuery(ContactIndex(contacts), recognizer, "john", emptySet(), "US").single() as PersonSuggestion
        assertEquals("John Smith", result.displayName)
    }

    @Test
    fun `an already-selected contact is not suggested again`() {
        val results = ContactSuggestions.forQuery(ContactIndex(contacts), recognizer, "smith", setOf("5551112222"), "US")
        assertEquals(listOf("5553334444"), results.map { it.address })
    }

    @Test
    fun `a name match that starts with the query sorts before one that only contains it`() {
        val withPrefixMatch = contacts + Contact("4", "Smithsonian Fan", "5559998888")
        val results = ContactSuggestions.forQuery(ContactIndex(withPrefixMatch), recognizer, "smith", emptySet(), "US")
        assertEquals("Smithsonian Fan", (results.first() as PersonSuggestion).displayName)
    }

    @Test
    fun `a real phone number leads the list ahead of any name matches`() {
        // Someone typing a number wants to know it will go through, not scroll past name hits.
        val phoneLikeContacts = contacts + Contact("5", "5551234567 Spam", "5559990000")
        val results = ContactSuggestions.forQuery(ContactIndex(phoneLikeContacts), recognizer, "5551234567", emptySet(), "US")
        assertTrue(results.first() is NewNumberSuggestion)
        assertEquals("5551234567", results.first().address)
    }

    @Test
    fun `matching by digits finds the contact without typing their name`() {
        // "1112222" is itself a plausible 7-digit NANP number, so it legitimately earns its own
        // new-number suggestion too — the assertion only needs the contact match to be present.
        val results = ContactSuggestions.forQuery(ContactIndex(contacts), recognizer, "1112222", emptySet(), "US")
        assertTrue(results.any { it is PersonSuggestion && it.address == "5551112222" })
    }

    @Test
    fun `a query matching a contact's exact number does not also duplicate it as a new number`() {
        // Both would carry the same address: a real duplicate-key hazard for a UI list keyed by
        // address, and confusing besides — the number already belongs to someone.
        val results = ContactSuggestions.forQuery(ContactIndex(contacts), recognizer, "5551112222", emptySet(), "US")
        assertEquals(listOf("5551112222"), results.map { it.address })
        assertTrue(results.single() is PersonSuggestion)
    }

    @Test
    fun `no match and no possible number returns an empty list`() {
        assertEquals(emptyList<ContactSuggestion>(), ContactSuggestions.forQuery(ContactIndex(contacts), recognizer, "xyz", emptySet(), "US"))
    }

    @Test
    fun `a contact synced from two accounts is offered once, not twice with the same address`() {
        // The crash this reproduces: two rows for the one person — different lookup keys, and
        // the number typed two different ways — that normalize to the identical address. Real
        // devices produce exactly this when a contact exists in more than one account. A LazyColumn
        // keyed by address crashes outright on the duplicate; distinctBy on the raw fields let
        // both through because neither the lookup key nor the raw string was actually equal.
        val duplicated = listOf(
            Contact(lookupKey = "google-1", displayName = "Sam Lee", phoneNumber = "(908) 670-1435"),
            Contact(lookupKey = "whatsapp-1", displayName = "Sam Lee", phoneNumber = "908-670-1435")
        )
        val results = ContactSuggestions.forQuery(ContactIndex(duplicated), recognizer, "sam", emptySet(), "US")
        assertEquals(listOf("9086701435"), results.map { it.address })
    }
}

class EmptyRecipientFieldTest {

    private val contacts = listOf(
        Contact(lookupKey = "1", displayName = "John Smith", phoneNumber = "5551112222"),
        Contact(lookupKey = "2", displayName = "Zoe Adams", phoneNumber = "5553334444", starred = true),
        Contact(lookupKey = "3", displayName = "Bob Jones", phoneNumber = "5555556666")
    )
    private val index = ContactIndex(contacts)

    @Test
    fun `an untouched field offers recents first, then the address book`() {
        // Neither reference app leaves this empty: QKSMS lists recents, then starred, then
        // everyone, and Fossify puts a recent-contacts row above the full list.
        val recents = listOf(RecentConversation("5559998888", "Dana Lee"))
        val results = ContactSuggestions.forEmptyField(recents, index, emptySet())
        assertTrue(results.first() is RecentSuggestion)
        assertEquals("5559998888", results.first().address)
    }

    @Test
    fun `favourites come before everyone else`() {
        val results = ContactSuggestions.forEmptyField(emptyList(), index, emptySet())
        assertEquals("5553334444", results.first().address)
    }

    @Test
    fun `contacts below the favourites are alphabetical`() {
        val results = ContactSuggestions.forEmptyField(emptyList(), index, emptySet())
        assertEquals(listOf("5553334444", "5555556666", "5551112222"), results.map { it.address })
    }

    @Test
    fun `a recent conversation with a known number borrows the contact name`() {
        val recents = listOf(RecentConversation("5551112222", displayName = null))
        val row = ContactSuggestions.forEmptyField(recents, index, emptySet()).first() as RecentSuggestion
        assertEquals("John Smith", row.displayName)
    }

    @Test
    fun `a contact already shown as a recent is not repeated below`() {
        // Two rows with the same address would collide as a list key, the same crash the
        // new-number suggestion had to be de-duplicated to avoid.
        val recents = listOf(RecentConversation("5551112222", "John Smith"))
        val results = ContactSuggestions.forEmptyField(recents, index, emptySet())
        assertEquals(results.map { it.address }.size, results.map { it.address }.toSet().size)
    }

    @Test
    fun `an already-chosen recipient is offered neither as a recent nor as a contact`() {
        val recents = listOf(RecentConversation("5551112222", "John Smith"))
        val results = ContactSuggestions.forEmptyField(recents, index, setOf("5551112222"))
        assertTrue(results.none { it.address == "5551112222" })
    }

    @Test
    fun `a contact synced from two accounts appears once in the untouched field`() {
        // This is the exact crash a real device hit: an untouched field lists the whole address
        // book (not a narrowed search), so a contact stored under two lookup keys with slightly
        // different number formatting was far more likely to land twice in the same page and
        // collide as a LazyColumn key than it ever was while typing a query.
        val withDuplicate = ContactIndex(
            contacts + listOf(
                Contact(lookupKey = "google-1", displayName = "Sam Lee", phoneNumber = "(908) 670-1435"),
                Contact(lookupKey = "whatsapp-1", displayName = "Sam Lee", phoneNumber = "908-670-1435")
            )
        )
        val results = ContactSuggestions.forEmptyField(emptyList(), withDuplicate, emptySet())
        assertEquals(results.map { it.address }.size, results.map { it.address }.toSet().size)
        assertEquals(1, results.count { it.address == "9086701435" })
    }

    @Test
    fun `picking a recent keeps its name on the chip`() {
        val recent = RecentSuggestion("5559998888", "Dana Lee · 5559998888", "Dana Lee")
        val selection = RecipientSelections.add(RecipientSelection(), recent)
        assertEquals("Dana Lee", selection.recipientNames["5559998888"])
    }
}

class ContactIndexTest {

    @Test
    fun `folding happens once at build time, not per keystroke`() {
        val index = ContactIndex(listOf(Contact("1", "José García", "+1 555-111-2222")))
        val entry = index.entries.single()
        assertEquals("jose garcia", entry.searchKey)
        assertEquals("15551112222", entry.digits)
    }

    @Test
    fun `a number is matched to its contact however either side is formatted`() {
        val index = ContactIndex(listOf(Contact("1", "John Smith", "+1 555-111-2222")))
        assertEquals("John Smith", index.nameFor("+15551112222"))
    }

    @Test
    fun `an unknown number has no name`() {
        assertNull(ContactIndex(emptyList()).nameFor("5551112222"))
    }
}
