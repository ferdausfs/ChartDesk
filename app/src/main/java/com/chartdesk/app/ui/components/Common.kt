package com.chartdesk.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

@Composable
fun CdChip(
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalChartDeskPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) palette.accentSoft else Color.Transparent)
            .border(
                1.dp,
                if (selected) palette.accent.copy(alpha = 0.7f) else palette.border,
                RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = ChartDeskType.label,
            color = when {
                !enabled -> palette.textSecondary.copy(alpha = 0.5f)
                selected -> palette.accent
                else -> palette.textSecondary
            },
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = ChartDeskType.label,
        color = LocalChartDeskPalette.current.textSecondary,
        modifier = modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String = "Loading…") {
    val palette = LocalChartDeskPalette.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        Spacer(Modifier.padding(top = 10.dp))
        Text(label, style = ChartDeskType.label, color = palette.textSecondary)
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = LocalChartDeskPalette.current
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text(
            body,
            style = ChartDeskType.label,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = palette.accent) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalChartDeskPalette.current
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
        Text(message, style = ChartDeskType.label, color = palette.textSecondary, modifier = Modifier.padding(top = 6.dp))
        TextButton(onClick = onRetry) { Text("Retry", color = palette.accent) }
    }
}

@Composable
fun OfflineBar(text: String = "Offline — showing cached data", modifier: Modifier = Modifier) {
    val palette = LocalChartDeskPalette.current
    Row(
        modifier = modifier.fillMaxWidth().background(palette.warning.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(palette.warning))
        Spacer(Modifier.width(6.dp))
        Text(text, style = ChartDeskType.label, color = palette.warning)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val palette = LocalChartDeskPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = ChartDeskType.label, color = palette.textSecondary)
            }
        }
        trailing?.invoke()
    }
}
