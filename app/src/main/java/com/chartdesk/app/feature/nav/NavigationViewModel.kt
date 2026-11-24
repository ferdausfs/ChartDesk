package com.chartdesk.app.feature.nav

import androidx.lifecycle.ViewModel
import com.chartdesk.app.data.ChartRequestBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val bus: ChartRequestBus,
) : ViewModel() {

    fun openSymbol(symbol: String) {
        bus.request(symbol)
    }
}
