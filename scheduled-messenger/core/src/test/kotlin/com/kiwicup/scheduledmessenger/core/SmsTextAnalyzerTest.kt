package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTextAnalyzerTest {

    @Test
    fun `plain ascii is gsm and fits one segment up to 160`() {
        val info = SmsTextAnalyzer.analyze("a".repeat(160))
        assertEquals(SmsEncoding.GSM_7BIT, info.encoding)
        assertEquals(1, info.segments)
        assertEquals(0, info.remainingInSegment)
    }

    @Test
    fun `161 gsm characters need two segments of 153`() {
        val info = SmsTextAnalyzer.analyze("a".repeat(161))
        assertEquals(2, info.segments)
        assertEquals(306 - 161, info.remainingInSegment)
        assertEquals(3, SmsTextAnalyzer.analyze("a".repeat(307)).segments)
    }

    @Test
    fun `extension characters cost two septets`() {
        val info = SmsTextAnalyzer.analyze("€{}")
        assertEquals(SmsEncoding.GSM_7BIT, info.encoding)
        assertEquals(6, info.length)
    }

    @Test
    fun `emoji forces ucs2 with 70 char segments`() {
        val info = SmsTextAnalyzer.analyze("hi 😀")
        assertEquals(SmsEncoding.UCS2, info.encoding)
        assertEquals(1, info.segments)
        assertEquals(2, SmsTextAnalyzer.analyze("é".repeat(71) + "😀").segments)
    }

    @Test
    fun `empty body is one empty segment and not sendable`() {
        val info = SmsTextAnalyzer.analyze("")
        assertEquals(1, info.segments)
        assertEquals(160, info.remainingInSegment)
        assertFalse(SmsTextAnalyzer.isSendable("   "))
        assertTrue(SmsTextAnalyzer.isSendable("x"))
    }
}
