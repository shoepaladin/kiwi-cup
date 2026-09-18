package com.kiwicup.scheduledmessenger.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kiwicup.scheduledmessenger.core.ThemeColors
import com.kiwicup.scheduledmessenger.core.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App-wide look and feel, persisted in Preferences DataStore. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            useDynamicColor = prefs[KEY_DYNAMIC] ?: false,
            seedColor = prefs[KEY_SEED] ?: ThemeColors.defaultSeed,
            incomingBubbleColor = prefs[KEY_INCOMING],
            outgoingBubbleColor = prefs[KEY_OUTGOING]
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[KEY_DYNAMIC] = enabled }
    suspend fun setSeedColor(argb: Int) = dataStore.edit { it[KEY_SEED] = argb }

    suspend fun setBubbleColors(incoming: Int?, outgoing: Int?) = dataStore.edit { prefs ->
        if (incoming == null) prefs.remove(KEY_INCOMING) else prefs[KEY_INCOMING] = incoming
        if (outgoing == null) prefs.remove(KEY_OUTGOING) else prefs[KEY_OUTGOING] = outgoing
    }

    suspend fun reset() = dataStore.edit { it.clear() }

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_SEED = intPreferencesKey("seed_color")
        val KEY_INCOMING = intPreferencesKey("incoming_bubble")
        val KEY_OUTGOING = intPreferencesKey("outgoing_bubble")
    }
}
