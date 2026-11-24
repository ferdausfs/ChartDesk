package com.chartdesk.app.data.watchlist

import com.chartdesk.app.data.market.Ticker
import com.chartdesk.app.feature.watchlist.WatchlistSorting
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistTest {

    @Test
    fun `reorder moves a symbol forward`() {
        val result = WatchlistRepository.reorder(listOf("A", "B", "C", "D"), from = 0, to = 2)
        assertEquals(listOf("B", "C", "A", "D"), result)
    }

    @Test
    fun `reorder moves a symbol backward`() {
        val result = WatchlistRepository.reorder(listOf("A", "B", "C", "D"), from = 3, to = 1)
        assertEquals(listOf("A", "D", "B", "C"), result)
    }

    @Test
    fun `reorder ignores out-of-range indices`() {
        val original = listOf("A", "B")
        assertEquals(original, WatchlistRepository.reorder(original, 5, 0))
        assertEquals(original, WatchlistRepository.reorder(original, 0, 9))
        assertEquals(original, WatchlistRepository.reorder(original, 1, 1))
    }

    @Test
    fun `default watchlist has unique symbols`() {
        val symbols = WatchlistRepository.DEFAULT_SYMBOLS
        assertEquals(symbols.size, symbols.toSet().size)
        assertEquals("BTCUSDT", symbols.first())
    }

    @Test
    fun `sorting by change puts the biggest gainer first`() {
        val tickers = mapOf(
            "BTCUSDT" to Ticker("BTCUSDT", 100.0, 1.5, 0.0),
            "ETHUSDT" to Ticker("ETHUSDT", 50.0, 4.2, 0.0),
            "SOLUSDT" to Ticker("SOLUSDT", 20.0, -2.0, 0.0),
        )
        val rows = WatchlistSorting.build(listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"), tickers, "change")
        assertEquals(listOf("ETHUSDT", "BTCUSDT", "SOLUSDT"), rows.map { it.symbol })
    }

    @Test
    fun `sorting by price and name`() {
        val tickers = mapOf(
            "BTCUSDT" to Ticker("BTCUSDT", 100.0, 1.5, 0.0),
            "ETHUSDT" to Ticker("ETHUSDT", 500.0, 1.0, 0.0),
        )
        assertEquals(
            listOf("ETHUSDT", "BTCUSDT"),
            WatchlistSorting.build(listOf("BTCUSDT", "ETHUSDT"), tickers, "price").map { it.symbol },
        )
        assertEquals(
            listOf("BTCUSDT", "ETHUSDT"),
            WatchlistSorting.build(listOf("ETHUSDT", "BTCUSDT"), tickers, "name").map { it.symbol },
        )
    }

    @Test
    fun `missing tickers stay in place and are flagged`() {
        val rows = WatchlistSorting.build(listOf("BTCUSDT", "NEWUSDT"), mapOf("BTCUSDT" to Ticker("BTCUSDT", 10.0, 0.0, 0.0)), "default")
        assertEquals(listOf("BTCUSDT", "NEWUSDT"), rows.map { it.symbol })
        assertEquals(false, rows[1].hasTicker)
        assertEquals(0.0, rows[1].price, 0.0001)
    }
}
