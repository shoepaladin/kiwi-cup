package com.example.wallpaperrotator

import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "WallpaperWorker"
    private val NOTIFICATION_ID = 1001

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "=== Starting wallpaper rotation ===")
            
            // CRITICAL: Promote to foreground service to prevent killing
            setForeground(createForegroundInfo("Rotating wallpaper..."))
            
            rotateWallpaper()
            
            Log.d(TAG, "=== Wallpaper rotation complete ===")
            Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - URI permission lost", e)
            // Don't retry security exceptions
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating wallpaper", e)
            if (runAttemptCount < 3) {
                Log.w(TAG, "Retrying... (attempt ${runAttemptCount + 1}/3)")
                Result.retry()
            } else {
                Log.e(TAG, "Max retries reached, giving up")
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(
            applicationContext,
            WallpaperRotatorApplication.CHANNEL_ID
        )
            .setContentTitle("Wallpaper Rotator")
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun rotateWallpaper() {
        val configManager = ConfigManager(applicationContext)
        val configs = configManager.getConfigs()
        
        if (configs.isEmpty()) {
            Log.w(TAG, "No wallpaper configurations found")
            return
        }

        val rotationMode = configManager.getRotationMode()
        val homeConfigs = configs.filter { it.forHomeScreen }
        val lockConfigs = configs.filter { it.forLockScreen }

        Log.d(TAG, "Total configs: ${configs.size}, Home: ${homeConfigs.size}, Lock: ${lockConfigs.size}")

        // Rotate home screen
        if (homeConfigs.isNotEmpty()) {
            setForeground(createForegroundInfo("Changing home screen..."))
            
            val config = if (rotationMode == "random") {
                homeConfigs.random()
            } else {
                val lastIndex = configManager.getLastRotationIndex("home")
                val nextIndex = (lastIndex + 1) % homeConfigs.size
                configManager.saveLastRotationIndex("home", nextIndex)
                homeConfigs[nextIndex]
            }
            
            Log.d(TAG, "Setting home screen wallpaper: ${config.imageUri}")
            setWallpaper(config, WallpaperManager.FLAG_SYSTEM)
        }

        // Rotate lock screen
        if (lockConfigs.isNotEmpty()) {
            setForeground(createForegroundInfo("Changing lock screen..."))
            
            val config = if (rotationMode == "random") {
                lockConfigs.random()
            } else {
                val lastIndex = configManager.getLastRotationIndex("lock")
                val nextIndex = (lastIndex + 1) % lockConfigs.size
                configManager.saveLastRotationIndex("lock", nextIndex)
                lockConfigs[nextIndex]
            }
            
            Log.d(TAG, "Setting lock screen wallpaper: ${config.imageUri}")
            setWallpaper(config, WallpaperManager.FLAG_LOCK)
        }
    }

    private fun setWallpaper(config: WallpaperConfig, flags: Int) {
        var bitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        
        try {
            val uri = Uri.parse(config.imageUri)
            
            // CRITICAL: Check if we still have permission for this URI
            val persistedUris = applicationContext.contentResolver.persistedUriPermissions
            val hasPermission = persistedUris.any { it.uri == uri }
            
            if (!hasPermission) {
                Log.e(TAG, "No persisted permission for URI: $uri")
                Log.e(TAG, "This usually happens after app reinstall")
                Log.e(TAG, "User needs to re-add this wallpaper")
                throw SecurityException("URI permission lost for: $uri")
            }
            
            // Try to re-verify permission
            try {
                applicationContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot take persistent permission, attempting read anyway")
            }
            
            val displayMetrics = applicationContext.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            Log.d(TAG, "Screen: ${screenWidth}x${screenHeight}")
            
            // Decode with inSampleSize
            val inputStream = applicationContext.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: $uri")
                throw SecurityException("Cannot access URI: $uri")
            }
            
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            val sampleSize = calculateInSampleSize(options, screenWidth, screenHeight)
            Log.d(TAG, "Sample size: $sampleSize (${options.outWidth}x${options.outHeight} → ~${options.outWidth/sampleSize}x${options.outHeight/sampleSize})")
            
            val inputStream2 = applicationContext.contentResolver.openInputStream(uri)
            if (inputStream2 == null) {
                Log.e(TAG, "Cannot reopen input stream")
                throw SecurityException("Cannot access URI: $uri")
            }
            
            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            bitmap = BitmapFactory.decodeStream(inputStream2, null, finalOptions)
            inputStream2.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap")
                throw Exception("Bitmap decode failed")
            }

            Log.d(TAG, "Loaded: ${bitmap.width}x${bitmap.height}")

            // Apply crop
            val cropRect = config.cropRect
            val cropX = (cropRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
            val cropY = (cropRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
            val cropWidth = ((cropRect.right - cropRect.left) * bitmap.width).toInt()
                .coerceIn(1, bitmap.width - cropX)
            val cropHeight = ((cropRect.bottom - cropRect.top) * bitmap.height).toInt()
                .coerceIn(1, bitmap.height - cropY)

            Log.d(TAG, "Crop: ($cropX,$cropY) ${cropWidth}x${cropHeight}")

            croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
            bitmap.recycle()
            bitmap = null

            // Apply rotation
            val finalBitmap = if (config.rotation != 0f) {
                val matrix = Matrix().apply { postRotate(config.rotation) }
                val rotated = Bitmap.createBitmap(
                    croppedBitmap, 0, 0,
                    croppedBitmap.width, croppedBitmap.height,
                    matrix, true
                )
                croppedBitmap.recycle()
                croppedBitmap = null
                rotatedBitmap = rotated
                rotated
            } else {
                croppedBitmap
            }

            // Scale to screen
            scaledBitmap = if (finalBitmap.width != screenWidth || finalBitmap.height != screenHeight) {
                Log.d(TAG, "Scaling to screen size")
                val scaled = Bitmap.createScaledBitmap(finalBitmap, screenWidth, screenHeight, true)
                if (finalBitmap != croppedBitmap && finalBitmap != rotatedBitmap) {
                    finalBitmap.recycle()
                }
                scaled
            } else {
                finalBitmap
            }

            Log.d(TAG, "Final: ${scaledBitmap.width}x${scaledBitmap.height}")

            // Set wallpaper
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            wallpaperManager.suggestDesiredDimensions(screenWidth, screenHeight)
            wallpaperManager.setBitmap(scaledBitmap, null, true, flags)
            
            Log.d(TAG, "✓ Wallpaper set successfully for flags: $flags")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper", e)
            throw e
        } finally {
            scaledBitmap?.recycle()
            rotatedBitmap?.recycle()
            croppedBitmap?.recycle()
            bitmap?.recycle()
            System.gc()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
