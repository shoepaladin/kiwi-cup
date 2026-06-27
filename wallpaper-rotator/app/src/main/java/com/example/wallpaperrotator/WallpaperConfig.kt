package com.example.wallpaperrotator

import android.graphics.RectF

data class WallpaperConfig(
    val imageUri: String,
    val cropRect: RectF,  // Normalized coordinates (0-1). Legacy fallback when [transform] is null.
    val rotation: Float,  // Rotation in degrees (informational; the full transform is in [transform]).
    val forLockScreen: Boolean,
    val forHomeScreen: Boolean,
    val id: Long = System.currentTimeMillis(),
    // 9 values of a Matrix mapping bitmap pixels -> normalized output [0,1]x[0,1].
    // Captures zoom, pan AND rotation exactly as positioned in the crop preview.
    // Null for configs saved before rotation support; those use [cropRect] + [rotation].
    val transform: FloatArray? = null
) {
    // Generated equals/hashCode would compare FloatArray by reference; identity by id is
    // all the app relies on, so override to keep behavior predictable.
    override fun equals(other: Any?): Boolean = other is WallpaperConfig && other.id == id
    override fun hashCode(): Int = id.hashCode()
}
