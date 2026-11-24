package com.chartdesk.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cross-tab request: "open the chart tab on this symbol". Consumed once by the chart. */
@Singleton
class ChartRequestBus @Inject constructor() {
    private val _symbol = MutableStateFlow<String?>(null)
    val symbol: StateFlow<String?> = _symbol.asStateFlow()

    fun request(symbol: String) {
        _symbol.value = symbol
    }

    fun consume() {
        _symbol.value = null
    }
}
