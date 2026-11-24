package com.chartdesk.app.chart

import com.chartdesk.app.data.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ready event parses`() {
        assertEquals(ChartEvent.Ready, ChartEventParser.parse(json, """{"type":"ready","payload":{"version":1}}"""))
    }

    @Test
    fun `crosshair event carries ohlc`() {
        val event = ChartEventParser.parse(
            json,
            """{"type":"crosshair","payload":{"time":1700000000,"open":1.0,"high":2.0,"low":0.5,"close":1.5,"volume":9.0}}""",
        )
        assertTrue(event is ChartEvent.Crosshair)
        event as ChartEvent.Crosshair
        assertEquals(1_700_000_000L, event.time)
        assertEquals(1.5, event.close, 0.0001)
        assertEquals(9.0, event.volume, 0.0001)
    }

    @Test
    fun `empty crosshair is ignored`() {
        val event = ChartEventParser.parse(json, """{"type":"crosshair","payload":{}}""")
        assertTrue(event is ChartEvent.Unknown)
    }

    @Test
    fun `malformed payload becomes a chart error, not a crash`() {
        val event = ChartEventParser.parse(json, "{not json")
        assertTrue(event is ChartEvent.ChartError)
    }

    @Test
    fun `setCandles encodes type, precision and arrays`() {
        val command = ChartCommand.SetCandles(
            symbol = "BTCUSDT",
            timeframe = "1m",
            candles = listOf(Candle(60, 1.0, 2.0, 0.5, 1.5, 10.0)),
            precision = 2,
        )
        val root = json.parseToJsonElement(command.encode(json)).jsonObject
        assertEquals("setCandles", root["type"]?.jsonPrimitive?.content)
        val payload = root["payload"]!!.jsonObject
        assertEquals("BTCUSDT", payload["symbol"]?.jsonPrimitive?.content)
        assertEquals(2, payload["precision"]?.jsonPrimitive?.content?.toInt())
        val rows = payload["candles"]!!.jsonArray
        assertEquals(1, rows.size)
        assertEquals(6, rows[0].jsonArray.size)
        assertEquals(60L, rows[0].jsonArray[0].jsonPrimitive.content.toLong())
    }

    @Test
    fun `init encodes theme and chart type`() {
        val command = ChartCommand.Init(dark = false, colorBlind = true, symbol = "ETHUSDT", timeframe = "5m")
        val payload = json.parseToJsonElement(command.encode(json)).jsonObject["payload"]!!.jsonObject
        assertEquals("ETHUSDT", payload["symbol"]?.jsonPrimitive?.content)
        assertEquals("5m", payload["timeframe"]?.jsonPrimitive?.content)
        assertEquals(false, payload["theme"]!!.jsonObject["dark"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `unknown commands are reported, not thrown`() {
        val event = ChartEventParser.parse(json, """{"type":"mystery","payload":{}}""")
        assertEquals(ChartEvent.Unknown("mystery"), event)
    }
}
