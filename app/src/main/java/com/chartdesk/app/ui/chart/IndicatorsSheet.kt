package com.chartdesk.app.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chartdesk.app.data.indicators.IndicatorConfig
import com.chartdesk.app.data.indicators.IndicatorCatalog
import com.chartdesk.app.data.indicators.IndicatorSpec
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorsSheet(
    active: List<IndicatorConfig>,
    onToggle: (String, Boolean) -> Unit,
    onParams: (String, Map<String, Double>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    var editing by remember { mutableStateOf<IndicatorSpec?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Indicators", style = ChartDeskType.priceLarge, color = palette.textPrimary)
            Text(
                "Computed in the chart layer · saved per symbol",
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )

            Spacer(Modifier.padding(top = 10.dp))
            IndicatorCatalog.all.forEach { spec ->
                val config = active.firstOrNull { it.type == spec.type }
                IndicatorRow(
                    spec = spec,
                    enabled = config != null,
                    onToggle = { enabled -> onToggle(spec.type, enabled) },
                    onSettings = { editing = spec },
                    hasSettings = spec.params.isNotEmpty() && config != null,
                )
            }
        }
    }

    val spec = editing
    if (spec != null) {
        ParamsDialog(
            spec = spec,
            current = active.firstOrNull { it.type == spec.type }?.params ?: IndicatorCatalog.defaults(spec.type),
            onSave = { params ->
                editing = null
                onParams(spec.type, params)
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun IndicatorRow(
    spec: IndicatorSpec,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onSettings: () -> Unit,
    hasSettings: Boolean,
) {
    val palette = LocalChartDeskPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(spec.name, style = ChartDeskType.price, color = palette.textPrimary)
            Text(
                when (spec.pane) {
                    "separate" -> "separate pane"
                    "volume" -> "volume overlay"
                    else -> "on price chart"
                },
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )
        }
        if (hasSettings) {
            IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Tune, contentDescription = "Settings", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.accent,
                checkedTrackColor = palette.accentSoft,
            ),
        )
    }
}

@Composable
private fun ParamsDialog(
    spec: IndicatorSpec,
    current: Map<String, Double>,
    onSave: (Map<String, Double>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    var values by remember {
        mutableStateOf(spec.params.associate { it.key to (current[it.key] ?: it.default).toString() })
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(spec.name, color = palette.textPrimary) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                spec.params.forEach { param ->
                    OutlinedTextField(
                        value = values[param.key] ?: "",
                        onValueChange = { values = values + (param.key to it) },
                        label = { Text(param.label, style = ChartDeskType.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
                error?.let { Text(it, style = ChartDeskType.label, color = palette.down) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = mutableMapOf<String, Double>()
                for (param in spec.params) {
                    val raw = values[param.key]?.toDoubleOrNull()
                    if (raw == null || raw <= 0.0) {
                        error = "${param.label} must be a positive number"
                        return@TextButton
                    }
                    parsed[param.key] = if (param.integer) raw.toInt().toDouble() else raw
                }
                onSave(parsed)
            }) { Text("Save", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
        },
    )
}
