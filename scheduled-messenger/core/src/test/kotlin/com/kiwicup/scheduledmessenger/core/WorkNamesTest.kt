package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkNamesTest {

    @Test
    fun `names round-trip through ids`() {
        assertEquals("scheduled-sms-42", WorkNames.scheduledSms(42))
        assertEquals(42L, WorkNames.messageIdFrom("scheduled-sms-42"))
        assertEquals("reminder-7", WorkNames.reminder(7))
        assertEquals(7L, WorkNames.reminderIdFrom("reminder-7"))
    }

    @Test
    fun `foreign names do not parse`() {
        assertNull(WorkNames.messageIdFrom("reminder-7"))
        assertNull(WorkNames.reminderIdFrom("scheduled-sms-1"))
        assertNull(WorkNames.messageIdFrom("scheduled-sms-abc"))
    }
}
