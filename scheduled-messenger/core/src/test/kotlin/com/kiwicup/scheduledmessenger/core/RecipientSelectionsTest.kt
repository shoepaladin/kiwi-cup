package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RecipientSelectionsTest {

    private val john = PersonSuggestion("5551112222", "John Smith · 5551112222", "John Smith", "lookup-1")
    private val newNumber = NewNumberSuggestion("5559998888", "New number: (555) 999-8888")

    @Test
    fun `adding a person suggestion appends the address and records the name`() {
        val result = RecipientSelections.add(RecipientSelection(), john)
        assertEquals(listOf("5551112222"), result.addresses)
        assertEquals("John Smith", result.recipientNames["5551112222"])
    }

    @Test
    fun `adding a new-number suggestion appends the address with no name`() {
        val result = RecipientSelections.add(RecipientSelection(), newNumber)
        assertEquals(listOf("5559998888"), result.addresses)
        assertEquals(emptyMap<String, String>(), result.recipientNames)
    }

    @Test
    fun `adding an address already selected changes nothing`() {
        val selection = RecipientSelections.add(RecipientSelection(), john)
        val result = RecipientSelections.add(selection, john)
        assertSame(selection, result)
    }

    @Test
    fun `a second recipient is appended, not replacing the first`() {
        val first = RecipientSelections.add(RecipientSelection(), john)
        val both = RecipientSelections.add(first, newNumber)
        assertEquals(listOf("5551112222", "5559998888"), both.addresses)
    }

    @Test
    fun `removing an address drops both the address and its recorded name`() {
        val withBoth = RecipientSelections.add(RecipientSelections.add(RecipientSelection(), john), newNumber)
        val result = RecipientSelections.remove(withBoth, "5551112222")
        assertEquals(listOf("5559998888"), result.addresses)
        assertEquals(emptyMap<String, String>(), result.recipientNames)
    }

    @Test
    fun `removing an address not present changes nothing`() {
        val selection = RecipientSelections.add(RecipientSelection(), john)
        val result = RecipientSelections.remove(selection, "0000000000")
        assertEquals(selection, result)
    }

    @Test
    fun `a blank query contributes nothing to the effective recipient`() {
        val selection = RecipientSelections.add(RecipientSelection(), john)
        assertEquals("5551112222", RecipientSelections.effectiveRecipient(selection, ""))
    }

    @Test
    fun `an unconfirmed query is folded in at submit time`() {
        // The regression this exists for: typing a number and hitting send immediately, without
        // ever tapping its suggestion row, used to send nothing at all.
        val result = RecipientSelections.effectiveRecipient(RecipientSelection(), "5559998888")
        assertEquals("5559998888", result)
    }

    @Test
    fun `an unconfirmed query joins confirmed chips rather than replacing them`() {
        val selection = RecipientSelections.add(RecipientSelection(), john)
        val result = RecipientSelections.effectiveRecipient(selection, "5559998888")
        assertEquals("5551112222,5559998888", result)
    }
}
