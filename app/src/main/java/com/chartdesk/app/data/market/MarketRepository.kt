package com.chartdesk.app.data.market

import com.chartdesk.app.data.Candle
import com.chartdesk.app.data.db.CandleEntity
import com.chartdesk.app.data.db.CandleDao
import com.chartdesk.app.data.db.SymbolDao
import com.chartdesk.app.data.db.SymbolEntity
import com.chartdesk.app.data.timeframeSeconds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

data class CandleSnapshot(
    val candles: List<Candle>,
    val fromCache: Boolean,
)

@Singleton
class MarketRepository @Inject constructor(
    private val provider: DataProvider,
    private val candleDao: CandleDao,
    private val symbolDao: SymbolDao,
) {

    /** Network first, cache fallback. Throws only when both paths are empty. */
    suspend fun loadCandles(symbol: String, timeframe: String, limit: Int = 500): CandleSnapshot {
        val networkError = try {
            val remote = provider.candles(symbol, timeframe, limit)
            if (remote.isEmpty()) throw IllegalStateException("Empty candle response")
            cache(symbol, timeframe, remote, limit)
            return CandleSnapshot(remote, fromCache = false)
        } catch (t: Throwable) {
            t
        }
        val cached = candleDao.latest(symbol, timeframe, limit).map { it.toCandle() }.sortedBy { it.time }
        if (cached.isNotEmpty()) return CandleSnapshot(cached, fromCache = true)
        throw networkError
    }

    private suspend fun cache(symbol: String, timeframe: String, candles: List<Candle>, limit: Int) {
        candleDao.upsert(candles.map { CandleEntity.from(symbol, timeframe, it) })
        val step = timeframeSeconds(timeframe)
        val cutoff = candles.last().time - step * limit * 3
        candleDao.prune(symbol, timeframe, cutoff)
    }

    suspend fun searchSymbols(query: String): List<SymbolInfo> {
        val remote = runCatching { provider.searchSymbols(query) }.getOrNull()
        if (!remote.isNullOrEmpty()) {
            val now = System.currentTimeMillis()
            symbolDao.upsert(remote.map {
                SymbolEntity(it.symbol, it.baseAsset, it.quoteAsset, it.pricePrecision, now)
            })
            return remote
        }
        val q = query.trim().uppercase()
        val cached = if (q.isEmpty()) symbolDao.all() else symbolDao.search(q)
        return cached.map { SymbolInfo(it.symbol, it.baseAsset, it.quoteAsset, it.pricePrecision) }
    }

    suspend fun tickers(symbols: List<String>): List<Ticker> =
        runCatching { provider.tickers(symbols) }.getOrDefault(emptyList())

    fun live(symbol: String, timeframe: String): Flow<MarketEvent> = provider.stream(symbol, timeframe)
}
