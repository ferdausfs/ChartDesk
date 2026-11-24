package com.chartdesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chartdesk.app.BuildConfig
import com.chartdesk.app.data.layouts.SavedLayout
import com.chartdesk.app.feature.layouts.LayoutsViewModel
import com.chartdesk.app.ui.components.ScreenHeader
import com.chartdesk.app.ui.components.SectionLabel
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

@Composable
fun MoreScreen(
    onOpenChart: () -> Unit,
    vm: LayoutsViewModel = hiltViewModel(),
) {
    val layouts by vm.layouts.collectAsStateWithLifecycle()
    val palette = LocalChartDeskPalette.current
    var showSave by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        ScreenHeader(title = "More", subtitle = "Layouts, version and attribution")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Saved layouts", style = ChartDeskType.price, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                    TextButton(onClick = { showSave = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                        Text(" Save current", color = palette.accent)
                    }
                }
            }
            if (layouts.isEmpty()) {
                item {
                    Text(
                        "No layouts yet. Set up a chart (symbol, timeframe, 2/4 grid) and save it here.",
                        style = ChartDeskType.label,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            } else {
                items(layouts, key = { it.id }) { layout ->
                    LayoutRow(
                        layout = layout,
                        onApply = {
                            vm.apply(layout)
                            onOpenChart()
                        },
                        onDelete = { vm.delete(layout.id) },
                    )
                }
            }

            item { SectionLabel("About", Modifier.padding(horizontal = 14.dp)) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Text("ChartDesk ${BuildConfig.VERSION_NAME}", style = ChartDeskType.price, color = palette.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Charts by TradingView · lightweight-charts (Apache-2.0). ChartDesk is an independent project, " +
                            "not affiliated with TradingView or LuxAlgo.",
                        style = ChartDeskType.label,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Market data: Binance spot public endpoints. AI assistant: your own key, stored on device.",
                        style = ChartDeskType.label,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showSave) {
        var name by remember { mutableStateOf("My layout") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            containerColor = palette.surface,
            title = { Text("Save layout", color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.save(name)
                    showSave = false
                }) { Text("Save", color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) { Text("Cancel", color = palette.textSecondary) }
            },
        )
    }
}

@Composable
private fun LayoutRow(layout: SavedLayout, onApply: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onApply).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(layout.name, style = ChartDeskType.price, color = palette.textPrimary)
            Text(
                "${layout.symbol} · ${layout.timeframe} · ${layout.grid}-chart · ${layout.chartType}",
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete layout", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}
