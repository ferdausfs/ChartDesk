package com.chartdesk.app.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

private data class Tool(val id: String, val label: String)

private val tools = listOf(
    Tool("none", "◌"),
    Tool("select", "◎"),
    Tool("trendline", "╱"),
    Tool("ray", "➤"),
    Tool("hline", "—"),
    Tool("vline", "│"),
    Tool("rect", "▭"),
    Tool("fib", "F"),
    Tool("fibchannel", "FC"),
    Tool("text", "T"),
    Tool("long", "L"),
    Tool("short", "S"),
    Tool("measure", "M"),
)

@Composable
fun DrawingRail(
    mode: String,
    tool: String,
    hasSelection: Boolean,
    locked: Boolean,
    onTool: (String) -> Unit,
    onMode: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalChartDeskPalette.current
    Column(
        modifier = modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(palette.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tools.forEach { item ->
            val active = when (item.id) {
                "none" -> mode == "none" && tool == "none"
                "select" -> mode == "select" && tool == "none"
                else -> tool == item.id
            }
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (active) palette.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .border(
                        1.dp,
                        if (active) palette.accent.copy(alpha = 0.6f) else palette.border.copy(alpha = 0.5f),
                        RoundedCornerShape(5.dp),
                    )
                    .clickable {
                        when (item.id) {
                            "none" -> onMode("none")
                            "select" -> onMode("select")
                            else -> onTool(item.id)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.label,
                    style = ChartDeskType.label,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (active) palette.accent else palette.textSecondary,
                )
            }
        }

        Spacer(Modifier.padding(top = 6.dp))
        Box(Modifier.width(26.dp).padding(vertical = 2.dp).background(palette.border).padding(top = 0.5.dp))

        RailAction(Icons.Outlined.Undo, "Undo", onUndo)
        RailAction(Icons.Outlined.Redo, "Redo", onRedo)
        RailAction(Icons.Outlined.Delete, "Delete selected", onDelete, enabled = hasSelection)
        RailAction(
            if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
            if (locked) "Unlock" else "Lock",
            onLock,
            enabled = hasSelection,
        )
        RailAction(Icons.Outlined.Delete, "Clear all", onClear)
    }
}

@Composable
private fun RailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val palette = LocalChartDeskPalette.current
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) palette.textSecondary else palette.textSecondary.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp),
        )
    }
}
