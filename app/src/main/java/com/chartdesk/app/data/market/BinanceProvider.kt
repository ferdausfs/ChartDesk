package com.chartdesk.app.data.market

import com.chartdesk.app.data.Candle
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class BinanceProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : DataProvider {

    override val id: String = "binance"

    // data-api.binance.vision is Binance's market-data-only mirror; it is reachable in more regions.
    private val restBases = listOf("https://data-api.binance.vision", "https://api.binance.com")
    private val wsBases = listOf("wss://data-stream.binance.vision/ws", "wss://stream.binance.com:9443/ws")

    private var symbolCache: List<SymbolInfo> = emptyList()

    override suspend fun searchSymbols(query: String): List<SymbolInfo> {
        val cached = symbolCache
        val source = if (cached.isNotEmpty()) cached else fetchExchangeInfo().also { symbolCache = it }
        val q = query.trim().uppercase()
        if (q.isEmpty()) return source.take(40)
        return source.asSequence()
            .filter { it.symbol.contains(q) || it.baseAsset.contains(q) }
            .take(40)
            .toList()
    }

    private suspend fun <T> rest(path: String, parse: (String) -> T): T {
        var last: Throwable? = null
        for (base in restBases) {
            try {
                val request = Request.Builder().url("$base$path").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    return parse(response.body?.string().orEmpty())
                }
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IOException("No Binance endpoint reachable")
    }

    private suspend fun fetchExchangeInfo(): List<SymbolInfo> =
        rest("/api/v3/exchangeInfo?permissions=SPOT") { BinanceParsers.parseExchangeInfo(it) }

    override suspend fun candles(symbol: String, timeframe: String, limit: Int): List<Candle> =
        rest(
            "/api/v3/klines?symbol=${symbol.uppercase()}&interval=${Timeframes.binance(timeframe)}&limit=${limit.coerceIn(20, 1000)}",
        ) { BinanceParsers.parseKlines(it) }

    override suspend fun tickers(symbols: List<String>): List<Ticker> {
        if (symbols.isEmpty()) return emptyList()
        val encoded = symbols.joinToString("%22,%22", prefix = "%5B%22", postfix = "%22%5D")
        return rest("/api/v3/ticker/24hr?symbols=$encoded") { BinanceParsers.parseTickers(it) }
    }

    override fun stream(symbol: String, timeframe: String): Flow<MarketEvent> = channelFlow {
        var attempt = 0
        var everConnected = false
        while (isActive) {
            send(MarketEvent.Status(if (everConnected) ConnectionState.Reconnecting else ConnectionState.Connecting, attempt))
            try {
                connectOnce(
                    base = wsBases[attempt % wsBases.size],
                    symbol = symbol.lowercase(),
                    interval = Timeframes.binance(timeframe),
                ) { candle, closed ->
                    trySend(MarketEvent.Bar(candle, closed))
                }
                attempt = 0
                everConnected = true
                send(MarketEvent.Status(ConnectionState.Live, 0))
            } catch (t: Throwable) {
                attempt += 1
                send(MarketEvent.Status(ConnectionState.Offline, attempt))
            }
            if (!isActive) break
            delay(RetryPolicy.backoffMillis(attempt))
        }
    }

    private suspend fun connectOnce(
        base: String,
        symbol: String,
        interval: String,
        onBar: (Candle, Boolean) -> Unit,
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val request = Request.Builder().url("$base/${symbol}@kline_$interval").build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Binance closes idle sockets after 24h; nothing to send, just stay open.
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = try {
                    BinanceParsers.parseKlineEvent(text)
                } catch (t: Throwable) {
                    null
                }
                if (parsed != null) onBar(parsed.first, parsed.second)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (cont.isActive) cont.resumeWithException(IOException(t.message ?: "socket failure", t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
        val socket = client.newWebSocket(request, listener)
        cont.invokeOnCancellation {
            runCatching { socket.close(1000, "client closed") }
        }
    }
}

/** Reconnect policy, pure and testable. */
object RetryPolicy {
    fun backoffMillis(attempt: Int, base: Long = 800, cap: Long = 30_000): Long {
        if (attempt <= 0) return 0
        var value = base
        repeat((attempt - 1).coerceAtMost(6)) { value *= 2 }
        return value.coerceAtMost(cap)
    }
}
