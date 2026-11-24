package com.chartdesk.app.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Normalised OHLCV bar. Time is UTC seconds, price/volume are doubles. */
data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
) {
    val isUp: Boolean get() = close >= open

    fun toArray(): DoubleArray = doubleArrayOf(time.toDouble(), open, high, low, close, volume)
}

fun List<Candle>.lastPrice(): Double = lastOrNull()?.close ?: 0.0

fun List<Candle>.changePercent(): Double {
    val first = firstOrNull() ?: return 0.0
    val last = lastOrNull() ?: return 0.0
    if (first.open == 0.0) return 0.0
    return (last.close - first.open) / first.open * 100.0
}

fun pricePrecision(price: Double): Int = when {
    price <= 0.0 -> 2
    price >= 10_000 -> 1
    price >= 100 -> 2
    price >= 1 -> 3
    price >= 0.01 -> 5
    else -> 8
}

/** Bucket size in seconds for a UI timeframe label. */
fun timeframeSeconds(tf: String): Long = when (tf) {
    "1m" -> 60L
    "5m" -> 300L
    "15m" -> 900L
    "30m" -> 1_800L
    "1h" -> 3_600L
    "4h" -> 14_400L
    "1d" -> 86_400L
    "1w" -> 604_800L
    "1M" -> 2_592_000L
    else -> 60L
}

/** Merge a live bar into a sorted series without mutating the original list. */
fun upsertCandle(candles: List<Candle>, candle: Candle): List<Candle> {
    if (candles.isEmpty() || candle.time > candles.last().time) return candles + candle
    val last = candles.last()
    if (candle.time == last.time) return candles.dropLast(1) + candle
    val idx = candles.indexOfLast { it.time == candle.time }
    if (idx >= 0) return candles.toMutableList().also { it[idx] = candle }
    return candles
}

/** Aggregate lower timeframe bars into fixed buckets (used by the offline cache and replay). */
fun aggregateCandles(candles: List<Candle>, bucketSeconds: Long): List<Candle> {
    if (candles.isEmpty() || bucketSeconds <= 0) return emptyList()
    val out = ArrayList<Candle>(candles.size / 4 + 1)
    var bucketStart = Long.MIN_VALUE
    var open = 0.0
    var high = 0.0
    var low = 0.0
    var close = 0.0
    var volume = 0.0
    for (c in candles) {
        val start = Math.floorDiv(c.time, bucketSeconds) * bucketSeconds
        if (start != bucketStart) {
            if (bucketStart != Long.MIN_VALUE) {
                out += Candle(bucketStart, open, high, low, close, volume)
            }
            bucketStart = start
            open = c.open
            high = c.high
            low = c.low
            close = c.close
            volume = c.volume
        } else {
            high = maxOf(high, c.high)
            low = minOf(low, c.low)
            close = c.close
            volume += c.volume
        }
    }
    if (bucketStart != Long.MIN_VALUE) out += Candle(bucketStart, open, high, low, close, volume)
    return out
}

/** Format a price for the native header. Kept here so JS and Kotlin agree on rounding. */
fun formatPrice(price: Double, precision: Int = pricePrecision(price)): String {
    if (price.isNaN() || price.isInfinite()) return "—"
    val scale = Math.pow(10.0, precision.toDouble())
    val rounded = (price * scale).roundToLong() / scale
    return String.format(Locale.US, "%.${precision}f", rounded)
}

fun formatSignedPercent(value: Double): String {
    if (abs(value) < 0.005) return "0.00%"
    val sign = if (value > 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.2f", value)}%"
}
