package com.chartdesk.app.feature.chart

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import com.chartdesk.app.chart.ChartCommand
import com.chartdesk.app.chart.ChartController
import com.chartdesk.app.chart.ChartControllerFactory
import com.chartdesk.app.chart.ChartEvent
import com.chartdesk.app.data.ChartStateBus
import com.chartdesk.app.data.ChartSummary
import com.chartdesk.app.data.LayoutRequest
import com.chartdesk.app.data.LayoutRequestBus
import com.chartdesk.app.data.Candle
import com.chartdesk.app.data.ChartRequestBus
import com.chartdesk.app.data.changePercent
import com.chartdesk.app.data.drawings.DrawingDto
import com.chartdesk.app.data.drawings.DrawingRepository
import com.chartdesk.app.data.indicators.IndicatorCatalog
import com.chartdesk.app.data.indicators.IndicatorConfig
import com.chartdesk.app.data.indicators.IndicatorRepository
import com.chartdesk.app.data.pricePrecision
import com.chartdesk.app.data.market.ConnectionState
import com.chartdesk.app.data.market.MarketEvent
import com.chartdesk.app.data.market.MarketRepository
import com.chartdesk.app.data.market.SymbolInfo
import com.chartdesk.app.data.settings.AppSettings
import com.chartdesk.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChartUiState(
    val symbol: String = "BTCUSDT",
    val timeframe: String = "1m",
    val chartType: String = "candle",
    val priceScale: String = "normal",
    val precision: Int = 1,
    val candles: List<Candle> = emptyList(),
    val lastPrice: Double = 0.0,
    val changePercent: Double = 0.0,
    val hovered: Candle? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val offline: Boolean = false,
    val connection: ConnectionState = ConnectionState.Connecting,
    val searchQuery: String = "",
    val searchResults: List<SymbolInfo> = emptyList(),
    val isSearching: Boolean = false,
    val showSymbolPicker: Boolean = false,
    val indicators: List<IndicatorConfig> = emptyList(),
    val drawingMode: String = "none",
    val drawingTool: String = "none",
    val selectedDrawingId: String? = null,
    val selectedDrawingLocked: Boolean = false,
    val pendingTextId: String? = null,
    val measureText: String? = null,
    val replayActive: Boolean = false,
    val replayPlaying: Boolean = false,
    val replayIndex: Int = 0,
    val replayTotal: Int = 0,
    val replaySpeed: Double = 1.0,
    val replayPrice: Double = 0.0,
    val replaySide: String? = null,
    val replayEntry: Double = 0.0,
    val replayPnl: Double = 0.0,
    val replayPnlPercent: Double = 0.0,
    val gridMode: Int = 1,
    val chartReady: Boolean = false,
    val lastBridgeLog: String = "",
)

