package com.chartdesk.app.feature.scripts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chartdesk.app.chart.ChartCommand
import com.chartdesk.app.chart.ChartController
import com.chartdesk.app.chart.ChartEvent
import com.chartdesk.app.data.db.ScriptEntity
import com.chartdesk.app.data.scripts.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScriptsUiState(
    val scripts: List<ScriptEntity> = emptyList(),
    val selectedId: String? = null,
    val name: String = "",
    val code: String = "",
    val isDirty: Boolean = false,
    val running: Boolean = false,
    val result: ChartEvent.PineResult? = null,
)

@HiltViewModel
class ScriptsViewModel @Inject constructor(
    private val repository: ScriptRepository,
    private val controller: ChartController,
) : ViewModel() {

    private val _state = MutableStateFlow(ScriptsUiState())
    val state: StateFlow<ScriptsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaults()
            repository.scripts.collect { list ->
                _state.value = _state.value.copy(scripts = list)
            }
        }
        viewModelScope.launch {
            controller.events.collect { event ->
                if (event is ChartEvent.PineResult) {
                    _state.value = _state.value.copy(result = event, running = false)
                }
            }
        }
    }

    fun open(script: ScriptEntity) {
        _state.value = _state.value.copy(
            selectedId = script.id,
            name = script.name,
            code = script.code,
            isDirty = false,
            result = null,
        )
    }

    fun newScript() {
        _state.value = _state.value.copy(
            selectedId = null,
            name = "New script",
            code = TEMPLATE,
            isDirty = true,
            result = null,
        )
    }

    fun updateName(name: String) {
        _state.value = _state.value.copy(name = name, isDirty = true)
    }

    fun updateCode(code: String) {
        _state.value = _state.value.copy(code = code, isDirty = true)
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            val id = repository.save(s.selectedId, s.name, s.code)
            _state.value = _state.value.copy(selectedId = id, isDirty = false)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            if (_state.value.selectedId == id) close()
        }
    }

    fun close() {
        _state.value = _state.value.copy(selectedId = null, name = "", code = "", isDirty = false, result = null)
    }

    fun run() {
        val s = _state.value
        if (s.code.isBlank()) return
        _state.value = _state.value.copy(running = true, result = null)
        controller.send(ChartCommand.RunPine(s.code))
    }

    fun clearFromChart() {
        controller.send(ChartCommand.ClearPine)
        _state.value = _state.value.copy(result = null)
    }

    companion object {
        val TEMPLATE = """
            // ChartDesk Pine subset
            indicator("My script", overlay=true)
            length = input.int(14, title="Length")
            value = ta.ema(close, length)
            plot(value, title="EMA", color=color.green)
        """.trimIndent()
    }
}
