package com.example.wallpaperrotator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val configManager = ConfigManager(context)
            if (configManager.getConfigs().isNotEmpty()) {
                scheduleWallpaperWork(context, configManager.getRotationInterval())
            }
        }
    }

    private fun scheduleWallpaperWork(context: Context, intervalMinutes: Int) {
        val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "wallpaper_rotation",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
