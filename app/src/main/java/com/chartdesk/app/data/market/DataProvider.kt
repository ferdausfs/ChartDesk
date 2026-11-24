package com.chartdesk.app.data.market

import com.chartdesk.app.data.Candle

data class SymbolInfo(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val pricePrecision: Int = 2,
)

data class Ticker(
    val symbol: String,
    val lastPrice: Double,
    val changePercent: Double,
    val quoteVolume: Double,
)

enum class ConnectionState { Connecting, Live, Reconnecting, Offline }

sealed interface MarketEvent {
    data class Bar(val candle: Candle, val closed: Boolean) : MarketEvent
    data class Status(val state: ConnectionState, val attempt: Int = 0) : MarketEvent
}

/** One swap point for every market-data source. Crypto first, forex behind the same contract. */
interface DataProvider {
    val id: String
    suspend fun searchSymbols(query: String): List<SymbolInfo>
    suspend fun candles(symbol: String, timeframe: String, limit: Int = 500): List<Candle>
    suspend fun tickers(symbols: List<String>): List<Ticker>
    fun stream(symbol: String, timeframe: String): kotlinx.coroutines.flow.Flow<MarketEvent>
}
