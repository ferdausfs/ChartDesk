# Market data

## Providers

| Provider | Status | Transport | Notes |
| --- | --- | --- | --- |
| Binance spot | live (default) | REST `/api/v3/*`, WS `stream.binance.com:9443` | public endpoints, no key |
| Forex (Yahoo) | later | REST | owner decision: crypto first, forex after the core is green |

Every source implements `DataProvider`:

```kotlin
interface DataProvider {
    val id: String
    suspend fun searchSymbols(query: String): List<SymbolInfo>
    suspend fun candles(symbol: String, timeframe: String, limit: Int): List<Candle>
    suspend fun tickers(symbols: List<String>): List<Ticker>
    fun stream(symbol: String, timeframe: String): Flow<MarketEvent>
}
```

Swapping a provider is a Hilt binding change (`MarketModule`), nothing else.

## Timeframes

`1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w, 1M` — Binance interval codes match the UI labels exactly.

## Loading path

1. `MarketRepository.loadCandles` hits REST with `limit=500`.
2. Bars are upserted into Room (`candles` table, key = symbol+timeframe+time) and old rows pruned.
3. If REST fails, the newest cached rows are returned and the UI shows the offline badge. If the cache is
   empty too, the error surfaces with a retry action — no fabricated candles.

## Live path

1. WebSocket `/<symbol>@kline_<interval>`.
2. Each event is parsed defensively; malformed frames are dropped, never crash.
3. Kotlin coalesces bars into the chart via `updateCandle` (single series update, no full re-set).
4. On disconnect: exponential backoff `800ms → 1.6s → 3.2s … capped at 30s`, status pushed to the UI
   (`LIVE / RETRY / OFFLINE`).
5. After a reconnect the last 120 bars are refetched and pushed with `fit=false`, closing any gap without
   moving the user's viewport.

## Cache size

Each (symbol, timeframe) keeps roughly three windows of 500 bars; anything older is pruned on write. That keeps
Room small (a few MB) while still giving a useful offline chart.
