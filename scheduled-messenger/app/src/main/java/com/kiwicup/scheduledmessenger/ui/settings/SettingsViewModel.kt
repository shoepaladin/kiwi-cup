package com.kiwicup.scheduledmessenger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.core.ThemeMode
import com.kiwicup.scheduledmessenger.data.settings.AppSettings
import com.kiwicup.scheduledmessenger.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setSeedColor(argb: Int) = viewModelScope.launch { repository.setSeedColor(argb) }
    fun setBubbleColors(incoming: Int?, outgoing: Int?) = viewModelScope.launch { repository.setBubbleColors(incoming, outgoing) }
    fun reset() = viewModelScope.launch { repository.reset() }
}
