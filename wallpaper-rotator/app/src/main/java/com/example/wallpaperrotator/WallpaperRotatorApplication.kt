package com.example.wallpaperrotator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class WallpaperRotatorApplication : Application() {

    companion object {
        private const val TAG = "WallpaperApp"
        const val CHANNEL_ID = "wallpaper_rotation_channel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application starting")
        
        // CRITICAL: Create notification channel FIRST, before anything else
        createNotificationChannel()
        
        // Clean up invalid URI permissions on app start
        cleanupInvalidUriPermissions()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Wallpaper Rotation"
            val descriptionText = "Shows when wallpaper is being changed"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }

    private fun cleanupInvalidUriPermissions() {
        try {
            val configManager = ConfigManager(this)
            val configs = configManager.getConfigs().toMutableList()
            val originalSize = configs.size
            
            // Get list of URIs we still have permission for
            val persistedPermissions = contentResolver.persistedUriPermissions
            val validUris = persistedPermissions.map { it.uri.toString() }.toSet()
            
            Log.d(TAG, "Persisted URI permissions: ${validUris.size}")
            Log.d(TAG, "Stored configs: ${configs.size}")
            
            // Remove configs with invalid URIs
            val validConfigs = configs.filter { config ->
                val hasPermission = validUris.contains(config.imageUri)
                if (!hasPermission) {
                    Log.w(TAG, "Removing config with invalid URI: ${config.imageUri}")
                }
                hasPermission
            }
            
            if (validConfigs.size != originalSize) {
                Log.w(TAG, "Removed ${originalSize - validConfigs.size} configs with invalid URIs")
                configManager.saveConfigs(validConfigs)
            } else {
                Log.d(TAG, "All configs have valid URI permissions")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up invalid URI permissions", e)
        }
    }
}
