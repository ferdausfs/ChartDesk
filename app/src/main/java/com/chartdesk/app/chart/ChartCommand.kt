package com.chartdesk.app.chart

import com.chartdesk.app.data.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Commands Kotlin can push into the chart WebView. Encoded as `{type, payload}`. */
sealed interface ChartCommand {
    val type: String
    fun payload(): JsonObject
    fun encode(json: Json): String = buildJsonObject {
        put("type", type)
        put("payload", payload())
    }.toString()

    data class Init(
        val dark: Boolean = true,
        val colorBlind: Boolean = false,
        val chartType: String = "candle",
        val priceScale: String = "normal",
        val symbol: String,
        val timeframe: String,
    ) : ChartCommand {
        override val type = "init"
        override fun payload(): JsonObject = buildJsonObject {
            put("theme", buildJsonObject {
                put("dark", dark)
                put("colorBlind", colorBlind)
            })
            put("type", chartType)
            put("scale", priceScale)
            put("symbol", symbol)
            put("timeframe", timeframe)
        }
    }

    data class SetCandles(
        val symbol: String,
        val timeframe: String,
        val candles: List<Candle>,
        val precision: Int,
        val fit: Boolean = true,
    ) : ChartCommand {
        override val type = "setCandles"
        override fun payload(): JsonObject = buildJsonObject {
            put("symbol", symbol)
            put("timeframe", timeframe)
            put("precision", precision)
            put("fit", fit)
            put("candles", JsonArray(candles.map { c ->
                JsonArray(listOf(
                    kotlinx.serialization.json.JsonPrimitive(c.time),
                    kotlinx.serialization.json.JsonPrimitive(c.open),
                    kotlinx.serialization.json.JsonPrimitive(c.high),
                    kotlinx.serialization.json.JsonPrimitive(c.low),
                    kotlinx.serialization.json.JsonPrimitive(c.close),
                    kotlinx.serialization.json.JsonPrimitive(c.volume),
                ))
            }))
        }
    }

    data class UpdateCandle(val candle: Candle) : ChartCommand {
        override val type = "updateCandle"
        override fun payload(): JsonObject = buildJsonObject {
            put("candle", JsonArray(listOf(
                kotlinx.serialization.json.JsonPrimitive(candle.time),
                kotlinx.serialization.json.JsonPrimitive(candle.open),
                kotlinx.serialization.json.JsonPrimitive(candle.high),
                kotlinx.serialization.json.JsonPrimitive(candle.low),
                kotlinx.serialization.json.JsonPrimitive(candle.close),
                kotlinx.serialization.json.JsonPrimitive(candle.volume),
            )))
        }
    }

    data class SetChartType(val chartType: String) : ChartCommand {
        override val type = "setChartType"
        override fun payload(): JsonObject = buildJsonObject { put("type", chartType) }
    }

    data class SetTheme(val dark: Boolean, val colorBlind: Boolean) : ChartCommand {
        override val type = "setTheme"
        override fun payload(): JsonObject = buildJsonObject {
            put("dark", dark)
            put("colorBlind", colorBlind)
        }
    }

    data class SetPriceScale(val mode: String) : ChartCommand {
        override val type = "setPriceScale"
        override fun payload(): JsonObject = buildJsonObject { put("mode", mode) }
    }

    data class SetTimeframe(val timeframe: String) : ChartCommand {
        override val type = "setTimeframe"
        override fun payload(): JsonObject = buildJsonObject { put("timeframe", timeframe) }
    }

    data class SetSymbol(val symbol: String) : ChartCommand {
        override val type = "setSymbol"
        override fun payload(): JsonObject = buildJsonObject { put("symbol", symbol) }
    }

    data class IndicatorDto(
        val uid: String,
        val type: String,
        val params: Map<String, Double>,
        val visible: Boolean,
    )

    data class SetIndicators(val list: List<IndicatorDto>) : ChartCommand {
        override val type = "setIndicators"
        override fun payload(): JsonObject = buildJsonObject {
            put("list", JsonArray(list.map { dto ->
                buildJsonObject {
                    put("uid", dto.uid)
                    put("type", dto.type)
                    put("visible", dto.visible)
                    put("params", buildJsonObject { dto.params.forEach { (key, value) -> put(key, value) } })
                }
            }))
        }
    }

    data class SetOptions(
        val grid: Boolean = true,
        val crosshair: Boolean = true,
        val watermark: Boolean = true,
    ) : ChartCommand {
        override val type = "setOptions"
        override fun payload(): JsonObject = buildJsonObject {
            put("grid", grid)
            put("crosshair", crosshair)
            put("watermark", watermark)
        }
    }

    data class ShowError(val message: String) : ChartCommand {
        override val type = "showError"
        override fun payload(): JsonObject = buildJsonObject { put("message", message) }
    }

    data object ResetView : ChartCommand {
        override val type = "resetView"
        override fun payload(): JsonObject = buildJsonObject { }
    }
}
