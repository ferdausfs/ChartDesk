package com.chartdesk.app.data.indicators

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class IndicatorConfig(
    val uid: String,
    val type: String,
    val params: Map<String, Double>,
    val visible: Boolean = true,
)

data class IndicatorParam(
    val key: String,
    val label: String,
    val default: Double,
    val integer: Boolean = true,
)

data class IndicatorSpec(
    val type: String,
    val name: String,
    val pane: String,
    val params: List<IndicatorParam>,
)

/** Mirrors assets/chart/indicators.js: same ids, same defaults. */
object IndicatorCatalog {

    val all: List<IndicatorSpec> = listOf(
        IndicatorSpec("ma", "MA", "overlay", listOf(IndicatorParam("period", "Period", 20.0))),
        IndicatorSpec("ema", "EMA", "overlay", listOf(IndicatorParam("period", "Period", 20.0))),
        IndicatorSpec("wma", "WMA", "overlay", listOf(IndicatorParam("period", "Period", 20.0))),
        IndicatorSpec(
            "bollinger", "Bollinger", "overlay",
            listOf(IndicatorParam("period", "Period", 20.0), IndicatorParam("mult", "Std dev", 2.0, integer = false)),
        ),
        IndicatorSpec("vwap", "VWAP", "overlay", emptyList()),
        IndicatorSpec(
            "ichimoku", "Ichimoku", "overlay",
            listOf(
                IndicatorParam("tenkan", "Tenkan", 9.0),
                IndicatorParam("kijun", "Kijun", 26.0),
                IndicatorParam("senkou", "Senkou", 52.0),
            ),
        ),
        IndicatorSpec(
            "supertrend", "SuperTrend", "overlay",
            listOf(IndicatorParam("period", "ATR period", 10.0), IndicatorParam("mult", "Multiplier", 3.0, integer = false)),
        ),
        IndicatorSpec("volume", "Volume", "volume", emptyList()),
        IndicatorSpec("rsi", "RSI", "separate", listOf(IndicatorParam("period", "Period", 14.0))),
        IndicatorSpec(
            "macd", "MACD", "separate",
            listOf(
                IndicatorParam("fast", "Fast", 12.0),
                IndicatorParam("slow", "Slow", 26.0),
                IndicatorParam("signal", "Signal", 9.0),
            ),
        ),
        IndicatorSpec("atr", "ATR", "separate", listOf(IndicatorParam("period", "Period", 14.0))),
        IndicatorSpec(
            "stochastic", "Stochastic", "separate",
            listOf(IndicatorParam("k", "%K", 14.0), IndicatorParam("d", "%D", 3.0)),
        ),
    )

    val byType: Map<String, IndicatorSpec> = all.associateBy { it.type }

    fun spec(type: String): IndicatorSpec? = byType[type]

    fun defaults(type: String): Map<String, Double> =
        spec(type)?.params?.associate { it.key to it.default } ?: emptyMap()

    /** Volume is on by default; everything else is opt-in. */
    fun defaultConfigs(): List<IndicatorConfig> = listOf(
        IndicatorConfig(uid = "volume", type = "volume", params = emptyMap()),
    )
}

object IndicatorCodec {
    fun encodeParams(json: Json, params: Map<String, Double>): String = json.encodeToString(params)

    fun decodeParams(json: Json, raw: String): Map<String, Double> =
        runCatching { json.decodeFromString<Map<String, Double>>(raw) }.getOrDefault(emptyMap())
}
