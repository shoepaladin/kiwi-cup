package com.example.wallpaperrotator

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log

class WallpaperSetter(private val context: Context) {

    private val TAG = "WallpaperSetter"

    fun setWallpaper(config: WallpaperConfig, flag: Int) {
        var bitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null
        
        try {
            val uri = Uri.parse(config.imageUri)
            
            // Re-verify permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Permission re-verification failed, continuing anyway")
            }

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: $uri")
                return
            }

            // Load FULL original bitmap (don't pre-crop!)
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI")
                return
            }

            Log.d(TAG, "Original bitmap: ${bitmap.width}x${bitmap.height}")
            Log.d(TAG, "Normalized crop rect: ${config.cropRect}")

            // Apply rotation FIRST (if needed)
            val workingBitmap = if (config.rotation != 0f) {
                val matrix = Matrix().apply {
                    postRotate(config.rotation)
                }
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotatedBitmap = rotated
                rotated
            } else {
                bitmap
            }

            // Calculate visible crop rect in BITMAP coordinates (not normalized!)
            // This is the KEY difference - we pass the rect to the system, not pre-crop
            val cropX = (config.cropRect.left * workingBitmap.width).toInt()
                .coerceIn(0, workingBitmap.width)
            val cropY = (config.cropRect.top * workingBitmap.height).toInt()
                .coerceIn(0, workingBitmap.height)
            val cropRight = (config.cropRect.right * workingBitmap.width).toInt()
                .coerceIn(cropX, workingBitmap.width)
            val cropBottom = (config.cropRect.bottom * workingBitmap.height).toInt()
                .coerceIn(cropY, workingBitmap.height)

            // Create Rect in bitmap coordinates
            val visibleCropHint = Rect(cropX, cropY, cropRight, cropBottom)
            
            Log.d(TAG, "Visible crop hint (bitmap coords): $visibleCropHint")
            Log.d(TAG, "Crop dimensions: ${visibleCropHint.width()}x${visibleCropHint.height()}")

            val wallpaperManager = WallpaperManager.getInstance(context)

            // CRITICAL: Use setBitmap(Bitmap, Rect, boolean) or setBitmap(Bitmap, Rect, boolean, int)
            // Pass the FULL bitmap and let the system handle cropping/positioning!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // For Android N and above, we can specify home/lock screen
                wallpaperManager.setBitmap(
                    workingBitmap,      // Full bitmap
                    visibleCropHint,    // Which part to show
                    true,                // Allow backup
                    flag                 // HOME or LOCK screen
                )
                Log.d(TAG, "✓ Set wallpaper using visible crop hint for flag: $flag")
            } else {
                // For older Android, use the 3-parameter version
                wallpaperManager.setBitmap(
                    workingBitmap,
                    visibleCropHint,
                    true
                )
                Log.d(TAG, "✓ Set wallpaper using visible crop hint")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper", e)
        } finally {
            // Clean up
            rotatedBitmap?.recycle()
            bitmap?.recycle()
        }
    }
}
