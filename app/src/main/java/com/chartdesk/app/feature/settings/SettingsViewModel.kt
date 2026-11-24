package com.chartdesk.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chartdesk.app.data.settings.AppSettings
import com.chartdesk.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setThemeMode(mode: String) = viewModelScope.launch { repository.setThemeMode(mode) }

    fun setColorBlind(enabled: Boolean) = viewModelScope.launch { repository.setColorBlind(enabled) }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHaptics(enabled) }

    fun setGrid(enabled: Boolean) = viewModelScope.launch { repository.setGrid(enabled) }

    fun setCrosshair(enabled: Boolean) = viewModelScope.launch { repository.setCrosshair(enabled) }

    fun setWatermark(enabled: Boolean) = viewModelScope.launch { repository.setWatermark(enabled) }

    fun setDefaultTimeframe(tf: String) = viewModelScope.launch { repository.setDefaultTimeframe(tf) }

    fun setDefaultChartType(type: String) = viewModelScope.launch { repository.setDefaultChartType(type) }

    fun setPriceScale(mode: String) = viewModelScope.launch { repository.setPriceScale(mode) }
}
