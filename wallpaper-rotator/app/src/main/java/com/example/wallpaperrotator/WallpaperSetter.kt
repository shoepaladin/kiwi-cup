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
                // Legacy path for configs saved before rotation support.
                croppedBitmap = processBitmap(rawBitmap, config)
                finalBitmap = Bitmap.createScaledBitmap(croppedBitmap, sw, sh, true)
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
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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

    private fun processBitmap(bitmap: Bitmap, config: WallpaperConfig): Bitmap {
        val cropX = (config.cropRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
        val cropY = (config.cropRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
        val cropWidth = ((config.cropRect.right - config.cropRect.left) * bitmap.width).toInt()
            .coerceIn(1, bitmap.width - cropX)
        val cropHeight = ((config.cropRect.bottom - config.cropRect.top) * bitmap.height).toInt()
            .coerceIn(1, bitmap.height - cropY)

        val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
        
        return if (config.rotation != 0f) {
            val matrix = Matrix().apply { postRotate(config.rotation) }
            val rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            if (rotated != cropped) cropped.recycle()
            rotated
        } else {
            cropped
        }
    }
}
