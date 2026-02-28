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
        val action = intent.action
        Log.d(TAG, "Received broadcast: $action")
        
        when (action) {
            "com.example.wallpaperrotator.ROTATE_WALLPAPER" -> {
                val lockScreenOnly = intent.getBooleanExtra("lock_screen_only", false)
                rotateWallpaper(context, lockScreenOnly)
            }
            Intent.ACTION_USER_PRESENT -> {
                val configManager = ConfigManager(context)
                if (configManager.getChangeOnUnlock()) {
                    Log.d(TAG, "Device unlocked - triggering rotation")
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

                val setter = WallpaperSetter(context)
                val rotationMode = configManager.getRotationMode()

                if (lockScreenOnly) {
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
                } else {
                    // Home Screen
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

                    // Lock Screen
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
            } catch (e: Exception) {
                Log.e(TAG, "Error in rotateWallpaper", e)
            }
        }
    }
}
