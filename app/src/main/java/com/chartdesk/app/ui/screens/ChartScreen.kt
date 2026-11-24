package com.chartdesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chartdesk.app.chart.ChartEvent
import com.chartdesk.app.chart.ChartWebView
import com.chartdesk.app.data.formatPrice
import com.chartdesk.app.data.formatSignedPercent
import com.chartdesk.app.data.market.ConnectionState
import com.chartdesk.app.data.market.SymbolInfo
import com.chartdesk.app.data.market.Timeframes
import com.chartdesk.app.feature.chart.ChartUiState
import com.chartdesk.app.feature.chart.ChartViewModel
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

private val chartTypes = listOf(
    "candle" to "Candles",
    "heikin" to "Heikin",
    "line" to "Line",
    "area" to "Area",
    "bar" to "Bars",
)

private val scaleModes = listOf("normal", "log", "percent")

@Composable
fun ChartScreen(vm: ChartViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = LocalChartDeskPalette.current
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ChartEvent.Haptic -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                is ChartEvent.ChartError -> snackbar.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize()) {
            ChartHeader(state = state, onSymbolClick = vm::openSymbolPicker)
            TimeframeBar(current = state.timeframe, onSelect = vm::setTimeframe)
            ChartControlBar(
                state = state,
                onType = vm::setChartType,
                onScale = vm::setPriceScale,
                onReset = vm::resetView,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ChartWebView(vm.controller, Modifier.fillMaxSize())
                when {
                    state.isLoading && state.candles.isEmpty() -> LoadingOverlay()
                    state.error != null && state.candles.isEmpty() -> ErrorOverlay(
                        message = state.error ?: "Market data unavailable",
                        onRetry = { vm.load() },
                    )
                }
                if (state.offline && state.candles.isNotEmpty()) {
                    OfflineBadge(Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }

    if (state.showSymbolPicker) {
        SymbolPickerDialog(
            state = state,
            onQuery = vm::search,
            onPick = vm::selectSymbol,
            onDismiss = vm::closeSymbolPicker,
        )
    }
}

@Composable
private fun ChartHeader(state: ChartUiState, onSymbolClick: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    val up = state.changePercent >= 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onSymbolClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    ConnectionDot(state.connection)
                }
                Text(
                    text = if (state.offline) "Cached data" else "Binance spot",
                    style = ChartDeskType.label,
                    color = palette.textSecondary,
                )
            }
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Change symbol",
                tint = palette.textSecondary,
                modifier = Modifier.padding(start = 8.dp).size(16.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatPrice(state.lastPrice, state.precision),
                style = ChartDeskType.priceLarge,
                color = if (up) palette.up else palette.down,
            )
            Text(
                text = formatSignedPercent(state.changePercent),
                style = ChartDeskType.price,
                color = if (up) palette.up else palette.down,
            )
        }
    }
}

@Composable
private fun ConnectionDot(state: ConnectionState) {
    val palette = LocalChartDeskPalette.current
    val color = when (state) {
        ConnectionState.Live -> palette.up
        ConnectionState.Connecting, ConnectionState.Reconnecting -> palette.warning
        ConnectionState.Offline -> palette.down
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            text = when (state) {
                ConnectionState.Live -> "LIVE"
                ConnectionState.Connecting -> "…"
                ConnectionState.Reconnecting -> "RETRY"
                ConnectionState.Offline -> "OFFLINE"
            },
            style = ChartDeskType.label,
            color = color,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

@Composable
private fun TimeframeBar(current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Timeframes.all.forEach { tf ->
            ChartChip(label = tf, selected = tf == current) { onSelect(tf) }
        }
    }
}

@Composable
private fun ChartControlBar(
    state: ChartUiState,
    onType: (String) -> Unit,
    onScale: (String) -> Unit,
    onReset: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chartTypes.forEach { (key, label) ->
            ChartChip(label = label, selected = state.chartType == key) { onType(key) }
        }
        ChartChip(
            label = when (state.priceScale) {
                "log" -> "Log"
                "percent" -> "%"
                else -> "Auto"
            },
            selected = false,
        ) {
            val idx = scaleModes.indexOf(state.priceScale)
            onScale(scaleModes[(idx + 1) % scaleModes.size])
        }
        IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = "Reset chart view", tint = palette.textSecondary)
        }
    }
}

@Composable
private fun ChartChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                1.dp,
                if (selected) palette.accent.copy(alpha = 0.6f) else palette.border,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = ChartDeskType.label,
            color = if (selected) palette.accent else palette.textSecondary,
        )
    }
}

@Composable
private fun LoadingOverlay() {
    val palette = LocalChartDeskPalette.current
    Box(
        Modifier.fillMaxSize().background(palette.background.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Market data unavailable", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
        Text(
            message,
            style = ChartDeskType.label,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        TextButton(onClick = onRetry) { Text("Retry", color = palette.accent) }
    }
}

@Composable
private fun OfflineBadge(modifier: Modifier = Modifier) {
    val palette = LocalChartDeskPalette.current
    Text(
        text = "Offline — cached candles",
        style = ChartDeskType.label,
        color = palette.warning,
        modifier = modifier
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(palette.surfaceAlt)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun SymbolPickerDialog(
    state: ChartUiState,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Symbol", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { Text("BTC, ETH, SOL…", style = ChartDeskType.label) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (state.isSearching) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(top = 12.dp).size(20.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 6.dp)) {
                    items(state.searchResults, key = { it.symbol }) { info ->
                        SymbolRow(info, selected = info.symbol == state.symbol) { onPick(info.symbol) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolRow(info: SymbolInfo, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (selected) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(info.symbol, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
            Text(
                "${info.baseAsset} / ${info.quoteAsset}",
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )
        }
        if (selected) Text("current", style = ChartDeskType.label, color = palette.accent)
    }
}
