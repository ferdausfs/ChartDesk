package com.chartdesk.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chartdesk.app.chart.ChartEvent
import com.chartdesk.app.data.db.ScriptEntity
import com.chartdesk.app.feature.scripts.ScriptsUiState
import com.chartdesk.app.feature.scripts.ScriptsViewModel
import com.chartdesk.app.ui.components.EmptyState
import com.chartdesk.app.ui.components.ScreenHeader
import com.chartdesk.app.ui.scripts.PineHighlighter
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScriptsScreen(vm: ScriptsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.selectedId != null || state.isDirty) {
        ScriptEditor(state = state, vm = vm)
    } else {
        ScriptLibrary(state = state, vm = vm)
    }
}

@Composable
private fun ScriptLibrary(state: ScriptsUiState, vm: ScriptsViewModel) {
    val palette = LocalChartDeskPalette.current
    Column(Modifier.fillMaxSize().background(palette.background)) {
        ScreenHeader(
            title = "Scripts",
            subtitle = "Pine v6 subset · runs in the chart layer",
            trailing = {
                IconButton(onClick = vm::newScript) {
                    Icon(Icons.Outlined.Add, contentDescription = "New script", tint = palette.accent)
                }
            },
        )
        if (state.scripts.isEmpty()) {
            EmptyState(
                title = "No scripts yet",
                body = "Write a Pine-style indicator and run it on the chart.",
                actionLabel = "New script",
                onAction = vm::newScript,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.scripts, key = { it.id }) { script ->
                    ScriptRow(script = script, onOpen = { vm.open(script) }, onDelete = { vm.delete(script.id) })
                }
            }
        }
    }
}

@Composable
private fun ScriptRow(script: ScriptEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalChartDeskPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(script.name, style = ChartDeskType.price, color = palette.textPrimary)
                if (script.builtIn) {
                    Text(
                        "sample",
                        style = ChartDeskType.label,
                        color = palette.warning,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.warning.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(script.updatedAt)),
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ScriptEditor(state: ScriptsUiState, vm: ScriptsViewModel) {
    val palette = LocalChartDeskPalette.current
    val highlighter = remember(palette) { PineHighlighter(palette) }

    Column(Modifier.fillMaxSize().background(palette.background).padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::close) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = palette.textSecondary)
            }
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::updateName,
                singleLine = true,
                textStyle = ChartDeskType.price,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                ),
            )
            IconButton(onClick = vm::save) {
                Icon(Icons.Outlined.Save, contentDescription = "Save", tint = palette.textSecondary)
            }
            IconButton(onClick = vm::run, enabled = !state.running) {
                if (state.running) {
                    CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Run", tint = palette.accent)
                }
            }
        }

        OutlinedTextField(
            value = state.code,
            onValueChange = vm::updateCode,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = palette.textPrimary),
            visualTransformation = highlighter,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                cursorColor = palette.accent,
            ),
        )

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Runs on the Chart tab",
                style = ChartDeskType.label,
                color = palette.textSecondary,
            )
            TextButton(onClick = vm::clearFromChart) { Text("Clear from chart", color = palette.textSecondary) }
        }

        val result = state.result
        if (result != null) {
            ResultPanel(result)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ResultPanel(result: ChartEvent.PineResult) {
    val palette = LocalChartDeskPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (result.ok) palette.up.copy(alpha = 0.1f) else palette.down.copy(alpha = 0.1f))
            .padding(8.dp),
    ) {
        Text(
            if (result.ok) "Ran · ${result.plots} plots · ${result.shapes} signals" else "Script error",
            style = ChartDeskType.price,
            fontWeight = FontWeight.Medium,
            color = if (result.ok) palette.up else palette.down,
        )
        result.errors.forEach { error ->
            Text(
                "line ${error.line}: ${error.message}",
                style = ChartDeskType.label,
                color = palette.down,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        result.warnings.forEach { warning ->
            Text("note: $warning", style = ChartDeskType.label, color = palette.warning)
        }
    }
}
