package com.chartdesk.app.data.drawings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `encode and decode keep points and text`() {
        val drawing = DrawingDto(
            id = "d1",
            type = "trendline",
            points = listOf(DrawingPoint(1_700_000_000, 100.5), DrawingPoint(1_700_000_600, 101.25)),
            locked = true,
            text = "breakout",
        )
        val encoded = DrawingCodec.encode(json, drawing)
        val (points, text) = DrawingCodec.decode(json, encoded)
        assertEquals(2, points.size)
        assertEquals(1_700_000_000L, points[0].time)
        assertEquals(101.25, points[1].price, 0.0001)
        assertEquals("breakout", text)
    }

    @Test
    fun `horizontal lines keep a null time`() {
        val encoded = DrawingCodec.encode(
            json,
            DrawingDto("h1", "hline", listOf(DrawingPoint(null, 42.0))),
        )
        val (points, _) = DrawingCodec.decode(json, encoded)
        assertNull(points[0].time)
        assertEquals(42.0, points[0].price, 0.0001)
    }

    @Test
    fun `bridge payload parses into drawings`() {
        val payload = json.parseToJsonElement(
            """
            {"list":[
              {"id":"a","type":"ray","locked":false,"points":[{"time":1700000000,"price":10},{"time":1700000300,"price":12}]},
              {"id":"b","type":"text","locked":true,"text":"hi","points":[{"time":1700000000,"price":11}]}
            ]}
            """.trimIndent(),
        ).jsonObject
        val parsed = DrawingCodec.parseBridgeList(json, payload)
        assertEquals(2, parsed.size)
        assertEquals("ray", parsed[0].type)
        assertEquals(2, parsed[0].points.size)
        assertEquals("hi", parsed[1].text)
        assertTrue(parsed[1].locked)
    }

    @Test
    fun `garbage payload yields an empty list, not a crash`() {
        val payload = json.parseToJsonElement("""{"list":"nope"}""").jsonObject
        assertEquals(0, DrawingCodec.parseBridgeList(json, payload).size)
        assertEquals(0, DrawingCodec.decode(json, "not json").first.size)
    }
}
