package com.kastoriadevs.astikoktel.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
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
import com.kastoriadevs.astikoktel.app.ui.line.RouteStopsScreen
import com.kastoriadevs.astikoktel.app.ui.lines.LinesScreen
import com.kastoriadevs.astikoktel.app.ui.map.MapScreen
import com.kastoriadevs.astikoktel.app.ui.settings.AboutScreen
import com.kastoriadevs.astikoktel.app.ui.settings.SettingsScreen
import com.kastoriadevs.astikoktel.app.ui.stop.StopDetailScreen
import com.kastoriadevs.astikoktel.app.ui.theme.Astika

/**
 * Bottom tabs. Screens opened from a tab stay part of it, so the highlighted tab is
 * tracked from the back stack rather than from the screen shown: a map opened with
 * "Show on map" from a stop still belongs to Home.
 */
private enum class Tab(@StringRes val label: Int, val route: Any, val isRoot: (NavDestination) -> Boolean) {
    HOME(R.string.tab_home, HomeRoute, { it.hasRoute<HomeRoute>() }),
    LINES(R.string.tab_lines, LinesRoute, { it.hasRoute<LinesRoute>() }),
    MAP(R.string.tab_map, MapRoute(), { it.hasRoute<MapRoute>() }),
    ANNOUNCEMENTS(R.string.announcements, AnnouncementsRoute, { it.hasRoute<AnnouncementsRoute>() }),
}

@Composable
private fun Tab.icon(): Painter = when (this) {
    Tab.HOME -> rememberVectorPainter(Icons.Outlined.Home)
    Tab.LINES -> painterResource(R.drawable.ic_route)
    Tab.MAP -> painterResource(R.drawable.ic_map)
    Tab.ANNOUNCEMENTS -> rememberVectorPainter(Icons.Outlined.Notifications)
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Back to the first screen of [tab], like tapping the tab you're already on in most apps */
private fun NavHostController.popToTabRoot(tab: Tab) {
    val startId = graph.findStartDestination().id
    if (tab == Tab.HOME) {
        popBackStack(startId, inclusive = false)
        return
    }
    // Other tabs sit right above Home: [Home, tab root, details...]
    while (previousBackStackEntry != null && previousBackStackEntry?.destination?.id != startId) {
        popBackStack()
    }
}

@Composable
fun AstikaNavHost(appVm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    var currentTab by rememberSaveable { mutableStateOf(Tab.HOME) }

    // A tab root reached by back (e.g. back from Lines lands on Home) moves the highlight.
    // A tab root opened inside another tab (the map from "Show on map") doesn't.
    LaunchedEffect(backStack) {
        val destination = backStack?.destination ?: return@LaunchedEffect
        val tab = Tab.entries.firstOrNull { it.isRoot(destination) } ?: return@LaunchedEffect
        val startId = nav.graph.findStartDestination().id
        if (tab == Tab.HOME || nav.previousBackStackEntry?.destination?.id == startId) currentTab = tab
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                HorizontalDivider(color = Astika.colors.border)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                if (tab == currentTab) {
                                    nav.popToTabRoot(tab)
                                } else {
                                    currentTab = tab
                                    nav.switchTab(tab)
                                }
                            },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Astika.colors.activeBg,
                                unselectedIconColor = Astika.colors.ink2,
                                unselectedTextColor = Astika.colors.ink2,
                            ),
                        )
                    }
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
                    onOpenAnnouncements = {
                        currentTab = Tab.ANNOUNCEMENTS
                        nav.switchTab(Tab.ANNOUNCEMENTS)
                    },
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<AnnouncementsRoute> {
                // A tab of its own; opened from a stop it gets a back arrow to return there
                val startId = nav.graph.findStartDestination().id
                val nested = nav.previousBackStackEntry?.destination?.id != startId
                AnnouncementsScreen(onBack = if (nested) ({ nav.popBackStack() }) else null)
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
                SettingsScreen(appVm = appVm, onBack = { nav.popBackStack() }, onOpenAbout = { nav.navigate(AboutRoute) })
            }
            composable<AboutRoute> {
                AboutScreen(onBack = { nav.popBackStack() })
            }
            composable<LineRoute> { entry ->
                val route = entry.toRoute<LineRoute>()
                LineDetailScreen(
                    lineId = route.lineId,
                    initialDirId = route.dirId,
                    onBack = { nav.popBackStack() },
                    onOpenStops = { lineId, dirId -> nav.navigate(RouteStopsRoute(lineId, dirId)) },
                    onOpenMap = { nav.navigate(MapRoute(lineId = it)) },
                )
            }
            composable<RouteStopsRoute> { entry ->
                val route = entry.toRoute<RouteStopsRoute>()
                RouteStopsScreen(
                    lineId = route.lineId,
                    initialDirId = route.dirId,
                    appVm = appVm,
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
                    onOpenAnnouncements = { nav.navigate(AnnouncementsRoute) },
                )
            }
        }
    }
}
