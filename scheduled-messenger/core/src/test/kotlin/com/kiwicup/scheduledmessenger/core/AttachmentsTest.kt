package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentsTest {

    @Test
    fun `attachments round-trip and empty encodes to null`() {
        val list = listOf(Attachment("content://mms/part/12", "image/jpeg"), Attachment("file:///data/a.mp4", "video/mp4"))
        val text = AttachmentCodec.encode(list)
        assertEquals(list, AttachmentCodec.decode(text))
        assertNull(AttachmentCodec.encode(emptyList()))
        assertEquals(emptyList<Attachment>(), AttachmentCodec.decode(null))
        assertTrue(list[0].isImage)
        assertTrue(list[1].isVideo)
        assertFalse(list[1].isImage)
    }

    @Test
    fun `malformed rows are skipped`() {
        assertEquals(listOf(Attachment("u", "m")), AttachmentCodec.decode("u|m\nbroken\n|x\ny|"))
    }

    @Test
    fun `recipient lists parse normalise and detect groups`() {
        assertEquals(listOf("+15550001111", "5550002222"), Recipients.normalizeAll("+1 555 000 1111, (555) 000-2222"))
        assertNull(Recipients.normalizeAll("+15550001111, bob"))
        assertNull(Recipients.normalizeAll(""))
        assertEquals(listOf("1", "2"), Recipients.decode("1;2"))
        assertTrue(Recipients.isGroup("1,2"))
        assertFalse(Recipients.isGroup("1"))
        assertEquals(listOf("1"), Recipients.normalizeAll("1, 1"))
    }
}
