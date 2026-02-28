package com.example.wallpaperrotator

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val TAG = "WallpaperWorker"

    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting wallpaper rotation from Worker")
            rotateWallpaper()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating wallpaper in Worker", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun rotateWallpaper() {
        val configManager = ConfigManager(applicationContext)
        val configs = configManager.getConfigs()
        
        if (configs.isEmpty()) {
            Log.w(TAG, "No wallpaper configurations found")
            return
        }

        val setter = WallpaperSetter(applicationContext)
        val rotationMode = configManager.getRotationMode()

        // Rotate home screen
        val homeConfigs = configs.filter { it.forHomeScreen }
        if (homeConfigs.isNotEmpty()) {
            val config = if (rotationMode == "random") {
                homeConfigs.random()
            } else {
                val lastIndex = configManager.getLastRotationIndex("home")
                val nextIndex = (lastIndex + 1) % homeConfigs.size
                configManager.saveLastRotationIndex("home", nextIndex)
                homeConfigs[nextIndex]
            }
            setter.setWallpaper(config, WallpaperManager.FLAG_SYSTEM)
        }

        // Rotate lock screen
        val lockConfigs = configs.filter { it.forLockScreen }
        if (lockConfigs.isNotEmpty()) {
            val config = if (rotationMode == "random") {
                lockConfigs.random()
            } else {
                val lastIndex = configManager.getLastRotationIndex("lock")
                val nextIndex = (lastIndex + 1) % lockConfigs.size
                configManager.saveLastRotationIndex("lock", nextIndex)
                lockConfigs[nextIndex]
            }
            setter.setWallpaper(config, WallpaperManager.FLAG_LOCK)
        }
    }
}
