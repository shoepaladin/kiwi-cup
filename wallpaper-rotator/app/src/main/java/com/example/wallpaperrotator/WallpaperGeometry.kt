package com.example.wallpaperrotator

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin geometry used when producing a wallpaper bitmap.
 *
 * Deliberately has NO dependency on android.graphics so it can be exercised by
 * fast host-side unit tests (android.graphics.Bitmap/Matrix/Rect are not
 * implemented on the JVM and throw "not mocked"). WallpaperSetter converts these
 * plain results into the corresponding Android calls.
 *
 * The core anti-stretch rule (see WallpaperManager docs): to fill a target of a
 * given aspect ratio WITHOUT distortion you must crop the source to a rectangle
 * whose aspect ratio equals the target's, then scale uniformly. Independently
 * scaling both axes into a mismatched aspect ratio is what stretches the image.
 */
object WallpaperGeometry {

    /** A pixel rectangle with integer, inclusive-left/exclusive-right bounds. */
    data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val aspect: Float get() = width.toFloat() / height.toFloat()
    }

    /**
     * Same algorithm BitmapFactory sampling has always used here, extracted so it
     * can be tested. Returns a power-of-two subsample factor (>= 1) that keeps the
     * decoded bitmap at least [reqWidth] x [reqHeight].
     */
    fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Given a normalized crop region (each coordinate in 0..1) on a bitmap of
     * [bmpWidth] x [bmpHeight], return the largest pixel rectangle that:
     *  - lies inside that region (never samples outside the user's selection),
     *  - is centered within the region, and
     *  - has the SAME aspect ratio as [targetWidth] x [targetHeight].
     *
     * Because the result matches the target aspect ratio, uniformly scaling it up
     * to the target size fills the screen with no stretching ("cover" semantics).
     *
     * Robust to inverted or degenerate regions (falls back to the whole bitmap).
     */
    fun aspectCorrectedCrop(
        normLeft: Float,
        normTop: Float,
        normRight: Float,
        normBottom: Float,
        bmpWidth: Int,
        bmpHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): PxRect {
        // Normalize (handle inverted rects) and clamp to [0,1], then to pixels.
        var left = min(normLeft, normRight).coerceIn(0f, 1f) * bmpWidth
        var right = max(normLeft, normRight).coerceIn(0f, 1f) * bmpWidth
        var top = min(normTop, normBottom).coerceIn(0f, 1f) * bmpHeight
        var bottom = max(normTop, normBottom).coerceIn(0f, 1f) * bmpHeight

        var regionW = right - left
        var regionH = bottom - top

        // Degenerate region -> use the whole bitmap.
        if (regionW < 1f || regionH < 1f) {
            left = 0f; top = 0f
            right = bmpWidth.toFloat(); bottom = bmpHeight.toFloat()
            regionW = bmpWidth.toFloat(); regionH = bmpHeight.toFloat()
        }

        val targetAspect = targetWidth.toFloat() / targetHeight.toFloat()
        val regionAspect = regionW / regionH

        // Shrink one dimension so the crop matches the target aspect exactly.
        var cropW = regionW
        var cropH = regionH
        if (regionAspect > targetAspect) {
            // Region is too wide: keep its height, trim the width.
            cropW = regionH * targetAspect
        } else {
            // Region is too tall: keep its width, trim the height.
            cropH = regionW / targetAspect
        }

        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f

        // Round to integer pixels and clamp so the rect stays inside the bitmap.
        val il = (centerX - cropW / 2f).roundToInt().coerceIn(0, max(0, bmpWidth - 1))
        val it = (centerY - cropH / 2f).roundToInt().coerceIn(0, max(0, bmpHeight - 1))
        val iw = cropW.roundToInt().coerceIn(1, bmpWidth - il)
        val ih = cropH.roundToInt().coerceIn(1, bmpHeight - it)

        return PxRect(il, it, il + iw, it + ih)
    }
}
