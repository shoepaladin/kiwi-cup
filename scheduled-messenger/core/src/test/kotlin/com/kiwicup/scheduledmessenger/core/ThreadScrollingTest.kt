package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadScrollingTest {

    @Test
    fun `a reader at the newest message follows new arrivals`() {
        assertTrue(shouldScrollToLatest(firstVisibleIndex = 0, sendRequested = false))
    }

    @Test
    fun `one item short of the newest still counts as following`() {
        // A partially visible row would otherwise read as "scrolled away" and strand the user one
        // message behind for the rest of the conversation.
        assertTrue(shouldScrollToLatest(firstVisibleIndex = LATEST_MESSAGE_TOLERANCE, sendRequested = false))
    }

    @Test
    fun `a reader in the history is left where they are`() {
        // The defect this replaces: the screen scrolled on every new message, so a text arriving
        // mid-scroll threw the user back to the bottom.
        assertFalse(shouldScrollToLatest(firstVisibleIndex = LATEST_MESSAGE_TOLERANCE + 1, sendRequested = false))
        assertFalse(shouldScrollToLatest(firstVisibleIndex = 40, sendRequested = false))
    }

    @Test
    fun `sending always wins over scroll position`() {
        // Having just sent something, the user wants to watch it land, wherever they were reading.
        assertTrue(shouldScrollToLatest(firstVisibleIndex = 40, sendRequested = true))
    }
}
