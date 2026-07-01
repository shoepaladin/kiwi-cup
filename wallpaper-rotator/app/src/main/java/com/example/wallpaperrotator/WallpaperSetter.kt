package com.example.wallpaperrotator

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Log

class WallpaperSetter(private val context: Context) {

    private val TAG = "WallpaperSetter"

    fun setWallpaper(config: WallpaperConfig, flags: Int) {
        var rawBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        var finalBitmap: Bitmap? = null

        try {
            val uri = Uri.parse(config.imageUri)
            val wm = WallpaperManager.getInstance(context)

            // USE THE TRUE PHYSICAL SCREEN DIMENSIONS (not window metrics).
            // This matches the aspect ratio enforced in CropView and is what the
            // wallpaper surface actually uses, so the system won't zoom/crop it.
            val size = ScreenUtils.getRealSize(context)
            val sw = size.x
            val sh = size.y

            Log.d(TAG, "Setting wallpaper. Screen: ${sw}x${sh}, Flags: $flags")

            // 1. Permission Check
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Permission check: ${e.message}")
            }

            // 2. Sampled Decode
            rawBitmap = decodeSampledBitmapFromUri(uri, sw, sh)
            if (rawBitmap == null) {
                Log.e(TAG, "Failed to decode $uri")
                return
            }

            // 3 & 4. Produce the exact screen-sized bitmap.
            val transform = config.transform
            if (transform != null) {
                // Preferred path: reproduce the crop preview's transform (zoom + pan +
                // rotation) pixel-for-pixel onto a screen-sized canvas.
                finalBitmap = renderWithTransform(rawBitmap, transform, sw, sh)
            } else {
                // Legacy path for configs saved before rotation support. The crop is
                // first reduced to the EXACT screen aspect ratio (WallpaperGeometry),
                // so the final uniform scale to sw x sh cannot stretch the image.
                val cropped = processBitmap(rawBitmap, config, sw, sh)
                croppedBitmap = cropped
                finalBitmap = if (cropped.width == sw && cropped.height == sh) {
                    croppedBitmap = null // ownership transferred to finalBitmap
                    cropped
                } else {
                    Bitmap.createScaledBitmap(cropped, sw, sh, true)
                }
            }

            // 5. Tell the system we want a screen-sized wallpaper (disables parallax stretching)
            try {
                wm.suggestDesiredDimensions(sw, sh)
            } catch (e: Exception) {
                Log.w(TAG, "Could not suggest dimensions: ${e.message}")
            }

            // 6. Set the wallpaper
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(finalBitmap, null, true, flags)
            } else {
                wm.setBitmap(finalBitmap)
            }
            
            Log.d(TAG, "✓ Wallpaper set successfully at ${sw}x${sh}")

        } catch (e: Exception) {
            Log.e(TAG, "Error in setWallpaper", e)
        } finally {
            rawBitmap?.recycle()
            croppedBitmap?.recycle()
            finalBitmap?.recycle()
        }
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                
                context.contentResolver.openInputStream(uri)?.use { innerInput ->
                    BitmapFactory.decodeStream(innerInput, null, options)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        return WallpaperGeometry.calculateInSampleSize(
            options.outWidth, options.outHeight, reqWidth, reqHeight
        )
    }

    /**
     * Renders [bitmap] onto a [sw]x[sh] canvas using [transformValues], a Matrix that
     * maps bitmap pixels -> normalized output [0,1]. Reproduces exactly what was framed
     * in the crop preview, including rotation. Any area outside the image is black.
     */
    private fun renderWithTransform(
        bitmap: Bitmap,
        transformValues: FloatArray,
        sw: Int,
        sh: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val matrix = Matrix()
        matrix.setValues(transformValues)      // bitmap -> normalized [0,1]
        matrix.postScale(sw.toFloat(), sh.toFloat()) // normalized -> output pixels

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        return output
    }

    /**
     * Legacy (transform == null) crop pipeline. Returns a bitmap whose aspect ratio
     * matches [targetW] x [targetH] so the caller's uniform scale cannot stretch it.
     */
    private fun processBitmap(bitmap: Bitmap, config: WallpaperConfig, targetW: Int, targetH: Int): Bitmap {
        // Apply rotation first (legacy configs are almost always 0°, but stay correct).
        var working = bitmap
        var rotatedCopy = false
        if (config.rotation != 0f) {
            val matrix = Matrix().apply { postRotate(config.rotation) }
            working = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            rotatedCopy = working != bitmap
        }

        // The stored normalized crop only maps to the un-rotated image; once rotated,
        // fall back to the whole (rotated) image before aspect-correcting.
        val crop = if (config.rotation == 0f) {
            WallpaperGeometry.aspectCorrectedCrop(
                config.cropRect.left, config.cropRect.top,
                config.cropRect.right, config.cropRect.bottom,
                working.width, working.height, targetW, targetH
            )
        } else {
            WallpaperGeometry.aspectCorrectedCrop(
                0f, 0f, 1f, 1f, working.width, working.height, targetW, targetH
            )
        }

        val result = Bitmap.createBitmap(working, crop.left, crop.top, crop.width, crop.height)
        if (rotatedCopy) working.recycle()
        return result
    }
}
