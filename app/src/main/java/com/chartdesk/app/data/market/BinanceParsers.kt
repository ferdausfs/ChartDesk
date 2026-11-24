package com.chartdesk.app.data.market

import com.chartdesk.app.data.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Binance JSON parsing kept pure so it can be tested without a socket or network.
 * Binance sends numbers as strings; every field is parsed defensively.
 */
object BinanceParsers {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseKlines(raw: String): List<Candle> {
        val rows = json.parseToJsonElement(raw).jsonArray
        val out = ArrayList<Candle>(rows.size)
        for (row in rows) {
            val a = row.jsonArray
            if (a.size < 6) continue
            val candle = Candle(
                time = a[0].jsonPrimitive.content.toLongOrNull()?.div(1000) ?: continue,
                open = a[1].jsonPrimitive.content.toDoubleOrNull() ?: continue,
                high = a[2].jsonPrimitive.content.toDoubleOrNull() ?: continue,
                low = a[3].jsonPrimitive.content.toDoubleOrNull() ?: continue,
                close = a[4].jsonPrimitive.content.toDoubleOrNull() ?: continue,
                volume = a[5].jsonPrimitive.content.toDoubleOrNull() ?: 0.0,
            )
            out += candle
        }
        return out.sortedBy { it.time }
    }

    /** Returns the parsed bar and whether the exchange marked it closed. */
    fun parseKlineEvent(raw: String): Pair<Candle, Boolean>? {
        val root = json.parseToJsonElement(raw).jsonObject
        val k = root["k"]?.jsonObject ?: return null
        val candle = Candle(
            time = (k["t"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null) / 1000,
            open = k["o"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null,
            high = k["h"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null,
            low = k["l"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null,
            close = k["c"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null,
            volume = k["v"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
        )
        val closed = k["x"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        return candle to closed
    }

    fun parseExchangeInfo(raw: String): List<SymbolInfo> {
        val root = json.parseToJsonElement(raw).jsonObject
        val symbols = root["symbols"] as? JsonArray ?: return emptyList()
        val out = ArrayList<SymbolInfo>()
        for (el in symbols) {
            val o = el.jsonObject
            val status = o["status"]?.jsonPrimitive?.content ?: continue
            if (status != "TRADING") continue
            val symbol = o["symbol"]?.jsonPrimitive?.content ?: continue
            val quote = o["quoteAsset"]?.jsonPrimitive?.content ?: continue
            if (quote != "USDT" && quote != "USDC" && quote != "BTC" && quote != "FDUSD") continue
            val base = o["baseAsset"]?.jsonPrimitive?.content ?: symbol
            val tickSize = o["filters"]
                ?.jsonArray
                ?.mapNotNull { it.jsonObject["tickSize"]?.jsonPrimitive?.content?.toDoubleOrNull() }
                ?.firstOrNull { it > 0 }
            out += SymbolInfo(
                symbol = symbol,
                baseAsset = base,
                quoteAsset = quote,
                pricePrecision = precisionFromTick(tickSize),
            )
        }
        return out.sortedBy { it.symbol }
    }

    fun parseTickers(raw: String): List<Ticker> {
        val rows = json.parseToJsonElement(raw).jsonArray
        return rows.mapNotNull { el ->
            val o = el.jsonObject
            val symbol = o["symbol"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val last = o["lastPrice"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val change = o["priceChangePercent"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val quoteVolume = o["quoteVolume"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            Ticker(symbol, last, change, quoteVolume)
        }
    }

    fun precisionFromTick(tickSize: Double?): Int {
        if (tickSize == null || tickSize <= 0.0) return 2
        var decimals = 0
        var value = tickSize
        while (value < 1.0 && decimals < 8) {
            value *= 10
            decimals++
        }
        return decimals
    }
}

/** Timeframe labels used by the UI and their Binance interval codes (identical strings). */
object Timeframes {
    val all = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w", "1M")
    fun binance(tf: String): String = if (tf in all) tf else "1m"
}
