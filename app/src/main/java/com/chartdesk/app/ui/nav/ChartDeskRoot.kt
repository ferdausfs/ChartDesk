package com.chartdesk.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.chartdesk.app.feature.nav.NavigationViewModel
import com.chartdesk.app.ui.screens.AlertsScreen
import com.chartdesk.app.ui.screens.ChartScreen
import com.chartdesk.app.ui.screens.MoreScreen
import com.chartdesk.app.ui.screens.ScriptsScreen
import com.chartdesk.app.ui.screens.WatchlistPanel
import com.chartdesk.app.ui.screens.WatchlistScreen
import com.chartdesk.app.ui.theme.ChartDeskType
import com.chartdesk.app.ui.theme.LocalChartDeskPalette

enum class RootTab(val route: String, val label: String, val icon: ImageVector) {
    Chart("chart", "Chart", Icons.Outlined.CandlestickChart),
    Watchlist("watchlist", "Watchlist", Icons.Outlined.StarOutline),
    Scripts("scripts", "Scripts", Icons.Outlined.ShowChart),
    Alerts("alerts", "Alerts", Icons.Outlined.Notifications),
    More("more", "More", Icons.Outlined.MoreHoriz),
}

@Composable
fun ChartDeskRoot(navVm: NavigationViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val openSymbol: (String) -> Unit = { symbol ->
        navVm.openSymbol(symbol)
        navController.navigateTo(RootTab.Chart)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    val onChartTab = current?.hierarchy?.any { it.route == RootTab.Chart.route } == true
    LaunchedEffect(onChartTab) {
        if (!onChartTab) fullscreen = false
    }

    Box(Modifier.fillMaxSize()) {
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                if (!fullscreen) {
                    LeftRail(current = current, onSelect = { tab -> navController.navigateTo(tab) })
                }
                NavHostContent(
                    navController = navController,
                    modifier = Modifier.weight(1f),
                    fullscreen = fullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onOpenSymbol = openSymbol,
                )
                if (!fullscreen && onChartTab) {
                    Box(Modifier.width(220.dp)) {
                        WatchlistPanel(onOpenSymbol = openSymbol)
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!fullscreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            RootTab.entries.forEach { tab ->
                                val selected = current?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navController.navigateTo(tab) },
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
                    }
                },
            ) { innerPadding ->
                NavHostContent(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                    fullscreen = fullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onOpenSymbol = openSymbol,
                )
            }
        }
    }
}

@Composable
private fun NavHostContent(
    navController: NavHostController,
    modifier: Modifier,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenSymbol: (String) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = RootTab.Chart.route,
        modifier = modifier,
    ) {
        composable(RootTab.Chart.route) {
            ChartScreen(
                fullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
            )
        }
        composable(RootTab.Watchlist.route) { WatchlistScreen(onOpenSymbol = onOpenSymbol) }
        composable(RootTab.Scripts.route) { ScriptsScreen() }
        composable(RootTab.Alerts.route) { AlertsScreen() }
        composable(RootTab.More.route) { MoreScreen() }
    }
}

private fun NavHostController.navigateTo(tab: RootTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun LeftRail(current: androidx.navigation.NavDestination?, onSelect: (RootTab) -> Unit) {
    val palette = LocalChartDeskPalette.current
    Column(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight()
            .background(palette.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "CD",
            style = ChartDeskType.label,
            fontWeight = FontWeight.Bold,
            color = palette.accent,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        RootTab.entries.forEach { tab ->
            val selected = current?.hierarchy?.any { it.route == tab.route } == true
            IconButton(onClick = { onSelect(tab) }, modifier = Modifier.size(44.dp)) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) palette.accent else palette.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}


