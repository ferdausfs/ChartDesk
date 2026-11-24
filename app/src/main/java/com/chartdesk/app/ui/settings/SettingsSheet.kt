package com.chartdesk.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chartdesk.app.data.settings.AppSettings
import com.chartdesk.app.data.market.Timeframes
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    onThemeMode: (String) -> Unit,
    onColorBlind: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onGrid: (Boolean) -> Unit,
    onCrosshair: (Boolean) -> Unit,
    onWatermark: (Boolean) -> Unit,
    onDefaultTimeframe: (String) -> Unit,
    onDefaultChartType: (String) -> Unit,
    onPriceScale: (String) -> Unit,
    diagnostics: String,
    onCopyDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Settings", style = ChartDeskType.priceLarge, color = palette.textPrimary)

            Section("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("dark" to "Dark", "light" to "Light", "system" to "Auto").forEach { (key, label) ->
                    ChoiceChip(label, settings.themeMode == key) { onThemeMode(key) }
                }
            }
            ToggleRow(
                title = "Colour-blind palette",
                subtitle = "Blue / orange candles instead of green / red",
                checked = settings.colorBlind,
                onChange = onColorBlind,
            )

            Section("Chart")
            ToggleRow("Grid lines", "Show horizontal and vertical grid", settings.showGrid, onGrid)
            ToggleRow("Crosshair", "Dashed crosshair with axis labels", settings.showCrosshair, onCrosshair)
            ToggleRow("Watermark", "ChartDesk mark behind the series", settings.showWatermark, onWatermark)

            Section("Interaction")
            ToggleRow("Haptic feedback", "Vibrate on long-press inspect and reset", settings.haptics, onHaptics)

            Section("Defaults")
            Text("Timeframe on launch", style = ChartDeskType.label, color = palette.textSecondary)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Timeframes.all.take(5).forEach { tf ->
                    ChoiceChip(tf, settings.defaultTimeframe == tf) { onDefaultTimeframe(tf) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Timeframes.all.drop(5).forEach { tf ->
                    ChoiceChip(tf, settings.defaultTimeframe == tf) { onDefaultTimeframe(tf) }
                }
            }
            Spacer(Modifier.padding(top = 10.dp))
            Text("Price scale", style = ChartDeskType.label, color = palette.textSecondary)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("normal" to "Auto", "log" to "Log", "percent" to "%").forEach { (key, label) ->
                    ChoiceChip(label, settings.priceScale == key) { onPriceScale(key) }
                }
            }
            Spacer(Modifier.padding(top = 10.dp))
            Text("Chart type on launch", style = ChartDeskType.label, color = palette.textSecondary)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppSettings.ChartTypes.forEach { type ->
                    ChoiceChip(type.replaceFirstChar { it.uppercase() }, settings.defaultChartType == type) {
                        onDefaultChartType(type)
                    }
                }
            }

            Spacer(Modifier.padding(top = 16.dp))
            Text("Diagnostics", style = ChartDeskType.label, color = palette.textSecondary)
            Text(
                diagnostics,
                style = ChartDeskType.label,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onCopyDiagnostics) { Text("Copy diagnostics", color = palette.accent) }

            Spacer(Modifier.padding(top = 8.dp))
            Text(
                "Charts by TradingView · lightweight-charts (Apache-2.0). ChartDesk is not affiliated with TradingView.",
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    val palette = LocalChartDeskPalette.current
    Text(
        title.uppercase(),
        style = ChartDeskType.label,
        color = palette.textSecondary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalChartDeskPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = ChartDeskType.price, color = palette.textPrimary)
            Text(subtitle, style = ChartDeskType.label, color = palette.textSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.accent,
                checkedTrackColor = palette.accentSoft,
            ),
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) palette.accentSoft else palette.surfaceAlt)
            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, style = ChartDeskType.label, color = if (selected) palette.accent else palette.textPrimary)
    }
}
