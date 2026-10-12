package com.kastoriadevs.astikoktel.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.announcements.AnnouncementsScreen
import com.kastoriadevs.astikoktel.app.ui.home.HomeScreen
import com.kastoriadevs.astikoktel.app.ui.line.LineDetailScreen
import com.kastoriadevs.astikoktel.app.ui.lines.LinesScreen
import com.kastoriadevs.astikoktel.app.ui.map.MapScreen
import com.kastoriadevs.astikoktel.app.ui.settings.SettingsScreen
import com.kastoriadevs.astikoktel.app.ui.stop.StopDetailScreen

/** Bottom tabs; `owns` decides which destinations keep the tab highlighted (details pushed from it) */
private enum class Tab(@StringRes val label: Int, val route: Any, val owns: (NavDestination) -> Boolean) {
    HOME(R.string.tab_home, HomeRoute, { it.hasRoute<HomeRoute>() || it.hasRoute<StopRoute>() || it.hasRoute<AnnouncementsRoute>() }),
    LINES(R.string.tab_lines, LinesRoute, { it.hasRoute<LinesRoute>() || it.hasRoute<LineRoute>() }),
    MAP(R.string.tab_map, MapRoute(), { it.hasRoute<MapRoute>() }),
    SETTINGS(R.string.tab_settings, SettingsRoute, { it.hasRoute<SettingsRoute>() }),
}

@Composable
private fun Tab.icon(): Painter = when (this) {
    Tab.HOME -> rememberVectorPainter(Icons.Outlined.Home)
    Tab.LINES -> painterResource(R.drawable.ic_route)
    Tab.MAP -> painterResource(R.drawable.ic_map)
    Tab.SETTINGS -> rememberVectorPainter(Icons.Outlined.Settings)
}

private fun NavDestination?.belongsTo(tab: Tab): Boolean =
    this?.hierarchy?.any { tab.owns(it) } == true

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AstikaNavHost(appVm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination.belongsTo(tab),
                        onClick = { nav.switchTab(tab) },
                        icon = { Icon(tab.icon(), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val openLine: (String, String?) -> Unit = { lineId, dirId -> nav.navigate(LineRoute(lineId, dirId)) }
        val openStop: (String) -> Unit = { nav.navigate(StopRoute(it)) }

        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    appVm = appVm,
                    onOpenStop = openStop,
                    onOpenLine = openLine,
                    onOpenAnnouncements = { nav.navigate(AnnouncementsRoute) },
                )
            }
            composable<AnnouncementsRoute> {
                AnnouncementsScreen(onBack = { nav.popBackStack() })
            }
            composable<LinesRoute> {
                LinesScreen(onOpenLine = { openLine(it, null) })
            }
            composable<MapRoute> { entry ->
                val route = entry.toRoute<MapRoute>()
                MapScreen(
                    initialLineId = route.lineId,
                    initialStopId = route.stopId,
                    onOpenStop = openStop,
                    onOpenLine = openLine,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(appVm = appVm)
            }
            composable<LineRoute> { entry ->
                val route = entry.toRoute<LineRoute>()
                LineDetailScreen(
                    lineId = route.lineId,
                    initialDirId = route.dirId,
                    onBack = { nav.popBackStack() },
                    onOpenStop = openStop,
                    onOpenMap = { nav.navigate(MapRoute(lineId = it)) },
                )
            }
            composable<StopRoute> { entry ->
                val route = entry.toRoute<StopRoute>()
                StopDetailScreen(
                    stopId = route.stopId,
                    appVm = appVm,
                    onBack = { nav.popBackStack() },
                    onOpenLine = openLine,
                    onOpenMap = { nav.navigate(MapRoute(stopId = it)) },
                )
            }
        }
    }
}
