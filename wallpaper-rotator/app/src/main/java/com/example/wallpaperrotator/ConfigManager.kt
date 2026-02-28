package com.example.wallpaperrotator

import android.content.Context
import android.content.SharedPreferences
import android.graphics.RectF
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wallpaper_configs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveConfigs(configs: List<WallpaperConfig>) {
        val json = gson.toJson(configs)
        prefs.edit().putString("configs", json).apply()
    }

    fun getConfigs(): List<WallpaperConfig> {
        val json = prefs.getString("configs", null) ?: return emptyList()
        val type = object : TypeToken<List<WallpaperConfig>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveRotationInterval(minutes: Int) {
        prefs.edit().putInt("rotation_interval", minutes).apply()
    }

    fun getRotationInterval(): Int {
        return prefs.getInt("rotation_interval", 60) // Default 1 hour
    }

    // NEW: Methods for tracking rotation indices
    fun saveHomeScreenIndex(index: Int) {
        prefs.edit().putInt("home_screen_index", index).apply()
    }

    fun getHomeScreenIndex(): Int {
        return prefs.getInt("home_screen_index", 0)
    }

    fun saveLockScreenIndex(index: Int) {
        prefs.edit().putInt("lock_screen_index", index).apply()
    }

    fun getLockScreenIndex(): Int {
        return prefs.getInt("lock_screen_index", 0)
    }

    // Keep old methods for backward compatibility with WallpaperReceiver
    fun saveLastRotationIndex(screenType: String, index: Int) {
        prefs.edit().putInt("last_index_$screenType", index).apply()
    }

    fun getLastRotationIndex(screenType: String): Int {
        return prefs.getInt("last_index_$screenType", -1)
    }

    fun saveRotationMode(mode: String) {
        prefs.edit().putString("rotation_mode", mode).apply()
    }

    fun getRotationMode(): String {
        return prefs.getString("rotation_mode", "sequential") ?: "sequential"
    }

    fun saveChangeOnUnlock(enabled: Boolean) {
        prefs.edit().putBoolean("change_on_unlock", enabled).apply()
    }

    fun getChangeOnUnlock(): Boolean {
        return prefs.getBoolean("change_on_unlock", false)
    }
}
