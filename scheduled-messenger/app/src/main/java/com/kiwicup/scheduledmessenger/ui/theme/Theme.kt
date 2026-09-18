package com.kiwicup.scheduledmessenger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kiwicup.scheduledmessenger.core.ThemeColors
import com.kiwicup.scheduledmessenger.core.ThemeMode
import com.kiwicup.scheduledmessenger.data.settings.AppSettings

/** Builds a Material 3 scheme from the user's seed color, or from the wallpaper when dynamic color is on. */
fun schemeFor(settings: AppSettings, dark: Boolean): ColorScheme {
    val p = ThemeColors.derive(settings.seedColor, dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(p.primary),
        onPrimary = Color(p.onPrimary),
        primaryContainer = Color(p.primaryContainer),
        onPrimaryContainer = Color(p.onPrimaryContainer),
        secondaryContainer = Color(p.secondaryContainer),
        onSecondaryContainer = Color(p.onSecondaryContainer),
        surfaceTint = Color(p.surfaceTint)
    )
}

@Composable
fun ScheduledMessengerTheme(
    settings: AppSettings = AppSettings(),
    darkTheme: Boolean = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    dynamicColor: Boolean = settings.useDynamicColor,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> schemeFor(settings, darkTheme)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
