package com.chartdesk.app.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chartdesk.app.data.ChartRequestBus
import com.chartdesk.app.data.market.MarketRepository
import com.chartdesk.app.data.market.SymbolInfo
import com.chartdesk.app.data.market.Ticker
import com.chartdesk.app.data.watchlist.Watchlist
import com.chartdesk.app.data.watchlist.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WatchlistRow(
    val symbol: String,
    val price: Double,
    val changePercent: Double,
    val quoteVolume: Double,
    val hasTicker: Boolean,
)

data class WatchlistUiState(
    val lists: List<Watchlist> = emptyList(),
    val selectedListId: String? = null,
    val rows: List<WatchlistRow> = emptyList(),
    val isLoading: Boolean = true,
    val offline: Boolean = false,
    val error: String? = null,
    val sortMode: String = "default",
    val showAddSymbol: Boolean = false,
    val showListActions: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SymbolInfo> = emptyList(),
    val isSearching: Boolean = false,
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val repository: WatchlistRepository,
    private val market: MarketRepository,
    private val requestBus: ChartRequestBus,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchlistUiState())
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    private var itemsJob: Job? = null
    private var tickerJob: Job? = null
    private var searchJob: Job? = null
    private var currentSymbols: List<String> = emptyList()
    private var tickerMap: Map<String, Ticker> = emptyMap()

    init {
        viewModelScope.launch {
            repository.ensureDefault()
            repository.lists.collect { lists ->
                val selected = _state.value.selectedListId?.takeIf { id -> lists.any { it.id == id } }
                    ?: lists.firstOrNull()?.id
                _state.value = _state.value.copy(lists = lists, selectedListId = selected, isLoading = false)
                if (selected != null) observeItems(selected)
            }
        }
    }

    private fun observeItems(listId: String) {
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            repository.items(listId).collect { items ->
                val symbols = items.map { it.symbol }
                if (symbols != currentSymbols) {
                    currentSymbols = symbols
                    startTickerPolling(symbols)
                }
                _state.value = _state.value.copy(
                    rows = WatchlistSorting.build(items.map { it.symbol }, tickerMap, _state.value.sortMode),
                )
            }
        }
    }

    private fun startTickerPolling(symbols: List<String>) {
        tickerJob?.cancel()
        if (symbols.isEmpty()) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                val result = runCatching { market.tickers(symbols) }
                result.onSuccess { tickers ->
                    tickerMap = tickers.associateBy { it.symbol }
                    _state.value = _state.value.copy(
                        rows = WatchlistSorting.build(currentSymbols, tickerMap, _state.value.sortMode),
                        offline = false,
                        error = null,
                    )
                }.onFailure {
                    _state.value = _state.value.copy(offline = true)
                }
                delay(5_000)
            }
        }
    }

    fun selectList(listId: String) {
        _state.value = _state.value.copy(selectedListId = listId)
        observeItems(listId)
    }

    fun setSortMode(mode: String) {
        _state.value = _state.value.copy(
            sortMode = mode,
            rows = WatchlistSorting.build(currentSymbols, tickerMap, mode),
        )
    }

    fun openAddSymbol() {
        _state.value = _state.value.copy(showAddSymbol = true)
        if (_state.value.searchResults.isEmpty()) search("")
    }

    fun closeAddSymbol() {
        _state.value = _state.value.copy(showAddSymbol = false)
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query, isSearching = true)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            val results = runCatching { market.searchSymbols(query) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(searchResults = results, isSearching = false)
        }
    }

    fun addSymbol(symbol: String) {
        val listId = _state.value.selectedListId ?: return
        viewModelScope.launch {
            repository.addSymbol(listId, symbol)
            _state.value = _state.value.copy(showAddSymbol = false, searchQuery = "", searchResults = emptyList())
        }
    }

    fun removeSymbol(symbol: String) {
        val listId = _state.value.selectedListId ?: return
        viewModelScope.launch { repository.removeSymbol(listId, symbol) }
    }

    fun move(from: Int, to: Int) {
        val listId = _state.value.selectedListId ?: return
        viewModelScope.launch { repository.move(listId, from, to) }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            val id = repository.createList(name)
            _state.value = _state.value.copy(selectedListId = id, showListActions = false)
            observeItems(id)
        }
    }

    fun renameList(name: String) {
        val id = _state.value.selectedListId ?: return
        viewModelScope.launch { repository.renameList(id, name) }
    }

    fun deleteList() {
        val id = _state.value.selectedListId ?: return
        viewModelScope.launch {
            repository.deleteList(id)
            _state.value = _state.value.copy(selectedListId = null, showListActions = false)
        }
    }

    fun openChart(symbol: String) {
        requestBus.request(symbol)
    }

    fun toggleListActions(show: Boolean) {
        _state.value = _state.value.copy(showListActions = show)
    }

    override fun onCleared() {
        itemsJob?.cancel()
        tickerJob?.cancel()
        searchJob?.cancel()
        super.onCleared()
    }
}
