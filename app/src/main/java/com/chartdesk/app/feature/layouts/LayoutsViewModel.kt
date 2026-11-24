package com.chartdesk.app.feature.layouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chartdesk.app.data.LayoutRequest
import com.chartdesk.app.data.LayoutRequestBus
import com.chartdesk.app.data.layouts.LayoutRepository
import com.chartdesk.app.data.layouts.SavedLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LayoutsViewModel @Inject constructor(
    private val repository: LayoutRepository,
    private val requestBus: LayoutRequestBus,
) : ViewModel() {

    val layouts: StateFlow<List<SavedLayout>> = repository.layouts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun save(name: String) {
        viewModelScope.launch { repository.save(name) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun apply(layout: SavedLayout) {
        requestBus.apply(
            LayoutRequest(
                symbol = layout.symbol,
                timeframe = layout.timeframe,
                chartType = layout.chartType,
                grid = layout.grid,
            ),
        )
    }
}
