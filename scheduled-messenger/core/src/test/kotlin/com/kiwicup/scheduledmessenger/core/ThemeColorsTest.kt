package com.kiwicup.scheduledmessenger.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {

    @Test
    fun `channels round-trip`() {
        val c = ThemeColors.argb(255, 0x12, 0x34, 0x56)
        assertEquals(0xFF123456.toInt(), c)
        assertEquals(0x12, ThemeColors.red(c))
        assertEquals(0x34, ThemeColors.green(c))
        assertEquals(0x56, ThemeColors.blue(c))
    }

    @Test
    fun `luminance and contrast follow WCAG`() {
        assertEquals(1.0, ThemeColors.luminance(ThemeColors.WHITE), 1e-6)
        assertEquals(0.0, ThemeColors.luminance(ThemeColors.BLACK), 1e-6)
        assertEquals(21.0, ThemeColors.contrast(ThemeColors.WHITE, ThemeColors.BLACK), 1e-6)
        assertEquals(1.0, ThemeColors.contrast(ThemeColors.WHITE, ThemeColors.WHITE), 1e-6)
    }

    @Test
    fun `readable foreground picks the higher contrast`() {
        assertEquals(ThemeColors.WHITE, ThemeColors.readableOn(0xFF1E5F74.toInt()))
        assertEquals(ThemeColors.BLACK, ThemeColors.readableOn(0xFFF9A825.toInt()))
    }

    @Test
    fun `blend is linear and clamps`() {
        val mid = ThemeColors.blend(ThemeColors.BLACK, ThemeColors.WHITE, 0.5)
        assertEquals(128, ThemeColors.red(mid))
        assertEquals(ThemeColors.WHITE, ThemeColors.blend(ThemeColors.BLACK, ThemeColors.WHITE, 5.0))
        assertEquals(ThemeColors.BLACK, ThemeColors.blend(ThemeColors.BLACK, ThemeColors.WHITE, -1.0))
    }

    @Test
    fun `every preset yields readable palettes in both modes`() {
        ThemeColors.presets.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val p = ThemeColors.derive(preset.argb, dark)
                assertTrue("${preset.name} dark=$dark primary", ThemeColors.contrast(p.primary, p.onPrimary) >= 4.5)
                assertTrue("${preset.name} dark=$dark container", ThemeColors.contrast(p.primaryContainer, p.onPrimaryContainer) >= 4.5)
                assertTrue("${preset.name} dark=$dark secondary", ThemeColors.contrast(p.secondaryContainer, p.onSecondaryContainer) >= 4.5)
            }
        }
    }

    @Test
    fun `dark containers are darker than light containers`() {
        val seed = 0xFF3F51B5.toInt()
        val light = ThemeColors.derive(seed, dark = false)
        val dark = ThemeColors.derive(seed, dark = true)
        assertTrue(ThemeColors.luminance(dark.primaryContainer) < ThemeColors.luminance(light.primaryContainer))
    }

    @Test
    fun `hex parsing accepts rgb and argb and rejects junk`() {
        assertEquals(0xFF123456.toInt(), ThemeColors.parseHex("#123456"))
        assertEquals(0x80123456.toInt(), ThemeColors.parseHex("80123456"))
        assertNull(ThemeColors.parseHex("#12345"))
        assertNull(ThemeColors.parseHex("zzzzzz"))
        assertEquals("#123456", ThemeColors.toHex(0xFF123456.toInt()))
    }
}
