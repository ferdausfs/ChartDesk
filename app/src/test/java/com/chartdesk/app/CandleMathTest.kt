package com.chartdesk.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandleMathTest {

    private fun bar(time: Long, open: Double, close: Double, volume: Double = 1.0) = Candle(
        time = time,
        open = open,
        high = maxOf(open, close) + 1.0,
        low = minOf(open, close) - 1.0,
        close = close,
        volume = volume,
    )

    @Test
    fun `price precision follows magnitude`() {
        assertEquals(1, pricePrecision(67_400.0))
        assertEquals(2, pricePrecision(172.5))
        assertEquals(3, pricePrecision(1.234))
        assertEquals(5, pricePrecision(0.0623))
        assertEquals(8, pricePrecision(0.00012345))
    }

    @Test
    fun `upsert replaces the last bucket`() {
        val series = listOf(bar(60, 10.0, 11.0), bar(120, 11.0, 12.0))
        val updated = upsertCandle(series, bar(120, 11.0, 13.0, 5.0))
        assertEquals(2, updated.size)
        assertEquals(13.0, updated.last().close, 0.0001)
        assertEquals(5.0, updated.last().volume, 0.0001)
    }

    @Test
    fun `upsert appends a newer bucket`() {
        val series = listOf(bar(60, 10.0, 11.0))
        val updated = upsertCandle(series, bar(120, 11.0, 12.0))
        assertEquals(2, updated.size)
        assertEquals(120L, updated.last().time)
    }

    @Test
    fun `aggregate merges five one-minute bars into one five-minute bar`() {
        val series = (0 until 5).map { i -> bar(60L * i, 100.0 + i, 101.0 + i, 2.0) }
        val out = aggregateCandles(series, 300)
        assertEquals(1, out.size)
        assertEquals(0L, out[0].time)
        assertEquals(100.0, out[0].open, 0.0001)
        assertEquals(105.0, out[0].close, 0.0001)
        assertEquals(10.0, out[0].volume, 0.0001)
        assertTrue(out[0].high >= out[0].close)
    }

    @Test
    fun `aggregate aligns buckets to epoch`() {
        val series = listOf(bar(301, 1.0, 2.0), bar(359, 2.0, 3.0), bar(600, 3.0, 4.0))
        val out = aggregateCandles(series, 300)
        assertEquals(2, out.size)
        assertEquals(300L, out[0].time)
        assertEquals(600L, out[1].time)
    }

    @Test
    fun `change percent uses first open and last close`() {
        val series = listOf(bar(60, 100.0, 105.0), bar(120, 105.0, 110.0))
        assertEquals(10.0, series.changePercent(), 0.0001)
    }

    @Test
    fun `format price respects precision`() {
        assertEquals("67400.0", formatPrice(67_400.0, 1))
        assertEquals("0.62310", formatPrice(0.6231, 5))
        assertEquals("+1.25%", formatSignedPercent(1.25))
        assertEquals("-0.50%", formatSignedPercent(-0.5))
    }
}
