package com.example.wallpaperrotator

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallpaperReceiver : BroadcastReceiver() {

    private val TAG = "WallpaperReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.example.wallpaperrotator.ROTATE_WALLPAPER" -> {
                val lockScreenOnly = intent.getBooleanExtra("lock_screen_only", false)
                rotateWallpaper(context, lockScreenOnly)
            }
            Intent.ACTION_USER_PRESENT -> {
                val configManager = ConfigManager(context)
                if (configManager.getChangeOnUnlock()) {
                    rotateWallpaper(context, false)
                }
            }
        }
    }

    private fun rotateWallpaper(context: Context, lockScreenOnly: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configManager = ConfigManager(context)
                val configs = configManager.getConfigs()
                
                if (configs.isEmpty()) {
                    Log.w(TAG, "No wallpaper configurations found")
                    return@launch
                }

                val wallpaperManager = WallpaperManager.getInstance(context)
                val rotationMode = configManager.getRotationMode()

                // If lock screen only (manual volume button trigger)
                if (lockScreenOnly) {
                    val lockConfigs = configs.filter { it.forLockScreen }
                    if (lockConfigs.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val config = when (rotationMode) {
                            "random" -> lockConfigs.random()
                            else -> {
                                val lastIndex = configManager.getLastRotationIndex("lock")
                                val nextIndex = (lastIndex + 1) % lockConfigs.size
                                configManager.saveLastRotationIndex("lock", nextIndex)
                                lockConfigs[nextIndex]
                            }
                        }
                        applyWallpaper(context, wallpaperManager, config, WallpaperManager.FLAG_LOCK)
                        Log.d(TAG, "Manually changed lock screen wallpaper")
                    }
                    return@launch
                }

                // Otherwise rotate both as normal
                val homeConfigs = configs.filter { it.forHomeScreen }
                if (homeConfigs.isNotEmpty()) {
                    val config = when (rotationMode) {
                        "random" -> homeConfigs.random()
                        else -> {
                            val lastIndex = configManager.getLastRotationIndex("home")
                            val nextIndex = (lastIndex + 1) % homeConfigs.size
                            configManager.saveLastRotationIndex("home", nextIndex)
                            homeConfigs[nextIndex]
                        }
                    }
                    applyWallpaper(context, wallpaperManager, config, WallpaperManager.FLAG_SYSTEM)
                }

                val lockConfigs = configs.filter { it.forLockScreen }
                if (lockConfigs.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val config = when (rotationMode) {
                        "random" -> lockConfigs.random()
                        else -> {
                            val lastIndex = configManager.getLastRotationIndex("lock")
                            val nextIndex = (lastIndex + 1) % lockConfigs.size
                            configManager.saveLastRotationIndex("lock", nextIndex)
                            lockConfigs[nextIndex]
                        }
                    }
                    applyWallpaper(context, wallpaperManager, config, WallpaperManager.FLAG_LOCK)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error rotating wallpaper", e)
            }
        }
    }

    private fun applyWallpaper(
        context: Context,
        wallpaperManager: WallpaperManager,
        config: WallpaperConfig,
        flag: Int
    ) {
        var bitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        
        try {
            val uri = Uri.parse(config.imageUri)
            
            // Get screen dimensions
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // MEMORY OPTIMIZATION: Decode with sampling
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: $uri")
                return
            }
            
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Calculate sample size
            val sampleSize = calculateInSampleSize(options, screenWidth, screenHeight)
            
            val inputStream2 = context.contentResolver.openInputStream(uri)
            if (inputStream2 == null) {
                Log.e(TAG, "Cannot reopen input stream")
                return
            }
            
            bitmap = BitmapFactory.decodeStream(inputStream2, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
            inputStream2.close()

            if (bitmap != null) {
                croppedBitmap = processBitmap(bitmap, config)
                
                // MEMORY: Recycle original immediately
                bitmap.recycle()
                bitmap = null
                
                // Scale to screen dimensions
                scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, screenWidth, screenHeight, true)
                
                // MEMORY: Recycle intermediate immediately
                if (scaledBitmap != croppedBitmap) {
                    croppedBitmap.recycle()
                    croppedBitmap = null
                }
                
                wallpaperManager.suggestDesiredDimensions(screenWidth, screenHeight)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wallpaperManager.setBitmap(scaledBitmap, null, true, flag)
                } else {
                    wallpaperManager.setBitmap(scaledBitmap)
                }
                
                Log.d(TAG, "Successfully set wallpaper for flag: $flag")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying wallpaper", e)
        } finally {
            // CRITICAL: Clean up all bitmaps
            scaledBitmap?.recycle()
            rotatedBitmap?.recycle()
            croppedBitmap?.recycle()
            bitmap?.recycle()
        }
    }

    private fun processBitmap(bitmap: Bitmap, config: WallpaperConfig): Bitmap {
        // Apply crop first
        val cropX = (config.cropRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
        val cropY = (config.cropRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
        val cropWidth = ((config.cropRect.right - config.cropRect.left) * bitmap.width).toInt()
            .coerceIn(1, bitmap.width - cropX)
        val cropHeight = ((config.cropRect.bottom - config.cropRect.top) * bitmap.height).toInt()
            .coerceIn(1, bitmap.height - cropY)

        val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)

        // Apply rotation if needed
        return if (config.rotation != 0f) {
            val matrix = Matrix()
            matrix.postRotate(config.rotation)
            val rotatedBitmap = Bitmap.createBitmap(
                croppedBitmap, 
                0, 
                0, 
                croppedBitmap.width, 
                croppedBitmap.height, 
                matrix, 
                true
            )
            if (rotatedBitmap != croppedBitmap) {
                croppedBitmap.recycle()
            }
            rotatedBitmap
        } else {
            croppedBitmap
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
