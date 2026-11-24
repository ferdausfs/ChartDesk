package com.chartdesk.app.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chartdesk.app.BuildConfig
import com.chartdesk.app.chart.ChartEvent
import com.chartdesk.app.chart.ChartWebView
import com.chartdesk.app.chart.ScreenshotManager
import com.chartdesk.app.data.market.ConnectionState
import com.chartdesk.app.data.market.SymbolInfo
import com.chartdesk.app.feature.chart.ChartUiState
import com.chartdesk.app.feature.chart.ChartViewModel
import com.chartdesk.app.feature.settings.SettingsViewModel
import com.chartdesk.app.ui.chart.ChartToolbar
import com.chartdesk.app.ui.chart.DrawingRail
import com.chartdesk.app.ui.chart.IndicatorsSheet
import com.chartdesk.app.ui.chart.ReplayBar
import com.chartdesk.app.ui.chart.ReplayStartSheet
import com.chartdesk.app.ui.chart.TimeframeSheetContent
import com.chartdesk.app.ui.settings.SettingsSheet
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    vm: ChartViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val palette = LocalChartDeskPalette.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val snackbar = remember { SnackbarHostState() }
    var showTimeframeSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showIndicatorsSheet by remember { mutableStateOf(false) }
    var showReplaySheet by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ChartEvent.Haptic -> if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                is ChartEvent.ChartError -> snackbar.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    LaunchedEffect(state.measureText) {
        val text = state.measureText ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.clearMeasure()
    }

    LaunchedEffect(fullscreen) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun takeScreenshot() {
        val activity = context as? Activity ?: return
        val chartView = vm.controller.webView() ?: return
        scope.launch {
            val bitmap = ScreenshotManager.capture(activity, chartView)
            if (bitmap == null) {
                snackbar.showSnackbar("Screenshot failed")
                return@launch
            }
            val file = ScreenshotManager.save(activity, bitmap)
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(share, "Share chart"))
            }
            snackbar.showSnackbar("Saved to ${file.absolutePath}")
        }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize()) {
            if (!fullscreen) {
                ChartToolbar(
                    state = state,
                    fullscreen = fullscreen,
                    onSymbolClick = vm::openSymbolPicker,
                    onTimeframeClick = { showTimeframeSheet = true },
                    onChartType = vm::setChartType,
                    onIndicators = { showIndicatorsSheet = true },
                    onReplay = { showReplaySheet = true },
                    onSettings = { showSettingsSheet = true },
                    onScreenshot = ::takeScreenshot,
                    onToggleFullscreen = onToggleFullscreen,
                )
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!fullscreen) {
                    DrawingRail(
                        mode = state.drawingMode,
                        tool = state.drawingTool,
                        hasSelection = state.selectedDrawingId != null,
                        locked = state.selectedDrawingLocked,
                        onTool = vm::setDrawingTool,
                        onMode = vm::setDrawingMode,
                        onUndo = vm::undoDrawing,
                        onRedo = vm::redoDrawing,
                        onDelete = vm::deleteDrawing,
                        onLock = vm::toggleDrawingLock,
                        onClear = vm::clearDrawings,
                    )
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
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
                    if (state.replayActive) {
                        ReplayBar(
                            state = state,
                            onPlayPause = vm::toggleReplayPlay,
                            onStep = vm::stepReplay,
                            onSpeed = vm::cycleReplaySpeed,
                            onStop = vm::stopReplay,
                            onTrade = vm::replayTrade,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }

    if (state.pendingTextId != null) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = vm::dismissDrawingText,
            containerColor = palette.surface,
            title = { Text("Drawing label", color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.submitDrawingText(label.ifBlank { "Text" }) }) {
                    Text("Save", color = palette.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDrawingText) { Text("Cancel", color = palette.textSecondary) }
            },
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

    if (showTimeframeSheet) {
        ModalBottomSheet(onDismissRequest = { showTimeframeSheet = false }, containerColor = palette.surface) {
            TimeframeSheetContent(current = state.timeframe) { tf ->
                showTimeframeSheet = false
                vm.setTimeframe(tf)
            }
        }
    }

    if (showIndicatorsSheet) {
        IndicatorsSheet(
            active = state.indicators,
            onToggle = vm::toggleIndicator,
            onParams = vm::updateIndicatorParams,
            onDismiss = { showIndicatorsSheet = false },
        )
    }

    if (showReplaySheet) {
        ReplayStartSheet(
            total = state.candles.size,
            onStart = { index ->
                showReplaySheet = false
                vm.startReplay(index)
            },
            onDismiss = { showReplaySheet = false },
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            settings = settings,
            onThemeMode = settingsVm::setThemeMode,
            onColorBlind = settingsVm::setColorBlind,
            onHaptics = settingsVm::setHaptics,
            onGrid = settingsVm::setGrid,
            onCrosshair = settingsVm::setCrosshair,
            onWatermark = settingsVm::setWatermark,
            onDefaultTimeframe = settingsVm::setDefaultTimeframe,
            onDefaultChartType = settingsVm::setDefaultChartType,
            onPriceScale = settingsVm::setPriceScale,
            onDismiss = { showSettingsSheet = false },
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
        Surface(shape = RoundedCornerShape(10.dp), color = palette.surface, modifier = Modifier.fillMaxWidth()) {
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
            .border(0.dp, palette.border)
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
