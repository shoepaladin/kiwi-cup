package com.example.wallpaperrotator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
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
    private val CHANNEL_ID = "wallpaper_rotation_channel"
    private val NOTIFICATION_ID = 1001

    override suspend fun doWork(): Result {
        return try {
            // CRITICAL: Promote to foreground service to prevent killing
            setForeground(createForegroundInfo("Rotating wallpaper..."))
            
            Log.d(TAG, "Starting wallpaper rotation (foreground)")
            rotateWallpaper()
            Log.d(TAG, "Wallpaper rotation successful")
            Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - URI permission lost", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating wallpaper", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    // CRITICAL: Create foreground info to prevent killing
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Wallpaper Rotator")
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wallpaper Rotation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when wallpaper is being changed"
                setShowBadge(false)
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
            
            // Re-verify URI permission
            try {
                applicationContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "URI permission not persistable, attempting read anyway")
            }
            
            val inputStream = applicationContext.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: $uri")
                return
            }

            // MEMORY OPTIMIZATION: Calculate required size first
            val displayMetrics = applicationContext.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Decode with inSampleSize to reduce memory usage
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Calculate inSampleSize to avoid loading huge images
            val sampleSize = calculateInSampleSize(options, screenWidth, screenHeight)
            
            // Now decode with proper sampling
            val inputStream2 = applicationContext.contentResolver.openInputStream(uri)
            if (inputStream2 == null) {
                Log.e(TAG, "Cannot reopen input stream for URI: $uri")
                return
            }
            
            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            bitmap = BitmapFactory.decodeStream(inputStream2, null, finalOptions)
            inputStream2.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI: $uri")
                return
            }

            Log.d(TAG, "Loaded bitmap: ${bitmap.width}x${bitmap.height} (sampled by $sampleSize)")
            Log.d(TAG, "Crop rect: ${config.cropRect}")

            // Apply crop
            val cropRect = config.cropRect
            val cropX = (cropRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
            val cropY = (cropRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
            val cropWidth = ((cropRect.right - cropRect.left) * bitmap.width).toInt()
                .coerceIn(1, bitmap.width - cropX)
            val cropHeight = ((cropRect.bottom - cropRect.top) * bitmap.height).toInt()
                .coerceIn(1, bitmap.height - cropY)

            Log.d(TAG, "Cropping to: x=$cropX, y=$cropY, w=$cropWidth, h=$cropHeight")

            croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
            
            // MEMORY: Recycle original immediately
            bitmap.recycle()
            bitmap = null

            Log.d(TAG, "Cropped bitmap: ${croppedBitmap.width}x${croppedBitmap.height}")

            // Apply rotation if needed
            val finalBitmap = if (config.rotation != 0f) {
                val matrix = Matrix().apply {
                    postRotate(config.rotation)
                }
                val rotated = Bitmap.createBitmap(
                    croppedBitmap,
                    0,
                    0,
                    croppedBitmap.width,
                    croppedBitmap.height,
                    matrix,
                    true
                )
                // MEMORY: Recycle cropped immediately
                croppedBitmap.recycle()
                croppedBitmap = null
                rotatedBitmap = rotated
                rotated
            } else {
                croppedBitmap
            }

            Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")
            Log.d(TAG, "Final bitmap before scaling: ${finalBitmap.width}x${finalBitmap.height}")
            
            // Scale to exact screen dimensions
            scaledBitmap = if (finalBitmap.width != screenWidth || finalBitmap.height != screenHeight) {
                Log.d(TAG, "Scaling bitmap to match screen")
                val scaled = Bitmap.createScaledBitmap(finalBitmap, screenWidth, screenHeight, true)
                // MEMORY: Recycle intermediate immediately
                if (finalBitmap != croppedBitmap && finalBitmap != rotatedBitmap) {
                    finalBitmap.recycle()
                }
                scaled
            } else {
                Log.d(TAG, "Bitmap already matches screen size")
                finalBitmap
            }

            Log.d(TAG, "Final scaled bitmap: ${scaledBitmap.width}x${scaledBitmap.height}")

            // Set wallpaper with proper dimensions
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            try {
                wallpaperManager.suggestDesiredDimensions(screenWidth, screenHeight)
                wallpaperManager.setBitmap(scaledBitmap, null, true, flags)
                
                Log.d(TAG, "✓ Successfully set wallpaper for flags: $flags")
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for wallpaper flags: $flags", e)
                throw e
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper", e)
            throw e
        } finally {
            // CRITICAL: Clean up ALL bitmaps to prevent memory leaks
            try {
                scaledBitmap?.recycle()
                rotatedBitmap?.recycle()
                croppedBitmap?.recycle()
                bitmap?.recycle()
                Log.d(TAG, "Memory cleaned up")
            } catch (e: Exception) {
                Log.e(TAG, "Error recycling bitmaps", e)
            }
            
            // Force garbage collection hint (system may ignore)
            System.gc()
        }
    }

    // Calculate sample size to avoid loading huge images
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
