package com.chartdesk.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chartdesk.app.ui.screens.AlertsScreen
import com.chartdesk.app.ui.screens.ChartScreen
import com.chartdesk.app.ui.screens.MoreScreen
import com.chartdesk.app.ui.screens.ScriptsScreen
import com.chartdesk.app.ui.screens.WatchlistScreen

enum class RootTab(val route: String, val label: String, val icon: ImageVector) {
    Chart("chart", "Chart", Icons.Outlined.CandlestickChart),
    Watchlist("watchlist", "Watchlist", Icons.Outlined.StarOutline),
    Scripts("scripts", "Scripts", Icons.Outlined.ShowChart),
    Alerts("alerts", "Alerts", Icons.Outlined.Notifications),
    More("more", "More", Icons.Outlined.MoreHoriz),
}

@Composable
fun ChartDeskRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                RootTab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RootTab.Chart.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(RootTab.Chart.route) { ChartScreen() }
            composable(RootTab.Watchlist.route) { WatchlistScreen() }
            composable(RootTab.Scripts.route) { ScriptsScreen() }
            composable(RootTab.Alerts.route) { AlertsScreen() }
            composable(RootTab.More.route) { MoreScreen() }
        }
    }
}
