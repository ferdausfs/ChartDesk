package com.chartdesk.app.data.drawings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class DrawingPoint(val time: Long?, val price: Double)

data class DrawingDto(
    val id: String,
    val type: String,
    val points: List<DrawingPoint>,
    val locked: Boolean = false,
    val text: String? = null,
)

object DrawingCodec {

    fun encode(json: Json, drawing: DrawingDto): String = buildJsonObject {
        putJsonArray("points") {
            drawing.points.forEach { point ->
                add(buildJsonObject {
                    point.time?.let { put("time", it) }
                    put("price", point.price)
                })
            }
        }
        drawing.text?.let { put("text", it) }
    }.toString()

    fun decode(json: Json, raw: String): Pair<List<DrawingPoint>, String?> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList<DrawingPoint>() to null
        val points = runCatching {
            root["points"]?.jsonArray?.map { el ->
                val o = el.jsonObject
                DrawingPoint(
                    time = o["time"]?.jsonPrimitive?.content?.toLongOrNull(),
                    price = o["price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
        return points to root["text"]?.jsonPrimitive?.content
    }

    /** Accepts the bridge payload `{list:[...]}` and returns normalised drawings. */
    fun parseBridgeList(json: Json, payload: JsonObject): List<DrawingDto> {
        val list = payload["list"] as? JsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val o = element.jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val type = o["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val points = o["points"]?.jsonArray?.mapNotNull { p ->
                val po = p.jsonObject
                val price = po["price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                DrawingPoint(po["time"]?.jsonPrimitive?.content?.toLongOrNull(), price)
            } ?: emptyList()
            DrawingDto(
                id = id,
                type = type,
                points = points,
                locked = o["locked"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                text = o["text"]?.jsonPrimitive?.content,
            )
        }
    }
}
