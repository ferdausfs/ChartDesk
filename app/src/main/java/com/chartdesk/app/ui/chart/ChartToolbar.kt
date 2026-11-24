package com.chartdesk.app.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.chartdesk.app.data.market.ConnectionState
import com.chartdesk.app.feature.chart.ChartUiState
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

private val typeLabels = mapOf(
    "candle" to "Candles",
    "heikin" to "Heikin-Ashi",
    "line" to "Line",
    "area" to "Area",
    "bar" to "Bars",
)

@Composable
fun ChartToolbar(
    state: ChartUiState,
    fullscreen: Boolean,
    onSymbolClick: () -> Unit,
    onTimeframeClick: () -> Unit,
    onChartType: (String) -> Unit,
    onIndicators: () -> Unit,
    onReplay: () -> Unit,
    onGrid: () -> Unit,
    onSettings: () -> Unit,
    onScreenshot: () -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    var typeMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .height(46.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSymbolClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.symbol,
                        style = ChartDeskType.price,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.width(4.dp))
                    ConnectionDot(state.connection)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatPrice(state.lastPrice, state.precision),
                        style = ChartDeskType.label,
                        color = if (state.changePercent >= 0) palette.up else palette.down,
                    )
                    Text(
                        text = "  ${formatSignedPercent(state.changePercent)}",
                        style = ChartDeskType.label,
                        color = if (state.changePercent >= 0) palette.up else palette.down,
                    )
                }
            }
        }

        TimeframePill(state.timeframe, onClick = onTimeframeClick)

        Box {
            ToolbarIcon(Icons.Outlined.ShowChart, "Chart type") { typeMenu = true }
            DropdownMenu(
                expanded = typeMenu,
                onDismissRequest = { typeMenu = false },
                containerColor = palette.surface,
            ) {
                typeLabels.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                style = ChartDeskType.label,
                                color = if (state.chartType == key) palette.accent else palette.textPrimary,
                            )
                        },
                        onClick = {
                            typeMenu = false
                            onChartType(key)
                        },
                    )
                }
            }
        }
        ToolbarIcon(Icons.Outlined.Functions, "Indicators", onIndicators)
        ToolbarIcon(Icons.Outlined.PlayArrow, "Bar replay", onReplay)
        ToolbarIcon(Icons.Outlined.GridView, "Chart grid", onGrid)
        ToolbarIcon(Icons.Outlined.PhotoCamera, "Screenshot", onScreenshot)
        ToolbarIcon(Icons.Outlined.Settings, "Settings", onSettings)
        ToolbarIcon(
            icon = if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
            label = if (fullscreen) "Exit fullscreen" else "Fullscreen",
            onClick = onToggleFullscreen,
        )
    }
}

@Composable
private fun TimeframePill(timeframe: String, onClick: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(timeframe, style = ChartDeskType.label, color = palette.textPrimary)
    }
}

@Composable
private fun ToolbarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        Icon(icon, contentDescription = label, tint = palette.textSecondary, modifier = Modifier.size(19.dp))
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
    Box(
        Modifier
            .padding(start = 2.dp)
            .size(5.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color),
    )
}

@Composable
fun TimeframeSheetContent(current: String, onSelect: (String) -> Unit) {
    val palette = LocalChartDeskPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Timeframe", style = ChartDeskType.label, color = palette.textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("1m", "5m", "15m", "30m").forEach { tf ->
                TimeframeChip(tf, tf == current, onSelect)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("1h", "4h", "1d", "1w", "1M").forEach { tf ->
                TimeframeChip(tf, tf == current, onSelect)
            }
        }
    }
}

@Composable
private fun TimeframeChip(tf: String, selected: Boolean, onSelect: (String) -> Unit) {
    val palette = LocalChartDeskPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) palette.accentSoft else palette.surfaceAlt)
            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(6.dp))
            .clickable { onSelect(tf) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(tf, style = ChartDeskType.label, color = if (selected) palette.accent else palette.textPrimary)
    }
}
