package com.chartdesk.app.feature.chart

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chartdesk.app.chart.ChartCommand
import com.chartdesk.app.chart.ChartController
import com.chartdesk.app.chart.ChartEvent
import com.chartdesk.app.data.Candle
import com.chartdesk.app.data.ChartRequestBus
import com.chartdesk.app.data.changePercent
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
)

@HiltViewModel
class ChartViewModel @Inject constructor(
    val controller: ChartController,
    private val repository: MarketRepository,
    private val settingsRepository: SettingsRepository,
    private val requestBus: ChartRequestBus,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    val events: SharedFlow<ChartEvent> = controller.events

    private var liveJob: Job? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var everConnected = false

    init {
        viewModelScope.launch {
            controller.events.collect { event ->
                when (event) {
                    is ChartEvent.Crosshair -> _state.value = _state.value.copy(
                        hovered = Candle(event.time, event.open, event.high, event.low, event.close, event.volume),
                    )
                    is ChartEvent.ChartError -> _state.value = _state.value.copy(error = event.message)
                    else -> Unit
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

    private fun applySettings(settings: AppSettings, initialLoad: Boolean) {
        if (initialLoad) {
            _state.value = _state.value.copy(
                timeframe = settings.defaultTimeframe,
                chartType = settings.defaultChartType,
                priceScale = settings.priceScale,
            )
        }
        controller.send(ChartCommand.SetTheme(isDark(settings.themeMode), settings.colorBlind))
        controller.send(ChartCommand.SetOptions(settings.showGrid, settings.showCrosshair, settings.showWatermark))
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
                controller.send(
                    ChartCommand.SetCandles(
                        symbol = s.symbol,
                        timeframe = s.timeframe,
                        candles = candles,
                        precision = precision,
                        fit = true,
                    ),
                )
                startLive()
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
        controller.send(ChartCommand.UpdateCandle(candle))
    }

    private suspend fun refreshAfterReconnect() {
        val s = _state.value
        runCatching { repository.loadCandles(s.symbol, s.timeframe, 120) }.onSuccess { snapshot ->
            _state.value = _state.value.copy(candles = snapshot.candles, offline = snapshot.fromCache)
            controller.send(
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

    fun selectSymbol(symbol: String) {
        if (symbol == _state.value.symbol) {
            closeSymbolPicker()
            return
        }
        _state.value = _state.value.copy(symbol = symbol, showSymbolPicker = false, candles = emptyList())
        controller.send(ChartCommand.SetSymbol(symbol))
        load()
    }

    fun setTimeframe(tf: String) {
        if (tf == _state.value.timeframe) return
        _state.value = _state.value.copy(timeframe = tf, candles = emptyList())
        controller.send(ChartCommand.SetTimeframe(tf))
        load()
    }

    fun setChartType(type: String) {
        if (type == _state.value.chartType) return
        _state.value = _state.value.copy(chartType = type)
        controller.send(ChartCommand.SetChartType(type))
    }

    fun resetView() = controller.send(ChartCommand.ResetView)

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
        super.onCleared()
    }
}
