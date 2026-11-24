package com.chartdesk.app.feature.watchlist

import com.chartdesk.app.data.market.Ticker

/** Pure row building + sorting, extracted so the UI logic is unit-testable without Android. */
object WatchlistSorting {

    fun build(
        symbols: List<String>,
        tickers: Map<String, Ticker>,
        sortMode: String,
    ): List<WatchlistRow> {
        val rows = symbols.map { symbol ->
            val ticker = tickers[symbol]
            WatchlistRow(
                symbol = symbol,
                price = ticker?.lastPrice ?: 0.0,
                changePercent = ticker?.changePercent ?: 0.0,
                quoteVolume = ticker?.quoteVolume ?: 0.0,
                hasTicker = ticker != null,
            )
        }
        return when (sortMode) {
            "change" -> rows.sortedByDescending { it.changePercent }
            "price" -> rows.sortedByDescending { it.price }
            "name" -> rows.sortedBy { it.symbol }
            else -> rows
        }
    }
}
