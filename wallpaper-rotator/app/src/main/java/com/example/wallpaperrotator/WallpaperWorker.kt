package com.example.wallpaperrotator

import android.app.WallpaperManager
import android.content.Context
import android.content.pm.ServiceInfo
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
            Log.d(TAG, "=== Starting rotation ===")
            setForeground(createForegroundInfo("Updating..."))
            rotateWallpaper()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun createForegroundInfo(p: String): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, WallpaperRotatorApplication.CHANNEL_ID)
            .setContentTitle("Wallpaper Rotator")
            .setContentText(p)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, n)
        }
    }

    private suspend fun rotateWallpaper() {
        val cm = ConfigManager(applicationContext)
        val configs = cm.getConfigs()
        if (configs.isEmpty()) return

        val mode = cm.getRotationMode()
        val setter = WallpaperSetter(applicationContext)
        
        // Handle Home
        val homes = configs.filter { it.forHomeScreen }
        if (homes.isNotEmpty()) {
            val c = if (mode == "random") homes.random() else {
                val idx = (cm.getLastRotationIndex("home") + 1) % homes.size
                cm.saveLastRotationIndex("home", idx)
                homes[idx]
            }
            setter.setWallpaper(c, WallpaperManager.FLAG_SYSTEM)
        }

        // Handle Lock
        val locks = configs.filter { it.forLockScreen }
        if (locks.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val c = if (mode == "random") locks.random() else {
                val idx = (cm.getLastRotationIndex("lock") + 1) % locks.size
                cm.saveLastRotationIndex("lock", idx)
                locks[idx]
            }
            setter.setWallpaper(c, WallpaperManager.FLAG_LOCK)
        }
    }
}
