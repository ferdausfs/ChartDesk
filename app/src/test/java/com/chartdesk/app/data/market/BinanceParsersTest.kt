package com.chartdesk.app.data.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinanceParsersTest {

    @Test
    fun `klines parse strings, convert ms to seconds and sort`() {
        val raw = """
            [
              [1700000060000,"101.5","103.0","101.0","102.5","12.5",1700000119999,"0",10,"0","0","0"],
              [1700000000000,"100.0","102.0","99.5","101.5","10.0",1700000059999,"0",10,"0","0","0"]
            ]
        """.trimIndent()
        val candles = BinanceParsers.parseKlines(raw)
        assertEquals(2, candles.size)
        assertEquals(1_700_000_000L, candles[0].time)
        assertEquals(100.0, candles[0].open, 0.0001)
        assertEquals(102.5, candles[1].close, 0.0001)
        assertEquals(12.5, candles[1].volume, 0.0001)
    }

    @Test
    fun `klines skip malformed rows instead of throwing`() {
        val raw = """[[1700000000000,"bad","1","1","1","1"],[1700000060000,"1","2","0.5","1.5","3"]]"""
        val candles = BinanceParsers.parseKlines(raw)
        assertEquals(1, candles.size)
        assertEquals(1.5, candles[0].close, 0.0001)
    }

    @Test
    fun `kline event exposes closed flag`() {
        val raw = """{"e":"kline","k":{"t":1700000000000,"o":"100","h":"101","l":"99","c":"100.5","v":"5","x":true}}"""
        val parsed = BinanceParsers.parseKlineEvent(raw)
        assertTrue(parsed != null)
        assertEquals(true, parsed!!.second)
        assertEquals(100.5, parsed.first.close, 0.0001)
    }

    @Test
    fun `kline event without k is ignored`() {
        assertEquals(null, BinanceParsers.parseKlineEvent("""{"e":"ping"}"""))
    }

    @Test
    fun `exchange info filters non-trading and non-quote assets`() {
        val raw = """
            {"symbols":[
              {"symbol":"BTCUSDT","status":"TRADING","baseAsset":"BTC","quoteAsset":"USDT","filters":[{"tickSize":"0.01000000"}]},
              {"symbol":"OLDUSDT","status":"BREAK","baseAsset":"OLD","quoteAsset":"USDT","filters":[]},
              {"symbol":"ETHBTC","status":"TRADING","baseAsset":"ETH","quoteAsset":"BTC","filters":[{"tickSize":"0.00000100"}]},
              {"symbol":"ABCXYZ","status":"TRADING","baseAsset":"ABC","quoteAsset":"XYZ","filters":[]}
            ]}
        """.trimIndent()
        val symbols = BinanceParsers.parseExchangeInfo(raw)
        assertEquals(listOf("BTCUSDT", "ETHBTC"), symbols.map { it.symbol })
        assertEquals(2, symbols.first { it.symbol == "BTCUSDT" }.pricePrecision)
        assertEquals(6, symbols.first { it.symbol == "ETHBTC" }.pricePrecision)
    }

    @Test
    fun `tickers parse change and volume`() {
        val raw = """[{"symbol":"BTCUSDT","lastPrice":"67400.10","priceChangePercent":"1.25","quoteVolume":"999000.5"}]"""
        val tickers = BinanceParsers.parseTickers(raw)
        assertEquals(1, tickers.size)
        assertEquals(67_400.10, tickers[0].lastPrice, 0.001)
        assertEquals(1.25, tickers[0].changePercent, 0.001)
    }

    @Test
    fun `tick size precision is derived`() {
        assertEquals(0, BinanceParsers.precisionFromTick(1.0))
        assertEquals(2, BinanceParsers.precisionFromTick(0.01))
        assertEquals(5, BinanceParsers.precisionFromTick(0.00001))
        assertEquals(2, BinanceParsers.precisionFromTick(null))
    }

    @Test
    fun `retry backoff grows and caps`() {
        assertEquals(0, RetryPolicy.backoffMillis(0))
        assertEquals(800, RetryPolicy.backoffMillis(1))
        assertEquals(1_600, RetryPolicy.backoffMillis(2))
        assertEquals(6_400, RetryPolicy.backoffMillis(4))
        assertEquals(30_000, RetryPolicy.backoffMillis(20))
    }

    @Test
    fun `timeframe mapping falls back safely`() {
        assertEquals("4h", Timeframes.binance("4h"))
        assertEquals("1m", Timeframes.binance("7m"))
        assertEquals(9, Timeframes.all.size)
    }
}
