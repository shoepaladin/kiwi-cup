package com.example.wallpaperrotator

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/**
 * Returns the true physical display size in pixels.
 *
 * IMPORTANT: do NOT use `context.resources.displayMetrics` for wallpaper sizing.
 * That returns the current app window's usable area, which can exclude system-bar
 * insets and differs between an Activity context and the application context. The
 * wallpaper surface spans the full physical display, so using window metrics makes
 * the system zoom/crop the bitmap to fill the screen. Both the crop preview
 * (CropView) and the wallpaper renderer (WallpaperSetter) must use this same value.
 */
object ScreenUtils {

    fun getRealSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // maximumWindowMetrics covers the full display regardless of insets.
            val bounds = wm.maximumWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(point)
            point
        }
    }
}
