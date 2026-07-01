package com.example.wallpaperrotator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Host-side tests for the wallpaper geometry. These run on the JVM (no emulator)
 * and exist to guarantee we are STRICTLY improving behavior:
 *  - the produced crop always matches the screen aspect ratio (no stretching),
 *  - the crop never leaves the bitmap or the user's selected region,
 *  - decode sub-sampling is unchanged (no image-quality regression).
 */
class WallpaperGeometryTest {

    // A representative modern phone screen (portrait 1080x2400).
    private val screenW = 1080
    private val screenH = 2400
    private val screenAspect = screenW.toFloat() / screenH.toFloat()

    // ---- inSampleSize parity (guards against decode-quality regressions) ----

    /** Reference implementation = the algorithm the app has always used. */
    private fun refSample(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var s = 1
        if (srcH > reqH || srcW > reqW) {
            val hh = srcH / 2
            val hw = srcW / 2
            while (hh / s >= reqH && hw / s >= reqW) s *= 2
        }
        return s
    }

    @Test
    fun inSampleSize_knownValues() {
        assertEquals(1, WallpaperGeometry.calculateInSampleSize(4000, 3000, 1080, 2400))
        assertEquals(2, WallpaperGeometry.calculateInSampleSize(8000, 6000, 1080, 2400))
        assertEquals(1, WallpaperGeometry.calculateInSampleSize(500, 500, 1080, 2400))
        assertEquals(32, WallpaperGeometry.calculateInSampleSize(4000, 4000, 100, 100))
    }

    @Test
    fun inSampleSize_matchesReferenceAcrossManyInputs() {
        val sizes = listOf(100, 640, 1080, 1500, 2000, 3000, 4096, 8000, 12000)
        for (sw in sizes) for (sh in sizes) {
            val expected = refSample(sw, sh, screenW, screenH)
            val actual = WallpaperGeometry.calculateInSampleSize(sw, sh, screenW, screenH)
            assertEquals("src=${sw}x$sh", expected, actual)
        }
    }

    // ---- aspectCorrectedCrop: the core anti-stretch guarantee ----

    private fun assertMatchesScreenAspect(rect: WallpaperGeometry.PxRect) {
        val rel = abs(rect.aspect - screenAspect) / screenAspect
        assertTrue(
            "crop aspect ${rect.aspect} should match screen aspect $screenAspect (rel=$rel)",
            rel < 0.01f
        )
    }

    private fun assertInsideBitmap(rect: WallpaperGeometry.PxRect, w: Int, h: Int) {
        assertTrue("left>=0", rect.left >= 0)
        assertTrue("top>=0", rect.top >= 0)
        assertTrue("right<=w", rect.right <= w)
        assertTrue("bottom<=h", rect.bottom <= h)
        assertTrue("width>=1", rect.width >= 1)
        assertTrue("height>=1", rect.height >= 1)
    }

    @Test
    fun fullRegion_landscapeBitmap_matchesScreenAspect() {
        val rect = WallpaperGeometry.aspectCorrectedCrop(
            0f, 0f, 1f, 1f, 4000, 3000, screenW, screenH
        )
        assertMatchesScreenAspect(rect)
        assertInsideBitmap(rect, 4000, 3000)
    }

    @Test
    fun fullRegion_tallPortraitBitmap_matchesScreenAspect() {
        val rect = WallpaperGeometry.aspectCorrectedCrop(
            0f, 0f, 1f, 1f, 2000, 5000, screenW, screenH
        )
        assertMatchesScreenAspect(rect)
        assertInsideBitmap(rect, 2000, 5000)
    }

    @Test
    fun regionAlreadyScreenAspect_isPreservedAndFillsWidth() {
        // A bitmap that is exactly screen aspect; whole image selected.
        val rect = WallpaperGeometry.aspectCorrectedCrop(
            0f, 0f, 1f, 1f, screenW, screenH, screenW, screenH
        )
        assertMatchesScreenAspect(rect)
        // Nothing should be trimmed for an already-correct aspect ratio.
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(screenW, rect.right)
        assertEquals(screenH, rect.bottom)
    }

    @Test
    fun subRegion_cropStaysInsideSelection() {
        val w = 3000; val h = 3000
        val rect = WallpaperGeometry.aspectCorrectedCrop(
            0.25f, 0.25f, 0.75f, 0.75f, w, h, screenW, screenH
        )
        assertMatchesScreenAspect(rect)
        assertInsideBitmap(rect, w, h)
        // The selected region in pixels is [750, 2250] on both axes.
        assertTrue("stays left of selection", rect.left >= 750 - 1)
        assertTrue("stays right of selection", rect.right <= 2250 + 1)
        assertTrue("stays above selection", rect.top >= 750 - 1)
        assertTrue("stays below selection", rect.bottom <= 2250 + 1)
    }

    @Test
    fun invertedRegion_isHandled() {
        // right < left and bottom < top should still yield a valid, aspect-correct rect.
        val rect = WallpaperGeometry.aspectCorrectedCrop(
            0.8f, 0.9f, 0.2f, 0.3f, 3000, 4000, screenW, screenH
        )
        assertMatchesScreenAspect(rect)
        assertInsideBitmap(rect, 3000, 4000)
    }

    @Test
    fun degenerateRegion_fallsBackToWholeBitmap() {
        val rect = WallpaperGeometry.aspectCorrectedCrop(
            0.5f, 0.5f, 0.5f, 0.5f, 4000, 3000, screenW, screenH
        )
        assertMatchesScreenAspect(rect)
        assertInsideBitmap(rect, 4000, 3000)
    }

    @Test
    fun noStretch_acrossManyBitmapAndScreenSizes() {
        val bmpSizes = listOf(
            800 to 600, 1200 to 1600, 3000 to 3000, 4000 to 2250, 2000 to 6000, 6000 to 2000
        )
        val screens = listOf(
            1080 to 2400, 1440 to 3200, 1080 to 1920, 720 to 1600, 1600 to 720 // incl. landscape
        )
        for ((bw, bh) in bmpSizes) for ((tw, th) in screens) {
            val rect = WallpaperGeometry.aspectCorrectedCrop(0f, 0f, 1f, 1f, bw, bh, tw, th)
            val targetAspect = tw.toFloat() / th.toFloat()
            val rel = abs(rect.aspect - targetAspect) / targetAspect
            assertTrue(
                "bmp=${bw}x$bh target=${tw}x$th -> cropAspect=${rect.aspect} targetAspect=$targetAspect rel=$rel",
                rel < 0.01f
            )
            assertInsideBitmap(rect, bw, bh)
        }
    }
}
