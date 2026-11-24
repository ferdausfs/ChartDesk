package com.chartdesk.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "chartdesk_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val colorBlind = booleanPreferencesKey("color_blind")
        val haptics = booleanPreferencesKey("haptics")
        val showGrid = booleanPreferencesKey("show_grid")
        val showCrosshair = booleanPreferencesKey("show_crosshair")
        val showWatermark = booleanPreferencesKey("show_watermark")
        val defaultTimeframe = stringPreferencesKey("default_timeframe")
        val defaultChartType = stringPreferencesKey("default_chart_type")
        val priceScale = stringPreferencesKey("price_scale")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.themeMode] ?: "dark",
            colorBlind = prefs[Keys.colorBlind] ?: false,
            haptics = prefs[Keys.haptics] ?: true,
            showGrid = prefs[Keys.showGrid] ?: true,
            showCrosshair = prefs[Keys.showCrosshair] ?: true,
            showWatermark = prefs[Keys.showWatermark] ?: true,
            defaultTimeframe = prefs[Keys.defaultTimeframe] ?: "1m",
            defaultChartType = prefs[Keys.defaultChartType] ?: "candle",
            priceScale = prefs[Keys.priceScale] ?: "normal",
        )
    }

    suspend fun setThemeMode(mode: String) = edit { it[Keys.themeMode] = mode }

    suspend fun setColorBlind(enabled: Boolean) = edit { it[Keys.colorBlind] = enabled }

    suspend fun setHaptics(enabled: Boolean) = edit { it[Keys.haptics] = enabled }

    suspend fun setGrid(enabled: Boolean) = edit { it[Keys.showGrid] = enabled }

    suspend fun setCrosshair(enabled: Boolean) = edit { it[Keys.showCrosshair] = enabled }

    suspend fun setWatermark(enabled: Boolean) = edit { it[Keys.showWatermark] = enabled }

    suspend fun setDefaultTimeframe(tf: String) = edit { it[Keys.defaultTimeframe] = tf }

    suspend fun setDefaultChartType(type: String) = edit { it[Keys.defaultChartType] = type }

    suspend fun setPriceScale(mode: String) = edit { it[Keys.priceScale] = mode }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }
}
