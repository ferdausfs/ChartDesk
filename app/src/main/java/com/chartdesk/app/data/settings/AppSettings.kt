package com.chartdesk.app.data.settings

data class AppSettings(
    val themeMode: String = "dark",
    val colorBlind: Boolean = false,
    val haptics: Boolean = true,
    val showGrid: Boolean = true,
    val showCrosshair: Boolean = true,
    val showWatermark: Boolean = true,
    val defaultTimeframe: String = "1m",
    val defaultChartType: String = "candle",
    val priceScale: String = "normal",
) {
    companion object {
        val ThemeModes = listOf("dark", "light", "system")
        val ChartTypes = listOf("candle", "heikin", "line", "area", "bar")
    }
}
