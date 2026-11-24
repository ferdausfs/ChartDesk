package com.chartdesk.app.chart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Pure JSON <-> [ChartEvent] mapping, kept free of Android types so it is unit-testable. */
object ChartEventParser {

    fun parse(json: Json, raw: String): ChartEvent {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val type = root["type"]?.jsonPrimitive?.content ?: return ChartEvent.Unknown("missing")
            val payload = root["payload"] as? JsonObject ?: JsonObject(emptyMap())
            fun str(key: String) = payload[key]?.jsonPrimitive?.content
            fun dbl(key: String) = payload[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            fun lng(key: String) = payload[key]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            when (type) {
                "ready" -> ChartEvent.Ready
                "dataApplied" -> ChartEvent.DataApplied(
                    count = payload["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    symbol = str("symbol") ?: "",
                    timeframe = str("timeframe") ?: "",
                )
                "crosshair" -> {
                    val time = lng("time")
                    if (time == 0L) ChartEvent.Unknown("crosshair-empty")
                    else ChartEvent.Crosshair(
                        time = time,
                        open = dbl("open"),
                        high = dbl("high"),
                        low = dbl("low"),
                        close = dbl("close"),
                        volume = dbl("volume"),
                    )
                }
                "drawingsChanged" -> ChartEvent.DrawingsChanged(
                    com.chartdesk.app.data.drawings.DrawingCodec.parseBridgeList(json, payload),
                )
                "drawingSelected" -> ChartEvent.DrawingSelected(
                    id = payload["id"]?.jsonPrimitive?.contentOrNull,
                    locked = payload["locked"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                )
                "textRequest" -> ChartEvent.TextRequest(str("id") ?: "")
                "measure" -> ChartEvent.Measure(dbl("from"), dbl("to"), dbl("pct"))
                "modeChanged" -> ChartEvent.ModeChanged(str("mode") ?: "none")
                "haptic" -> ChartEvent.Haptic(str("kind") ?: "tap")
                "error" -> ChartEvent.ChartError(str("message") ?: "chart error", str("command"))
                "ack" -> ChartEvent.Ack(str("id") ?: "")
                else -> ChartEvent.Unknown(type)
            }
        } catch (t: Throwable) {
            ChartEvent.ChartError("Bad bridge payload: ${t.message}", null)
        }
    }
}
