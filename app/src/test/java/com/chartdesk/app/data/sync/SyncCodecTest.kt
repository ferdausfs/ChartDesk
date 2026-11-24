package com.chartdesk.app.data.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `payload round-trips through json`() {
        val payload = SyncPayload(
            exportedAt = 1_700_000_000_000,
            watchlists = listOf(WatchlistDto("main", "Main", 0, 1)),
            items = listOf(WatchlistItemDto("main", "BTCUSDT", 0)),
            layouts = listOf(LayoutDto("l1", "Scalp", "BTCUSDT", "1m", "candle", 4, 2)),
            scripts = listOf(ScriptDto("s1", "EMA", "plot(close)", 3, false)),
            drawings = listOf(DrawingDtoSync("BTCUSDT", "d1", "hline", "{}", true, 0, 4)),
            indicators = listOf(IndicatorDtoSync("BTCUSDT", "rsi", "rsi", "{\"period\":14}", true, 0)),
            alerts = listOf(AlertDtoSync("a1", "ETHUSDT", "above", 3000.0, true, 5, null)),
        )
        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<SyncPayload>(encoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun `missing optional fields use defaults`() {
        val decoded = json.decodeFromString<SyncPayload>("""{"version":1}""")
        assertTrue(decoded.watchlists.isEmpty())
        assertTrue(decoded.alerts.isEmpty())
        assertEquals(1, decoded.version)
    }

    @Test
    fun `import of a malformed bundle fails cleanly`() {
        val result = runCatching { json.decodeFromString<SyncPayload>("not json") }
        assertTrue(result.isFailure)
    }
}
