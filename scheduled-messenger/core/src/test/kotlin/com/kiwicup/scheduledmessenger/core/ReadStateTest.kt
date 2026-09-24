package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadStateTest {

    @Test
    fun `a text that arrives while the conversation is open is read`() {
        assertEquals(listOf(11L), ReadState.arrivalsToMarkRead(listOf(11L), openedAtMaxId = 10, markedUnreadThisVisit = emptySet()))
    }

    @Test
    fun `an older message the user marked unread stays unread`() {
        // Everything at or below the open watermark was already cleared on open, so an unread
        // one down there can only be the user's own "Mark unread".
        assertEquals(emptyList<Long>(), ReadState.arrivalsToMarkRead(listOf(5L), openedAtMaxId = 10, markedUnreadThisVisit = setOf(5L)))
    }

    @Test
    fun `a new arrival the user then marked unread stays unread`() {
        // The case the watermark alone would get wrong: it arrived during the visit, so without
        // the explicit set it would be re-read the next time the screen resumed.
        assertEquals(emptyList<Long>(), ReadState.arrivalsToMarkRead(listOf(12L), openedAtMaxId = 10, markedUnreadThisVisit = setOf(12L)))
    }

    @Test
    fun `before the open has finished, nothing is auto-read`() {
        // The view model starts its watermark at Long.MAX_VALUE until the open completes.
        assertEquals(emptyList<Long>(), ReadState.arrivalsToMarkRead(listOf(1L, 2L), openedAtMaxId = Long.MAX_VALUE, markedUnreadThisVisit = emptySet()))
    }
}
