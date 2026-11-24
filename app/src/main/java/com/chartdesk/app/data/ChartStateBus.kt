package com.chartdesk.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChartSummary(
    val symbol: String = "BTCUSDT",
    val timeframe: String = "1m",
    val chartType: String = "candle",
    val grid: Int = 1,
)

/** Current chart state exposed to other tabs (used when saving a layout). */
@Singleton
class ChartStateBus @Inject constructor() {
    private val _state = MutableStateFlow(ChartSummary())
    val state: StateFlow<ChartSummary> = _state.asStateFlow()

    fun update(summary: ChartSummary) {
        _state.value = summary
    }
}

/** Request from the More tab to apply a saved layout on the chart tab. */
@Singleton
class LayoutRequestBus @Inject constructor() {
    private val _request = MutableStateFlow<LayoutRequest?>(null)
    val request: StateFlow<LayoutRequest?> = _request.asStateFlow()

    fun apply(request: LayoutRequest) {
        _request.value = request
    }

    fun consume() {
        _request.value = null
    }
}

data class LayoutRequest(
    val symbol: String,
    val timeframe: String,
    val chartType: String,
    val grid: Int,
)