@HiltViewModel
class ChartViewModel @Inject constructor(
    val controller: ChartController,
    private val repository: MarketRepository,
    private val settingsRepository: SettingsRepository,
    private val indicatorRepository: IndicatorRepository,
    private val drawingRepository: DrawingRepository,
    private val requestBus: ChartRequestBus,
    private val controllerFactory: ChartControllerFactory,
    private val stateBus: ChartStateBus,
    private val layoutRequestBus: LayoutRequestBus,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    val events: SharedFlow<ChartEvent> = controller.events

    private var liveJob: Job? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var indicatorsJob: Job? = null
    private var drawingsJob: Job? = null
    private var lastSentDrawings: String = ""
    private var everConnected = false
    private var lastSettings: AppSettings = AppSettings()
    private val secondaryControllers = mutableListOf<ChartController>()
    private var lastCrosshairAt = 0L

    init {
        viewModelScope.launch {
            controller.events.collect { event ->
                when (event) {
                    is ChartEvent.Crosshair -> {
                        _state.value = _state.value.copy(
                            hovered = Candle(event.time, event.open, event.high, event.low, event.close, event.volume),
                        )
                        broadcastCrosshair(event, controller)
                    }
                    is ChartEvent.Ready -> _state.value = _state.value.copy(chartReady = true, error = null)
                    is ChartEvent.Log -> _state.value = _state.value.copy(lastBridgeLog = event.message)
                    is ChartEvent.Unknown -> if (event.type == "crosshair-empty") clearCrosshair(controller)
                    is ChartEvent.ChartError -> _state.value = _state.value.copy(error = event.message)
                    is ChartEvent.DrawingsChanged -> persistDrawings(event.list)
                    is ChartEvent.DrawingSelected -> _state.value = _state.value.copy(
                        selectedDrawingId = event.id,
                        selectedDrawingLocked = event.locked,
                    )
                    is ChartEvent.TextRequest -> _state.value = _state.value.copy(pendingTextId = event.id)
                    is ChartEvent.Measure -> _state.value = _state.value.copy(
                        measureText = "Δ ${String.format(java.util.Locale.US, "%.4f", event.to - event.from)} (${String.format(java.util.Locale.US, "%+.2f", event.percent)}%)",
                    )
                    is ChartEvent.ModeChanged -> _state.value = _state.value.copy(drawingMode = event.mode)
                    is ChartEvent.ReplayState -> _state.value = _state.value.copy(
                        replayActive = event.active,
                        replayPlaying = event.playing,
                        replayIndex = event.index,
                        replayTotal = event.total,
                        replaySpeed = event.speed,
                        replayPrice = event.price,
                        replaySide = event.side,
                        replayEntry = event.entry,
                        replayPnl = event.pnl,
                        replayPnlPercent = event.pnlPercent,
                    )
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            layoutRequestBus.request.collect { request ->
                if (request != null) {
                    applyLayout(request)
                    layoutRequestBus.consume()
                }
            }
        }
        viewModelScope.launch {
            requestBus.symbol.collect { symbol ->
                if (symbol != null) {
                    selectSymbol(symbol)
                    requestBus.consume()
                }
            }
        }
        viewModelScope.launch {
            val initial = settingsRepository.settings.first()
            applySettings(initial, initialLoad = true)
            sendInit(initial)
            observeIndicators(_state.value.symbol)
            observeDrawings(_state.value.symbol)
            load()
            settingsRepository.settings.drop(1).collect { applySettings(it, initialLoad = false) }
        }
    }

    private fun isDark(mode: String): Boolean = when (mode) {
        "light" -> false
        "dark" -> true
        else -> (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun sendInit(settings: AppSettings) {
        val s = _state.value
        controller.send(
            ChartCommand.Init(
                dark = isDark(settings.themeMode),
                colorBlind = settings.colorBlind,
                chartType = s.chartType,
                priceScale = s.priceScale,
                symbol = s.symbol,
                timeframe = s.timeframe,
            ),
        )
    }

    private fun allControllers(): List<ChartController> = listOf(controller) + secondaryControllers

    fun secondaryController(index: Int): ChartController? = secondaryControllers.getOrNull(index)

    fun setGrid(mode: Int) {
        val target = if (mode == 2 || mode == 4) mode else 1
        val s = _state.value
        while (secondaryControllers.size < target - 1) {
            val extra = controllerFactory.create()
            secondaryControllers += extra
            observeSecondary(extra)
            extra.send(
                ChartCommand.Init(
                    dark = isDark(lastSettings.themeMode),
                    colorBlind = lastSettings.colorBlind,
                    chartType = s.chartType,
                    priceScale = s.priceScale,
                    symbol = s.symbol,
                    timeframe = s.timeframe,
                ),
            )
            extra.send(ChartCommand.SetOptions(lastSettings.showGrid, lastSettings.showCrosshair, lastSettings.showWatermark))
            if (s.candles.isNotEmpty()) {
                extra.send(
                    ChartCommand.SetCandles(s.symbol, s.timeframe, s.candles, s.precision, fit = true),
                )
            }
        }
        while (secondaryControllers.size > target - 1) {
            secondaryControllers.removeAt(secondaryControllers.lastIndex)
        }
        _state.value = _state.value.copy(gridMode = target)
        syncBus()
    }

    private fun observeSecondary(extra: ChartController) {
        viewModelScope.launch {
            extra.events.collect { event ->
                when (event) {
                    is ChartEvent.Crosshair -> broadcastCrosshair(event, extra)
                    is ChartEvent.Unknown -> if (event.type == "crosshair-empty") clearCrosshair(extra)
                    else -> Unit
                }
            }
        }
    }

    private fun broadcastCrosshair(event: ChartEvent.Crosshair, from: ChartController) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCrosshairAt < 45) return
        lastCrosshairAt = now
        allControllers().filter { it !== from }.forEach {
            it.send(ChartCommand.SetCrosshair(event.time, event.close))
        }
    }

    private fun clearCrosshair(from: ChartController) {
        allControllers().filter { it !== from }.forEach { it.send(ChartCommand.ClearCrosshair) }
    }

    private fun applyLayout(request: LayoutRequest) {
        _state.value = _state.value.copy(
            symbol = request.symbol,
            timeframe = request.timeframe,
            chartType = request.chartType,
            candles = emptyList(),
        )
        setGrid(request.grid)
        controller.send(ChartCommand.SetSymbol(request.symbol))
        controller.send(ChartCommand.SetTimeframe(request.timeframe))
        controller.send(ChartCommand.SetChartType(request.chartType))
        observeIndicators(request.symbol)
        observeDrawings(request.symbol)
        load()
    }

    private fun syncBus() {
        val s = _state.value
        stateBus.update(ChartSummary(s.symbol, s.timeframe, s.chartType, s.gridMode))
    }

    private fun applySettings(settings: AppSettings, initialLoad: Boolean) {
        lastSettings = settings
        syncBus()
        if (initialLoad) {
            _state.value = _state.value.copy(
                timeframe = settings.defaultTimeframe,
                chartType = settings.defaultChartType,
                priceScale = settings.priceScale,
            )
        }
        allControllers().forEach {
            it.send(ChartCommand.SetTheme(isDark(settings.themeMode), settings.colorBlind))
            it.send(ChartCommand.SetOptions(settings.showGrid, settings.showCrosshair, settings.showWatermark))
        }
        if (!initialLoad && settings.priceScale != _state.value.priceScale) {
            _state.value = _state.value.copy(priceScale = settings.priceScale)
            controller.send(ChartCommand.SetPriceScale(settings.priceScale))
        }
    }

    fun load(initial: Boolean = false) {
        val s = _state.value
        loadJob?.cancel()
        liveJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val snapshot = repository.loadCandles(s.symbol, s.timeframe, 500)
                val candles = snapshot.candles
                val precision = pricePrecision(candles.last().close)
                _state.value = _state.value.copy(
                    candles = candles,
                    precision = precision,
                    lastPrice = candles.last().close,
                    changePercent = candles.changePercent(),
                    isLoading = false,
                    offline = snapshot.fromCache,
                    error = null,
                )
                allControllers().forEach { target ->
                    target.send(
                        ChartCommand.SetCandles(
                            symbol = s.symbol,
                            timeframe = s.timeframe,
                            candles = candles,
                            precision = precision,
                            fit = true,
                        ),
                    )
                }
                startLive()
                viewModelScope.launch {
                    delay(20_000)
                    if (!controller.isReady() && _state.value.candles.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            error = "Chart engine did not start. Tap retry.",
                            chartReady = false,
                        )
                    }
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    offline = true,
                    connection = ConnectionState.Offline,
                    error = t.message ?: "Could not load market data",
                )
            }
        }
    }

    private fun startLive() {
        val s = _state.value
        liveJob?.cancel()
        everConnected = false
        liveJob = viewModelScope.launch {
            repository.live(s.symbol, s.timeframe).collect { event ->
                when (event) {
                    is MarketEvent.Status -> {
                        _state.value = _state.value.copy(connection = event.state)
                        if (event.state == ConnectionState.Live) {
                            if (everConnected) refreshAfterReconnect()
                            everConnected = true
                        }
                    }
                    is MarketEvent.Bar -> onBar(event.candle)
                }
            }
        }
    }

    private fun onBar(candle: Candle) {
        val s = _state.value
        val merged = com.chartdesk.app.data.upsertCandle(s.candles, candle)
        val last = merged.last()
        _state.value = s.copy(
            candles = merged,
            lastPrice = last.close,
            changePercent = merged.changePercent(),
            connection = ConnectionState.Live,
            offline = false,
        )
        allControllers().forEach { it.send(ChartCommand.UpdateCandle(candle)) }
    }

    private suspend fun refreshAfterReconnect() {
        val s = _state.value
        runCatching { repository.loadCandles(s.symbol, s.timeframe, 120) }.onSuccess { snapshot ->
            _state.value = _state.value.copy(candles = snapshot.candles, offline = snapshot.fromCache)
            allControllers().forEach { target ->
                target.send(
                    ChartCommand.SetCandles(
                        symbol = s.symbol,
                        timeframe = s.timeframe,
                        candles = snapshot.candles,
                        precision = _state.value.precision,
                        fit = false,
                    ),
                )
            }
        }
    }

    fun selectSymbol(symbol: String) {
        if (symbol == _state.value.symbol) {
            closeSymbolPicker()
            return
        }
        resetReplay()
        _state.value = _state.value.copy(symbol = symbol, showSymbolPicker = false, candles = emptyList())
        allControllers().forEach { it.send(ChartCommand.SetSymbol(symbol)) }
        observeIndicators(symbol)
        observeDrawings(symbol)
        syncBus()
        load()
    }

    private fun observeDrawings(symbol: String) {
        drawingsJob?.cancel()
        lastSentDrawings = ""
        drawingsJob = viewModelScope.launch {
            drawingRepository.observe(symbol).collect { list ->
                val fingerprint = list.joinToString("|") { "${it.id}:${it.type}:${it.locked}:${it.points.size}" }
                if (fingerprint == lastSentDrawings) return@collect
                lastSentDrawings = fingerprint
                controller.send(ChartCommand.SetDrawings(list))
            }
        }
    }

    private fun persistDrawings(list: List<DrawingDto>) {
        val symbol = _state.value.symbol
        lastSentDrawings = list.joinToString("|") { "${it.id}:${it.type}:${it.locked}:${it.points.size}" }
        viewModelScope.launch { drawingRepository.replaceAll(symbol, list) }
    }

    fun setDrawingTool(tool: String) {
        _state.value = _state.value.copy(drawingTool = tool, drawingMode = if (tool == "none") "none" else "select")
        if (tool == "none") controller.send(ChartCommand.SetDrawMode("none"))
        else controller.send(ChartCommand.SetDrawTool(tool))
    }

    fun setDrawingMode(mode: String) {
        _state.value = _state.value.copy(drawingMode = mode, drawingTool = "none")
        controller.send(ChartCommand.SetDrawMode(mode))
    }

    fun undoDrawing() = controller.send(ChartCommand.UndoDrawing)

    fun redoDrawing() = controller.send(ChartCommand.RedoDrawing)

    fun deleteDrawing() = controller.send(ChartCommand.DeleteDrawing)

    fun clearDrawings() = controller.send(ChartCommand.ClearDrawings)

    fun toggleDrawingLock() {
        controller.send(ChartCommand.LockDrawing(!_state.value.selectedDrawingLocked))
    }

    fun submitDrawingText(text: String) {
        val id = _state.value.pendingTextId ?: return
        controller.send(ChartCommand.UpdateDrawingText(id, text))
        _state.value = _state.value.copy(pendingTextId = null)
    }

    fun dismissDrawingText() {
        _state.value = _state.value.copy(pendingTextId = null)
    }

    fun clearMeasure() {
        _state.value = _state.value.copy(measureText = null)
    }

    fun startReplay(index: Int) {
        controller.send(ChartCommand.ReplayStart(index))
    }

    fun toggleReplayPlay() {
        if (_state.value.replayPlaying) controller.send(ChartCommand.ReplayPause)
        else controller.send(ChartCommand.ReplayPlay(_state.value.replaySpeed))
    }

    fun stepReplay(delta: Int) {
        controller.send(ChartCommand.ReplayStep(delta))
    }

    fun cycleReplaySpeed() {
        val speeds = listOf(0.5, 1.0, 2.0, 5.0)
        val current = _state.value.replaySpeed
        val next = speeds[(speeds.indexOf(current).takeIf { it >= 0 } ?: 0) + 1] ?: speeds.first()
        controller.send(ChartCommand.ReplaySpeed(next))
    }

    fun stopReplay() {
        controller.send(ChartCommand.ReplayStop)
    }

    fun replayTrade(action: String) {
        controller.send(ChartCommand.ReplayTrade(action))
    }

    private fun observeIndicators(symbol: String) {
        indicatorsJob?.cancel()
        indicatorsJob = viewModelScope.launch {
            indicatorRepository.ensureDefault(symbol)
            indicatorRepository.observe(symbol).collect { list ->
                _state.value = _state.value.copy(indicators = list)
                controller.send(
                    ChartCommand.SetIndicators(
                        list.map { ChartCommand.IndicatorDto(it.uid, it.type, it.params, it.visible) },
                    ),
                )
            }
        }
    }

    fun toggleIndicator(type: String, enabled: Boolean) {
        val current = _state.value.indicators
        val updated = if (enabled) {
            if (current.any { it.type == type }) current
            else current + IndicatorConfig(
                uid = type,
                type = type,
                params = IndicatorCatalog.defaults(type),
            )
        } else {
            current.filterNot { it.type == type }
        }
        persistIndicators(updated)
    }

    fun updateIndicatorParams(type: String, params: Map<String, Double>) {
        persistIndicators(
            _state.value.indicators.map { if (it.type == type) it.copy(params = params) else it },
        )
    }

    private fun resetReplay() {
        if (_state.value.replayActive) controller.send(ChartCommand.ReplayStop)
        _state.value = _state.value.copy(
            replayActive = false,
            replayPlaying = false,
            replayIndex = 0,
            replayTotal = 0,
            replaySide = null,
            replayPnl = 0.0,
            replayPnlPercent = 0.0,
        )
    }

    private fun persistIndicators(list: List<IndicatorConfig>) {
        val symbol = _state.value.symbol
        _state.value = _state.value.copy(indicators = list)
        viewModelScope.launch { indicatorRepository.replace(symbol, list) }
    }

    fun setTimeframe(tf: String) {
        if (tf == _state.value.timeframe) return
        resetReplay()
        _state.value = _state.value.copy(timeframe = tf, candles = emptyList())
        allControllers().forEach { it.send(ChartCommand.SetTimeframe(tf)) }
        syncBus()
        load()
    }

    fun setChartType(type: String) {
        if (type == _state.value.chartType) return
        _state.value = _state.value.copy(chartType = type)
        allControllers().forEach { it.send(ChartCommand.SetChartType(type)) }
        syncBus()
    }

    fun resetView() {
        allControllers().forEach { it.send(ChartCommand.ResetView) }
    }

    fun resendChart() {
        val s = _state.value
        sendInit(lastSettings)
        if (s.candles.isNotEmpty()) {
            allControllers().forEach {
                it.send(ChartCommand.SetCandles(s.symbol, s.timeframe, s.candles, s.precision, fit = true))
            }
        }
        allControllers().forEach { it.send(ChartCommand.SetChartType(s.chartType)) }
        controller.send(
            ChartCommand.SetIndicators(
                s.indicators.map { ChartCommand.IndicatorDto(it.uid, it.type, it.params, it.visible) },
            ),
        )
        _state.value = _state.value.copy(error = null)
    }

    fun showError(message: String) = controller.send(ChartCommand.ShowError(message))

    fun openSymbolPicker() {
        _state.value = _state.value.copy(showSymbolPicker = true)
        if (_state.value.searchResults.isEmpty()) search("")
    }

    fun closeSymbolPicker() {
        _state.value = _state.value.copy(showSymbolPicker = false)
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query, isSearching = true)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            val results = runCatching { repository.searchSymbols(query) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(searchResults = results, isSearching = false)
        }
    }

    override fun onCleared() {
        liveJob?.cancel()
        loadJob?.cancel()
        searchJob?.cancel()
        indicatorsJob?.cancel()
        drawingsJob?.cancel()
        super.onCleared()
    }
}
