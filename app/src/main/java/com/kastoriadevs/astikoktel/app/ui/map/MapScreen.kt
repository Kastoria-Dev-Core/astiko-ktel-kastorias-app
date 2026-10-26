package com.kastoriadevs.astikoktel.app.ui.map

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.LocationDisabledException
import com.kastoriadevs.astikoktel.app.data.Stop
import com.kastoriadevs.astikoktel.app.domain.Coords
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.currentLanguage
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.common.toColor
import com.kastoriadevs.astikoktel.app.ui.components.DepartureList
import com.kastoriadevs.astikoktel.app.ui.components.BadgeSize
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import com.kastoriadevs.astikoktel.app.ui.theme.RouteYellow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private val KASTORIA = GeoPoint(40.5196, 21.266)

/**
 * Dark theme uses the same OpenStreetMap tiles as light, with their colours inverted and the
 * hue turned 180 degrees (like OASTH's app): light ground goes dark while forests stay green,
 * water blue and motorways pink. No separate dark tile provider or API key needed.
 */
private val DarkTiles = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            0.574f, -1.430f, -0.144f, 0f, 255f,
            -0.426f, -0.430f, -0.144f, 0f, 255f,
            -0.426f, -1.430f, 0.856f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

@Composable
fun MapScreen(
    initialLineId: String?,
    initialStopId: String?,
    onOpenStop: (String) -> Unit,
    onOpenLine: (lineId: String, dirId: String?) -> Unit,
) {
    val dark = isAppDark()
    StatusBarIcons(darkIcons = !dark)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = appContainer()
    val data = container.transit.data
    val scope = rememberCoroutineScope()

    var lineFilter by rememberSaveable { mutableStateOf(initialLineId) }
    var openStopId by rememberSaveable { mutableStateOf(initialStopId) }
    var user by remember { mutableStateOf<Coords?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<Int?>(null) }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 11.0
            maxZoomLevel = 19.0
            setTileSource(TileSourceFactory.MAPNIK)
            controller.setZoom(14.0)
            controller.setCenter(KASTORIA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(dark) {
        mapView.overlayManager.tilesOverlay.apply {
            setColorFilter(if (dark) DarkTiles else null)
            // The grid shown while tiles load, dark too so the map doesn't flash white
            loadingBackgroundColor = if (dark) 0xFF1B1B1B.toInt() else 0xFFEDEAE4.toInt()
            loadingLineColor = if (dark) 0xFF2A2A2A.toInt() else 0xFFDDD8D0.toInt()
        }
        mapView.invalidate()
    }

    // Two pins shared by every stop marker instead of one bitmap per marker
    val stopIcon = remember { pinIcon(context, fill = STOP_GREEN, glyph = 0xFFFFFFFF.toInt(), radiusDp = 11f) }
    val openStopIcon = remember { pinIcon(context, fill = RouteYellow.toArgb(), glyph = 0xFF14222B.toInt(), radiusDp = 14f) }
    val language = currentLanguage()
    val markers = remember { mutableMapOf<String, Marker>() }
    val stopGeoPoints = remember { data.stops.associate { it.id to GeoPoint(it.lat, it.lng) } }
    val stopMarkers = remember { mutableMapOf<String, Marker>() }
    val routePointsCache = remember { mutableMapOf<String, List<GeoPoint>>() }
    fun getRoutePoints(key: String, path: List<Pair<Double, Double>>): List<GeoPoint> =
        routePointsCache.getOrPut(key) { path.map { (lat, lng) -> GeoPoint(lat, lng) } }

    val selectedLine = data.line(lineFilter)
    val shownStops = remember(selectedLine) {
        if (selectedLine != null) {
            val ids = data.lineStopIds(selectedLine)
            data.stops.filter { it.id in ids }
        } else {
            data.stops
        }
    }
    // Don't keep a card open for a stop the line filter just hid
    LaunchedEffect(shownStops) {
        if (openStopId != null && shownStops.none { it.id == openStopId }) openStopId = null
    }

    // Overlays are rebuilt when what they show changes. Opening a stop only swaps two icons (below).
    LaunchedEffect(selectedLine, user, stopIcon, language) {
        val lines = selectedLine?.let { listOf(it) } ?: data.lines
        markers.clear()
        mapView.overlays.clear()
        // Added first so markers above it get taps first; a tap on empty map closes the sheet
        mapView.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    openStopId = null
                    return true
                }

                override fun longPressHelper(p: GeoPoint?): Boolean = false
            }),
        )
        // Both directions: outbound and return often take different streets
        lines.forEach { line ->
            line.directions.forEach { d ->
                mapView.overlays.add(routeOverlay(mapView, line, getRoutePoints("dir-${line.id}-${d.id}", data.directionPath(line, d.id))))
                data.branchPaths(line, d.id).forEachIndexed { index, path ->
                    mapView.overlays.add(routeOverlay(mapView, line, getRoutePoints("br-${line.id}-${d.id}-$index", path)))
                }
            }
        }
        user?.let { addUserOverlays(mapView, it) }
        shownStops.forEach { stop ->
            val marker = stopMarkers.getOrPut(stop.id) {
                Marker(mapView).apply {
                    position = stopGeoPoints[stop.id] ?: GeoPoint(stop.lat, stop.lng)
                    // The pin's point sits on the stop
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        openStopId = stop.id
                        true
                    }
                }
            }
            marker.icon = if (stop.id == openStopId) openStopIcon else stopIcon
            marker.title = stop.name.get(language)
            markers[stop.id] = marker
            mapView.overlays.add(marker)
        }
        mapView.overlays.add(CopyrightOverlay(context))
        mapView.invalidate()
    }

    // Highlight the open stop by swapping icons, without rebuilding the overlays
    var highlighted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openStopId, selectedLine, user, stopIcon, language) {
        highlighted?.let { markers[it]?.icon = stopIcon }
        openStopId?.let { id ->
            markers[id]?.let { marker ->
                marker.icon = openStopIcon
                // Draw the open stop above its neighbours
                mapView.overlays.remove(marker)
                mapView.overlays.add(mapView.overlays.size - 1, marker)
            }
        }
        highlighted = openStopId
        mapView.invalidate()
    }

    // Buses, estimated from the timetable since they have no GPS yet, moved every few seconds
    var busTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(BUS_REFRESH_MS)
                busTime = LocalDateTime.now()
            }
        }
    }
    val buses = remember(busTime, selectedLine) {
        container.busEstimator.estimate(busTime).filter { selectedLine == null || it.line.id == selectedLine.id }
    }
    val busIcons = remember { mutableMapOf<String, BitmapDrawable>() }
    val busMarkers = remember { mutableListOf<Marker>() }
    // Also keyed on what rebuilds the overlays, since a rebuild clears the bus markers
    LaunchedEffect(buses, selectedLine, user, stopIcon, language) {
        val currentOverlays = mapView.overlays
        val insertIndex = (currentOverlays.size - 1).coerceAtLeast(0)

        // Reuse existing markers to avoid object churn every 5 seconds
        while (busMarkers.size < buses.size) {
            val marker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            busMarkers.add(marker)
            currentOverlays.add(insertIndex, marker)
        }
        while (busMarkers.size > buses.size) {
            val excess = busMarkers.removeAt(busMarkers.size - 1)
            currentOverlays.remove(excess)
        }

        buses.forEachIndexed { index, bus ->
            val marker = busMarkers[index]
            if (!currentOverlays.contains(marker)) {
                currentOverlays.add((currentOverlays.size - 1).coerceAtLeast(0), marker)
            }
            marker.position = GeoPoint(bus.lat, bus.lng)
            marker.icon = busIcons.getOrPut(bus.line.id) { busIcon(context, bus.line) }
            marker.title = "${bus.line.number} → ${bus.direction.headsign.get(language)}"
            marker.setOnMarkerClickListener { _, _ ->
                onOpenLine(bus.line.id, bus.direction.id)
                true
            }
        }
        mapView.invalidate()
    }

    // Camera: focus a stop if one was passed in, otherwise fit the shown stops
    LaunchedEffect(lineFilter) {
        val focus = data.stop(initialStopId)
        if (focus != null && lineFilter == initialLineId) {
            mapView.whenLaidOut {
                mapView.controller.setZoom(16.0)
                mapView.controller.setCenter(GeoPoint(focus.lat, focus.lng))
            }
        } else {
            val line = data.line(lineFilter)
            val points = (line?.let { l -> l.directions.flatMap { d -> data.directionStops(l, d.id) } } ?: data.stops)
                .map { GeoPoint(it.lat, it.lng) }
            if (points.size > 1) {
                mapView.whenLaidOut {
                    mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 96)
                }
            }
        }
    }

    val locate: () -> Unit = {
        locating = true
        locationMessage = null
        scope.launch {
            try {
                val coords = container.location.current()
                user = coords
                mapView.controller.animateTo(GeoPoint(coords.lat, coords.lng), 16.0, 800L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocationDisabledException) {
                locationMessage = R.string.location_unsupported
            } catch (e: Exception) {
                locationMessage = R.string.location_error
            } finally {
                locating = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            locate()
        } else {
            locationMessage = R.string.location_denied
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LineFilters(
            lines = data.lines,
            selected = lineFilter,
            onSelect = { lineFilter = it },
            onOpenLine = onOpenLine,
        )
        // osmdroid doesn't clip its drawing to the view, so without this the routes spill
        // over the line filters and the status bar, worst of all while pinch zooming
        Box(modifier = Modifier.weight(1f).clipToBounds()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            Surface(
                onClick = {
                    if (container.location.hasPermission()) {
                        locate()
                    } else {
                        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                },
                enabled = !locating,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Astika.colors.border),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = stringResource(R.string.my_location),
                        tint = if (locating) Astika.colors.muted else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (buses.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Astika.colors.border),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp, end = 72.dp),
                ) {
                    Text(
                        stringResource(R.string.buses_estimated),
                        fontSize = 12.sp,
                        color = Astika.colors.muted,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            val openStop = data.stop(openStopId)
            if (openStop != null) {
                StopSheet(
                    stop = openStop,
                    onClose = { openStopId = null },
                    onOpenStop = { onOpenStop(openStop.id) },
                    onOpenLine = onOpenLine,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                locationMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Astika.colors.border),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = 28.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(stringResource(msg), fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LineFilters(
    lines: List<Line>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onOpenLine: ((String, String?) -> Unit)? = null,
) {
    var showNames by rememberSaveable { mutableStateOf(false) }
    val selectedLine = remember(lines, selected) { lines.firstOrNull { it.id == selected } }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selected == null,
                            onClick = { onSelect(null) },
                            contentPadding = PaddingValues(horizontal = 14.dp),
                        ) {
                            Text(
                                stringResource(R.string.all_filter),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(lines, key = { it.id }) { line ->
                        FilterChip(
                            selected = selected == line.id,
                            onClick = { onSelect(if (selected == line.id) null else line.id) },
                            contentPadding = PaddingValues(horizontal = if (showNames) 10.dp else 6.dp),
                        ) {
                            if (showNames) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    LineBadge(line, size = BadgeSize.SM)
                                    Text(
                                        text = line.name.text(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            } else {
                                LineBadge(line, size = BadgeSize.MD)
                            }
                        }
                    }
                }

                Surface(
                    onClick = { showNames = !showNames },
                    shape = RoundedCornerShape(12.dp),
                    color = if (showNames) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface,
                    contentColor = if (showNames) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, if (showNames) MaterialTheme.colorScheme.onBackground else Astika.colors.border),
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .defaultMinSize(minWidth = 40.dp)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(if (showNames) R.string.view_numbers else R.string.view_names),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !showNames && selectedLine != null) {
                if (selectedLine != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .then(
                                    if (onOpenLine != null) {
                                        Modifier.clickable { onOpenLine(selectedLine.id, null) }
                                    } else Modifier
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            LineBadge(selectedLine, size = BadgeSize.SM)
                            Text(
                                text = selectedLine.name.text(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            )
                            IconButton(
                                onClick = { onSelect(null) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.all_filter),
                                    tint = Astika.colors.muted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    content: @Composable () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, if (selected) bg else Astika.colors.border),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .defaultMinSize(minWidth = 40.dp)
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun StopSheet(
    stop: Stop,
    onClose: () -> Unit,
    onOpenStop: () -> Unit,
    onOpenLine: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = appContainer()
    val context = LocalContext.current
    val now = rememberNow()
    val departures = container.schedule.nextDepartures(stop.id, now, 3)
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Astika.colors.border),
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
            .fillMaxWidth()
            .heightIn(max = 440.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stop.name.text(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.stop_code, stop.id), fontSize = 13.sp, color = Astika.colors.muted)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
            Column(modifier = Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DepartureList(departures, now) { onOpenLine(it.line.id, it.direction.id) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenStop,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(stringResource(R.string.all_departures), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { openWalkingDirections(context, stop) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_route), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.directions), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/** Walking directions to a stop in the phone's maps app; falls back to just showing the point */
private fun openWalkingDirections(context: Context, stop: Stop) {
    val navigation = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${stop.lat},${stop.lng}&mode=w"))
    val point = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${stop.lat},${stop.lng}?q=${stop.lat},${stop.lng}(${Uri.encode(stop.name.el)})"))
    try {
        context.startActivity(navigation)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(point)
        } catch (none: ActivityNotFoundException) {
            // No maps app at all; nothing to open
        }
    }
}

private fun routeOverlay(map: MapView, line: Line, points: List<GeoPoint>): Polyline =
    Polyline(map).apply {
        setPoints(points)
        outlinePaint.color = line.color.toColor().toArgb()
        outlinePaint.strokeWidth = 6f * map.context.resources.displayMetrics.density
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        setOnClickListener { _, _, _ -> false }
    }

private fun addUserOverlays(map: MapView, coords: Coords) {
    val center = GeoPoint(coords.lat, coords.lng)
    val blue = 0xFF2563EB.toInt()
    map.overlays.add(
        Polygon(map).apply {
            setPoints(Polygon.pointsAsCircle(center, coords.accuracy.toDouble().coerceAtLeast(10.0)))
            fillPaint.color = (blue and 0x00FFFFFF) or (0x1F shl 24)
            outlinePaint.color = blue
            outlinePaint.strokeWidth = 1f * map.context.resources.displayMetrics.density
            setOnClickListener { _, _, _ -> false }
        },
    )
    map.overlays.add(
        Marker(map).apply {
            position = center
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = dotIcon(map.context, fill = blue, ring = 0xFFFFFFFF.toInt(), radiusDp = 7f)
            setOnMarkerClickListener { _, _ -> true }
        },
    )
}

/** How often the estimated buses move */
private const val BUS_REFRESH_MS = 5_000L

/** A bus: a circle in the line's colour with its number, outlined in white */
private fun busIcon(context: Context, line: Line): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val r = 13f * density
    val outline = 2.5f * density
    val size = ((r + outline) * 2).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(c, c, r + outline, paint)
    paint.color = line.color.toColor().toArgb()
    canvas.drawCircle(c, c, r, paint)
    paint.color = line.textColor.toColor().toArgb()
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = (if (line.number.length > 2) 10f else 12f) * density
    // Centre the digits vertically
    val y = c - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(line.number, c, y, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/** Stop pins, green like OASTH's */
private const val STOP_GREEN = 0xFF1FA155.toInt()

/** A map pin: a round head with a bus drawn in it and a point at the bottom, outlined in white */
private fun pinIcon(context: Context, fill: Int, glyph: Int, radiusDp: Float): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val r = radiusDp * density
    val outline = 2f * density
    val tip = r * 0.75f
    val width = ((r + outline) * 2).toInt()
    val height = (r * 2 + tip + outline * 2).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f
    val cy = r + outline

    val shape = Path().apply {
        addCircle(cx, cy, r, Path.Direction.CW)
        moveTo(cx - r * 0.62f, cy + r * 0.78f)
        lineTo(cx, cy + r + tip)
        lineTo(cx + r * 0.62f, cy + r * 0.78f)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // White outline first, then the fill on top of it
    paint.style = Paint.Style.FILL_AND_STROKE
    paint.strokeWidth = outline * 2
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawPath(shape, paint)
    paint.style = Paint.Style.FILL
    paint.color = fill
    canvas.drawPath(shape, paint)

    // The bus: body, windscreen and headlights in the pin colour, wheels below
    paint.color = glyph
    canvas.drawRoundRect(RectF(cx - r * 0.46f, cy - r * 0.56f, cx + r * 0.46f, cy + r * 0.42f), r * 0.14f, r * 0.14f, paint)
    canvas.drawRect(RectF(cx - r * 0.34f, cy + r * 0.40f, cx - r * 0.18f, cy + r * 0.58f), paint)
    canvas.drawRect(RectF(cx + r * 0.18f, cy + r * 0.40f, cx + r * 0.34f, cy + r * 0.58f), paint)
    paint.color = fill
    canvas.drawRoundRect(RectF(cx - r * 0.33f, cy - r * 0.42f, cx + r * 0.33f, cy - r * 0.04f), r * 0.06f, r * 0.06f, paint)
    canvas.drawCircle(cx - r * 0.24f, cy + r * 0.2f, r * 0.08f, paint)
    canvas.drawCircle(cx + r * 0.24f, cy + r * 0.2f, r * 0.08f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun dotIcon(context: Context, fill: Int, ring: Int, radiusDp: Float): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val stroke = 3f * density
    val r = radiusDp * density
    val size = ((r + stroke) * 2).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.FILL
    paint.color = fill
    canvas.drawCircle(size / 2f, size / 2f, r, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = stroke
    paint.color = ring
    canvas.drawCircle(size / 2f, size / 2f, r, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun MapView.whenLaidOut(block: () -> Unit) {
    if (isLayoutOccurred) block() else addOnFirstLayoutListener { _, _, _, _, _ -> block() }
}

