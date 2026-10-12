package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsBudgetTest {

    @Test
    fun `one picture gets the whole message less the overhead`() {
        assertEquals(MmsBudget.MESSAGE_BYTES - MmsBudget.OVERHEAD_BYTES, MmsBudget.perImageBytes(imageCount = 1, otherPartBytes = 0))
    }

    @Test
    fun `several pictures share one message budget`() {
        // The defect this replaces: a per-picture cap let three photos total about three times
        // the limit. Shared, they must still fit in one message together.
        val each = MmsBudget.perImageBytes(imageCount = 3, otherPartBytes = 0)
        assertTrue(3 * each + MmsBudget.OVERHEAD_BYTES <= MmsBudget.MESSAGE_BYTES)
    }

    @Test
    fun `text and other parts come out of the same budget`() {
        val withText = MmsBudget.perImageBytes(imageCount = 1, otherPartBytes = 50 * 1024)
        assertEquals(MmsBudget.MESSAGE_BYTES - MmsBudget.OVERHEAD_BYTES - 50 * 1024, withText)
    }

    @Test
    fun `a picture is never squeezed below the floor`() {
        assertEquals(MmsBudget.MIN_PER_IMAGE_BYTES, MmsBudget.perImageBytes(imageCount = 20, otherPartBytes = 0))
    }

    @Test
    fun `no pictures leaves the whole budget`() {
        assertEquals(MmsBudget.MESSAGE_BYTES, MmsBudget.perImageBytes(imageCount = 0, otherPartBytes = 0))
    }
}
