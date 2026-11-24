package com.chartdesk.app.chart

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

/**
 * Owns the WebView reference and the command queue. Commands sent before the JS layer
 * reports `ready` are buffered and flushed in order.
 */
@Singleton
class ChartController @Inject constructor(private val json: Json) {

    private var webViewRef: WeakReference<WebView>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<ChartCommand>()
    private val stateCommands = LinkedHashMap<String, ChartCommand>()
    private var ready = false

    /** Commands that describe chart state and must be replayed when a fresh WebView attaches. */
    private val replayable = setOf(
        "init", "setCandles", "setTheme", "setOptions", "setIndicators", "setDrawings",
        "setSymbol", "setTimeframe", "setChartType", "setPriceScale", "setPrecision",
    )

    private val _events = MutableSharedFlow<ChartEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ChartEvent> = _events.asSharedFlow()

    fun attach(webView: WebView) {
        webViewRef = WeakReference(webView)
        ready = false
    }

    fun detach() {
        webViewRef = null
        ready = false
    }

    fun webView(): WebView? = webViewRef?.get()

    fun send(command: ChartCommand) {
        mainHandler.post {
            if (command.type in replayable) stateCommands[command.type] = command
            val view = webViewRef?.get()
            if (!ready || view == null) {
                pending.addLast(command)
                return@post
            }
            evaluate(view, command)
        }
    }

    fun isReady(): Boolean = ready

    /** Force the full state back into the page (used by the retry action). */
    fun resendState() {
        mainHandler.post {
            val view = webViewRef?.get() ?: return@post
            if (!ready) return@post
            stateCommands.values.forEach { evaluate(view, it) }
        }
    }

    fun onJsMessage(raw: String) {
        val event = parse(raw)
        if (event is ChartEvent.Ready) {
            mainHandler.post {
                ready = true
                val view = webViewRef?.get() ?: return@post
                // Replay the latest state first, then any non-state commands (updates, crosshair…).
                stateCommands.values.forEach { evaluate(view, it) }
                while (pending.isNotEmpty()) {
                    val command = pending.removeFirst()
                    if (command.type !in stateCommands) evaluate(view, command)
                }
            }
        }
        _events.tryEmit(event)
    }

    private fun evaluate(view: WebView, command: ChartCommand) {
        val encoded = command.encode(json)
        val quoted = JSONObject.quote(encoded)
        view.evaluateJavascript("window.ChartDesk && ChartDesk.handle($quoted);", null)
    }

    internal fun parse(raw: String): ChartEvent = ChartEventParser.parse(json, raw)
}
