package com.example.wallpaperrotator

import android.graphics.RectF

data class WallpaperConfig(
    val imageUri: String,
    val cropRect: RectF,  // Normalized coordinates (0-1)
    val rotation: Float,
    val forLockScreen: Boolean,
    val forHomeScreen: Boolean,
    val id: Long = System.currentTimeMillis()
)
