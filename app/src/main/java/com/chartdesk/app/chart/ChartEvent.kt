package com.chartdesk.app.chart

/** Events the chart JS layer pushes back to Kotlin. */
sealed interface ChartEvent {
    data object Ready : ChartEvent

    data class DataApplied(val count: Int, val symbol: String, val timeframe: String) : ChartEvent

    data class Crosshair(
        val time: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
    ) : ChartEvent

    data class Haptic(val kind: String) : ChartEvent

    data class ChartError(val message: String, val command: String?) : ChartEvent

    data class Ack(val id: String) : ChartEvent

    data class Unknown(val type: String) : ChartEvent
}
