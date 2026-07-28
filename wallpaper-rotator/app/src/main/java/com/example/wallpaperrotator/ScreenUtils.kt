package com.example.wallpaperrotator

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager

/**
 * Returns the true physical display size in pixels, in the device's NATURAL
 * (portrait) orientation — the same value no matter what orientation the device
 * happens to be in when this is called.
 *
 * IMPORTANT: do NOT use `context.resources.displayMetrics` for wallpaper sizing.
 * That returns the current app window's usable area, which can exclude system-bar
 * insets and differs between an Activity context and the application context. The
 * wallpaper surface spans the full physical display, so using window metrics makes
 * the system zoom/crop the bitmap to fill the screen. Both the crop preview
 * (CropView) and the wallpaper renderer (WallpaperSetter) must use this same value.
 *
 * IMPORTANT: the raw values from maximumWindowMetrics / getRealSize swap width and
 * height with the CURRENT display rotation. Wallpaper rotation runs in the
 * background (WorkManager / unlock receiver), so it can fire while the device is
 * in landscape; rendering a landscape-sized bitmap makes the system zoom/crop it
 * to fill the portrait wallpaper surface. Normalizing by the display rotation
 * makes the result stable across calls, matching the fact that the physical
 * panel never changes.
 */
object ScreenUtils {

    fun getRealSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // maximumWindowMetrics covers the full display regardless of insets.
            val bounds = wm.maximumWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(it)
            }
        }

        // Undo the current rotation so the result is always in natural orientation.
        // DisplayManager works from any context (including the application context).
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val rotation = dm.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        return if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            Point(size.y, size.x)
        } else {
            size
        }
    }
}
