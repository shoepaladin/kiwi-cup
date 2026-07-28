package com.example.wallpaperrotator

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WallpaperReceiver : BroadcastReceiver() {

    private val TAG = "WallpaperReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        // Use goAsync to prevent the system from killing the receiver too early
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    "com.example.wallpaperrotator.ROTATE_WALLPAPER",
                    Intent.ACTION_USER_PRESENT -> {
                        rotateWallpaper(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun rotateWallpaper(context: Context) {
        try {
            val configManager = ConfigManager(context)
            val configs = configManager.getConfigs()
            
            if (configs.isEmpty()) return

            val rotationMode = configManager.getRotationMode()
            val setter = WallpaperSetter(context)
            
            // Rotate Home
            val homeConfigs = configs.filter { it.forHomeScreen }
            if (homeConfigs.isNotEmpty()) {
                val config = if (rotationMode == "random") homeConfigs.random() else {
                    val lastIndex = configManager.getLastRotationIndex("home")
                    val nextIndex = (lastIndex + 1) % homeConfigs.size
                    configManager.saveLastRotationIndex("home", nextIndex)
                    homeConfigs[nextIndex]
                }
                setter.setWallpaper(config, WallpaperManager.FLAG_SYSTEM)
            }

            // Rotate Lock
            val lockConfigs = configs.filter { it.forLockScreen }
            if (lockConfigs.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val config = if (rotationMode == "random") lockConfigs.random() else {
                    val lastIndex = configManager.getLastRotationIndex("lock")
                    val nextIndex = (lastIndex + 1) % lockConfigs.size
                    configManager.saveLastRotationIndex("lock", nextIndex)
                    lockConfigs[nextIndex]
                }
                setter.setWallpaper(config, WallpaperManager.FLAG_LOCK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in rotateWallpaper", e)
        }
    }
}
