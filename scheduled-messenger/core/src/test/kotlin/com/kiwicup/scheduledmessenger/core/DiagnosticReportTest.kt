package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {

    private val report = DiagnosticReport(
        listOf(
            DiagnosticSection("Device", listOf("sdk" to "37", "manufacturer" to "Pixel")),
            DiagnosticSection("Install source", listOf("installing package" to "null"))
        )
    )

    @Test
    fun eachSectionGetsAHeading() {
        val text = report.render()
        assertTrue(text.contains("== Device =="))
        assertTrue(text.contains("== Install source =="))
    }

    @Test
    fun valuesLineUpSoTheReportStaysReadableWhenPasted() {
        val lines = report.render().lines()
        val shortKey = lines.first { it.startsWith("sdk") }
        val longKey = lines.first { it.startsWith("manufacturer") }
        assertEquals(shortKey.indexOf("37"), longKey.indexOf("Pixel"))
    }

    @Test
    fun sectionsAreSeparatedByABlankLine() {
        assertTrue(report.render().contains("\n\n== Install source =="))
    }

    @Test
    fun aSectionWithNoReadingsStillShowsItsHeadingRatherThanVanishing() {
        // A section that collected nothing is itself a finding, so it must not be dropped.
        val empty = DiagnosticReport(listOf(DiagnosticSection("Default SMS role", emptyList())))
        assertEquals("== Default SMS role ==\n", empty.render())
    }

    @Test
    fun theReportEndsWithANewlineSoItConcatenatesCleanly() {
        assertTrue(report.render().endsWith("\n"))
    }
}
