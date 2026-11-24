package com.chartdesk.app.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chartdesk.app.data.formatPrice
import com.chartdesk.app.data.formatSignedPercent
import com.chartdesk.app.feature.chart.ChartUiState
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayStartSheet(
    total: Int,
    onStart: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    var value by remember { mutableFloatStateOf((total - 60).coerceAtLeast(0).toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 26.dp)) {
            Text("Bar replay", style = ChartDeskType.priceLarge, color = palette.textPrimary)
            Text(
                "Pick where playback starts. Indicators and drawings recalculate on every bar.",
                style = ChartDeskType.label,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Bar ${value.toInt() + 1} of $total",
                style = ChartDeskType.price,
                color = palette.accent,
                modifier = Modifier.padding(top = 14.dp),
            )
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = 0f..((total - 1).coerceAtLeast(1)).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.border,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
                TextButton(onClick = { onStart(value.toInt()) }) { Text("Start replay", color = palette.accent) }
            }
        }
    }
}

@Composable
fun ReplayBar(
    state: ChartUiState,
    onPlayPause: () -> Unit,
    onStep: (Int) -> Unit,
    onSpeed: () -> Unit,
    onStop: () -> Unit,
    onTrade: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChartDeskPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(palette.surface.copy(alpha = 0.96f))
            .border(1.dp, palette.border, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(5.dp)).background(palette.accentSoft)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.replayPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (state.replayPlaying) "Pause" else "Play",
                tint = palette.accent,
                modifier = Modifier.size(17.dp),
            )
        }
        ReplayAction("−1") { onStep(-1) }
        ReplayAction("+1") { onStep(1) }
        ReplayAction("${formatSpeed(state.replaySpeed)}×") { onSpeed() }
        ReplayAction("Bar ${state.replayIndex + 1}/${state.replayTotal}") { }
        ReplayAction("■") { onStop() }
        Spacer(Modifier.width(4.dp))

        TradeButton("Buy", palette.up) { onTrade("buy") }
        TradeButton("Sell", palette.down) { onTrade("sell") }
        TradeButton("Close", palette.textSecondary) { onTrade("close") }

        Column(Modifier.padding(start = 6.dp)) {
            Text(
                if (state.replaySide == null) "flat" else "${state.replaySide} @ ${formatPrice(state.replayEntry, state.precision)}",
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )
            Text(
                "${formatSignedPrice(state.replayPnl)}  (${formatSignedPercent(state.replayPnlPercent)})",
                style = ChartDeskType.price,
                fontWeight = FontWeight.Medium,
                color = if (state.replayPnl >= 0) palette.up else palette.down,
            )
        }
    }
}

private fun formatSpeed(speed: Double): String =
    if (speed == speed.toInt().toDouble()) speed.toInt().toString() else speed.toString()

private fun formatSignedPrice(value: Double): String {
    val sign = if (value > 0) "+" else ""
    return "$sign${String.format(java.util.Locale.US, "%.2f", value)}"
}

@Composable
private fun ReplayAction(label: String, onClick: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, palette.border, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(label, style = ChartDeskType.label, color = palette.textSecondary)
    }
}

@Composable
private fun TradeButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, style = ChartDeskType.label, color = color)
    }
}
