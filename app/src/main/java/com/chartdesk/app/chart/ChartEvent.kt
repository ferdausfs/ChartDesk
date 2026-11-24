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

    data class DrawingsChanged(val list: List<com.chartdesk.app.data.drawings.DrawingDto>) : ChartEvent

    data class DrawingSelected(val id: String?, val locked: Boolean) : ChartEvent

    data class TextRequest(val id: String) : ChartEvent

    data class Measure(val from: Double, val to: Double, val percent: Double) : ChartEvent

    data class ModeChanged(val mode: String) : ChartEvent

    data class ReplayState(
        val active: Boolean,
        val playing: Boolean,
        val index: Int,
        val total: Int,
        val speed: Double,
        val price: Double,
        val side: String?,
        val entry: Double,
        val pnl: Double,
        val pnlPercent: Double,
    ) : ChartEvent

    data class Ack(val id: String) : ChartEvent

    data class Unknown(val type: String) : ChartEvent
}
