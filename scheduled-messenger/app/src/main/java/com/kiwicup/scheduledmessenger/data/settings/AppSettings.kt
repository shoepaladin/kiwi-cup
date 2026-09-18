package com.kiwicup.scheduledmessenger.data.settings

import com.kiwicup.scheduledmessenger.core.ThemeColors
import com.kiwicup.scheduledmessenger.core.ThemeMode

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Use Android 12+ wallpaper-derived colors instead of [seedColor]. */
    val useDynamicColor: Boolean = false,
    val seedColor: Int = ThemeColors.defaultSeed,
    /** Default bubble colors; null means "from the theme". Per-conversation styles override these. */
    val incomingBubbleColor: Int? = null,
    val outgoingBubbleColor: Int? = null
)
