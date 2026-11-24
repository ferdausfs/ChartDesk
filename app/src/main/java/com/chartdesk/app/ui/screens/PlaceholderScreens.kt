package com.chartdesk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

@Composable
private fun Placeholder(title: String, body: String) {
    val palette = LocalChartDeskPalette.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = palette.textPrimary)
        Text(
            body,
            style = ChartDeskType.label,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun WatchlistScreen() = Placeholder("Watchlist", "Live lists arrive in feature 5.")

@Composable
fun ScriptsScreen() = Placeholder("Scripts", "Pine subset editor arrives in feature 9.")

@Composable
fun AlertsScreen() = Placeholder("Alerts", "Alert manager arrives in feature 10.")

@Composable
fun MoreScreen() = Placeholder("More", "Settings, layouts and account arrive in later features.")
