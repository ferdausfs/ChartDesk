package com.chartdesk.app.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chartdesk.app.data.alerts.AlertNotifier
import com.chartdesk.app.data.alerts.AlertRepository
import com.chartdesk.app.data.db.AlertEntity
import com.chartdesk.app.data.db.AlertEventEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AlertsUiState(
    val alerts: List<AlertEntity> = emptyList(),
    val events: List<AlertEventEntity> = emptyList(),
    val showAdd: Boolean = false,
    val symbol: String = "BTCUSDT",
    val direction: String = "above",
    val price: String = "",
    val error: String? = null,
    val offline: Boolean = false,
    val checking: Boolean = false,
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: AlertRepository,
    private val notifier: AlertNotifier,
) : ViewModel() {

    private val _state = MutableStateFlow(AlertsUiState())
    val state: StateFlow<AlertsUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        notifier.ensureChannel()
        viewModelScope.launch {
            repository.alerts.collect { list -> _state.value = _state.value.copy(alerts = list) }
        }
        viewModelScope.launch {
            repository.events.collect { list -> _state.value = _state.value.copy(events = list) }
        }
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val result = runCatching { repository.checkNow() }
                _state.value = _state.value.copy(
                    offline = result.isFailure,
                    checking = false,
                )
                delay(30_000)
            }
        }
    }

    fun openAdd() {
        _state.value = _state.value.copy(showAdd = true, symbol = "BTCUSDT", direction = "above", price = "", error = null)
    }

    fun closeAdd() {
        _state.value = _state.value.copy(showAdd = false)
    }

    fun setSymbol(symbol: String) = _state.value.let { _state.value = it.copy(symbol = symbol.uppercase()) }

    fun setDirection(direction: String) = _state.value.let { _state.value = it.copy(direction = direction) }

    fun setPrice(price: String) = _state.value.let { _state.value = it.copy(price = price) }

    fun save() {
        val s = _state.value
        val price = s.price.toDoubleOrNull()
        if (s.symbol.isBlank() || price == null || price <= 0) {
            _state.value = s.copy(error = "Enter a symbol and a positive price")
            return
        }
        viewModelScope.launch {
            runCatching { repository.create(s.symbol, s.direction, price) }
                .onSuccess { _state.value = _state.value.copy(showAdd = false, error = null) }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "Could not save alert") }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun markSeen() {
        viewModelScope.launch { repository.markEventsSeen() }
    }

    fun checkNow() {
        _state.value = _state.value.copy(checking = true)
        viewModelScope.launch {
            val result = runCatching { repository.checkNow() }
            _state.value = _state.value.copy(checking = false, offline = result.isFailure)
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
