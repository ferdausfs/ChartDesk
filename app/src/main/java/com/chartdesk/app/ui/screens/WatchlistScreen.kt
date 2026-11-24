package com.chartdesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chartdesk.app.data.formatPrice
import com.chartdesk.app.data.formatSignedPercent
import com.chartdesk.app.data.market.SymbolInfo
import com.chartdesk.app.data.pricePrecision
import com.chartdesk.app.feature.watchlist.WatchlistRow
import com.chartdesk.app.feature.watchlist.WatchlistUiState
import com.chartdesk.app.feature.watchlist.WatchlistViewModel
import com.chartdesk.app.ui.components.CdChip
import com.chartdesk.app.ui.components.EmptyState
import com.chartdesk.app.ui.components.LoadingBox
import com.chartdesk.app.ui.components.OfflineBar
import com.chartdesk.app.ui.components.ScreenHeader
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette
import kotlin.math.roundToInt

private val sortModes = listOf("default" to "Default", "change" to "Change", "price" to "Price", "name" to "A–Z")
private const val ROW_HEIGHT_DP = 54

@Composable
fun WatchlistScreen(
    onOpenSymbol: (String) -> Unit,
    vm: WatchlistViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    WatchlistBody(state = state, vm = vm, onOpenSymbol = onOpenSymbol, compact = false)
}

@Composable
fun WatchlistPanel(
    onOpenSymbol: (String) -> Unit,
    vm: WatchlistViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    WatchlistBody(state = state, vm = vm, onOpenSymbol = onOpenSymbol, compact = true)
}

@Composable
private fun WatchlistBody(
    state: WatchlistUiState,
    vm: WatchlistViewModel,
    onOpenSymbol: (String) -> Unit,
    compact: Boolean,
) {
    val palette = LocalChartDeskPalette.current
    var showCreateList by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        if (!compact) {
            ScreenHeader(
                title = "Watchlist",
                subtitle = "Binance spot · 5s refresh",
                trailing = {
                    Row {
                        IconButton(onClick = vm::openAddSymbol) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add symbol", tint = palette.accent)
                        }
                        IconButton(onClick = { vm.toggleListActions(true) }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "List actions", tint = palette.textSecondary)
                        }
                    }
                },
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Watchlist", style = ChartDeskType.price, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = vm::openAddSymbol, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add symbol", tint = palette.accent, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (state.offline) OfflineBar()

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(state.lists, key = { it.id }) { list ->
                CdChip(label = list.name, selected = list.id == state.selectedListId) { vm.selectList(list.id) }
            }
            item {
                CdChip(label = "+ list", selected = false) { showCreateList = true }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(sortModes) { (key, label) ->
                CdChip(label = label, selected = state.sortMode == key) { vm.setSortMode(key) }
            }
        }

        when {
            state.isLoading -> LoadingBox(label = "Loading lists…")
            state.rows.isEmpty() -> EmptyState(
                title = "No symbols yet",
                body = "Add a symbol to start tracking live prices.",
                actionLabel = "Add symbol",
                onAction = vm::openAddSymbol,
            )
            else -> DraggableWatchlist(
                rows = state.rows,
                sortMode = state.sortMode,
                onOpenSymbol = onOpenSymbol,
                onRemove = vm::removeSymbol,
                onMove = vm::move,
            )
        }
    }

    if (state.showAddSymbol) {
        AddSymbolDialog(
            state = state,
            onQuery = vm::search,
            onPick = vm::addSymbol,
            onDismiss = vm::closeAddSymbol,
        )
    }

    if (showCreateList) {
        NameDialog(
            title = "New list",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                showCreateList = false
                vm.createList(name)
            },
            onDismiss = { showCreateList = false },
        )
    }

    if (state.showListActions) {
        val selected = state.lists.firstOrNull { it.id == state.selectedListId }
        AlertDialog(
            onDismissRequest = { vm.toggleListActions(false) },
            containerColor = palette.surface,
            title = { Text("List actions", color = palette.textPrimary) },
            text = {
                Column {
                    Text(selected?.name ?: "No list", style = ChartDeskType.price, color = palette.textSecondary)
                    TextButton(onClick = {
                        vm.toggleListActions(false)
                        renameTarget = selected?.name
                    }) { Text("Rename list", color = palette.accent) }
                    TextButton(onClick = {
                        vm.toggleListActions(false)
                        vm.deleteList()
                    }) { Text("Delete list", color = palette.down) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.toggleListActions(false) }) { Text("Close", color = palette.textSecondary) }
            },
        )
    }

    val target = renameTarget
    if (target != null) {
        NameDialog(
            title = "Rename list",
            initial = target,
            confirmLabel = "Save",
            onConfirm = { name ->
                renameTarget = null
                vm.renameList(name)
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun DraggableWatchlist(
    rows: List<WatchlistRow>,
    sortMode: String,
    onOpenSymbol: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    val haptic = LocalHapticFeedback.current
    val rowHeightPx = with(LocalDensity.current) { (ROW_HEIGHT_DP + 1).dp.toPx() }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(rows, key = { _, row -> row.symbol }) { index, row ->
            val dragging = index == dragIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT_DP.dp)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        alpha = if (dragging) 0.92f else 1f
                    }
                    .background(if (dragging) palette.surfaceAlt else palette.background)
                    .pointerInput(rows.size, sortMode) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            },
                            onDragEnd = {
                                val target = (index + (dragOffset / rowHeightPx).roundToInt())
                                    .coerceIn(0, rows.lastIndex)
                                if (target != index) {
                                    onMove(index, target)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dragIndex = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                dragIndex = -1
                                dragOffset = 0f
                            },
                        )
                    }
                    .clickable { onOpenSymbol(row.symbol) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        row.symbol,
                        style = ChartDeskType.price,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                    )
                    Text(
                        if (row.hasTicker) "Binance spot" else "waiting for ticker…",
                        style = ChartDeskType.label,
                        color = palette.textSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (row.hasTicker) formatPrice(row.price, pricePrecision(row.price)) else "—",
                        style = ChartDeskType.price,
                        color = palette.textPrimary,
                    )
                    Text(
                        if (row.hasTicker) formatSignedPercent(row.changePercent) else "—",
                        style = ChartDeskType.label,
                        color = when {
                            !row.hasTicker -> palette.textSecondary
                            row.changePercent >= 0 -> palette.up
                            else -> palette.down
                        },
                    )
                }
                IconButton(onClick = { onRemove(row.symbol) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove symbol",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                IconButton(onClick = {}, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        contentDescription = "Drag to reorder",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSymbolDialog(
    state: WatchlistUiState,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Add symbol", color = palette.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { Text("BTC, ETH, SOL…", style = ChartDeskType.label) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isSearching) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(top = 10.dp).size(18.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(top = 6.dp)) {
                    items(state.searchResults, key = { it.symbol }) { info ->
                        SymbolPickRow(info, already = state.rows.any { it.symbol == info.symbol }) { onPick(info.symbol) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = palette.textSecondary) }
        },
    )
}

@Composable
private fun SymbolPickRow(info: SymbolInfo, already: Boolean, onClick: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !already, onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(info.symbol, style = ChartDeskType.price, color = palette.textPrimary)
            Text("${info.baseAsset} / ${info.quoteAsset}", style = ChartDeskType.label, color = palette.textSecondary)
        }
        if (already) Text("added", style = ChartDeskType.label, color = palette.textSecondary)
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(title, color = palette.textPrimary) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(confirmLabel, color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        },
    )
}
