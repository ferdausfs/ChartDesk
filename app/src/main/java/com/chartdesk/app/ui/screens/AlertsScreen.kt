package com.chartdesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chartdesk.app.data.db.AlertEntity
import com.chartdesk.app.data.db.AlertEventEntity
import com.chartdesk.app.data.formatPrice
import com.chartdesk.app.feature.alerts.AlertsUiState
import com.chartdesk.app.feature.alerts.AlertsViewModel
import com.chartdesk.app.ui.components.CdChip
import com.chartdesk.app.ui.components.EmptyState
import com.chartdesk.app.ui.components.OfflineBar
import com.chartdesk.app.ui.components.ScreenHeader
import com.chartdesk.app.ui.components.SectionLabel
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(vm: AlertsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = LocalChartDeskPalette.current

    Column(Modifier.fillMaxSize().background(palette.background)) {
        ScreenHeader(
            title = "Alerts",
            subtitle = "Checked every 30s in-app · 15 min in background",
            trailing = {
                Row {
                    IconButton(onClick = vm::checkNow) {
                        if (state.checking) {
                            CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Check now", tint = palette.textSecondary)
                        }
                    }
                    IconButton(onClick = vm::openAdd) {
                        Icon(Icons.Outlined.Add, contentDescription = "New alert", tint = palette.accent)
                    }
                }
            },
        )
        if (state.offline) OfflineBar(text = "Offline — alerts will be checked when the network returns")

        LazyColumn(Modifier.fillMaxSize()) {
            if (state.alerts.isEmpty()) {
                item {
                    EmptyState(
                        title = "No alerts",
                        body = "Get a push when a symbol crosses your price.",
                        actionLabel = "Create alert",
                        onAction = vm::openAdd,
                    )
                }
            } else {
                item { SectionLabel("Price alerts", Modifier.padding(horizontal = 14.dp)) }
                items(state.alerts, key = { it.id }) { alert ->
                    AlertRow(alert = alert, onDelete = { vm.delete(alert.id) })
                }
            }
            if (state.events.isNotEmpty()) {
                item { SectionLabel("Triggered", Modifier.padding(horizontal = 14.dp)) }
                items(state.events, key = { it.id }) { event -> EventRow(event) }
            }
        }
    }

    if (state.showAdd) {
        AddAlertDialog(state = state, vm = vm)
    }
}

@Composable
private fun AlertRow(alert: AlertEntity, onDelete: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(alert.symbol, style = ChartDeskType.price, fontWeight = FontWeight.Medium, color = palette.textPrimary)
                Text(
                    if (alert.direction == "above") " ↑ above " else " ↓ below ",
                    style = ChartDeskType.label,
                    color = palette.textSecondary,
                )
                Text(formatPrice(alert.price, com.chartdesk.app.data.pricePrecision(alert.price)), style = ChartDeskType.price, color = palette.textPrimary)
            }
            Text(
                if (alert.active) "active" else "triggered",
                style = ChartDeskType.label,
                color = if (alert.active) palette.up else palette.textSecondary,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete alert", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EventRow(event: AlertEventEntity) {
    val palette = LocalChartDeskPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(
            "${event.symbol} ${if (event.direction == "above") "above" else "below"} ${formatPrice(event.target, 2)} @ ${formatPrice(event.price, 2)}",
            style = ChartDeskType.label,
            color = palette.textPrimary,
        )
        Text(
            SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).format(Date(event.triggeredAt)),
            style = ChartDeskType.label,
            color = palette.textSecondary,
        )
    }
}

@Composable
private fun AddAlertDialog(state: AlertsUiState, vm: AlertsViewModel) {
    val palette = LocalChartDeskPalette.current
    AlertDialog(
        onDismissRequest = vm::closeAdd,
        containerColor = palette.surface,
        title = { Text("New price alert", color = palette.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.symbol,
                    onValueChange = vm::setSymbol,
                    singleLine = true,
                    label = { Text("Symbol", style = ChartDeskType.label) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CdChip("Above", selected = state.direction == "above") { vm.setDirection("above") }
                    CdChip("Below", selected = state.direction == "below") { vm.setDirection("below") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.price,
                    onValueChange = vm::setPrice,
                    singleLine = true,
                    label = { Text("Price", style = ChartDeskType.label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(it, style = ChartDeskType.label, color = palette.down, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::save) { Text("Create", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = vm::closeAdd) { Text("Cancel", color = palette.textSecondary) }
        },
    )
}
