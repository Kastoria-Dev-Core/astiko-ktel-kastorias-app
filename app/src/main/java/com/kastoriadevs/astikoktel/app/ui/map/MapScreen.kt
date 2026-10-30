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
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.kastoriadevs.astikoktel.app.ui.common.title
import com.kastoriadevs.astikoktel.app.ui.common.toColor
import com.kastoriadevs.astikoktel.app.ui.components.DepartureList
import com.kastoriadevs.astikoktel.app.ui.components.BadgeSize
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import com.kastoriadevs.astikoktel.app.ui.theme.Lake
import com.kastoriadevs.astikoktel.app.ui.theme.RouteYellow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private val KASTORIA = GeoPoint(40.5196, 21.266)

/** How far past the outermost stops the map can be panned, in degrees (about 8 km) */
private const val AREA_MARGIN = 0.07

/** The map opens on the town at this zoom */
private const val TOWN_ZOOM = 14.5

/** Below this zoom the buses are drawn small, with just their number */
private const val BUS_SMALL_ZOOM = 14.0

/** The + and − buttons above the location button. Off for now, pinching is enough; set to true to bring them back. */
private const val SHOW_ZOOM_BUTTONS = false

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

/**
 * The map: every stop, the lines along the streets, and the buses where the timetable says they are.
 * [initialLineId] shows just that line, [initialStopId] opens that stop's card.
 */
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
            // One copy of the world, and no panning far from the network: fewer tiles to load and draw
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            val lats = data.stops.map { it.lat }
            val lngs = data.stops.map { it.lng }
            if (lats.isNotEmpty()) {
                setScrollableAreaLimitDouble(
                    BoundingBox(lats.max() + AREA_MARGIN, lngs.max() + AREA_MARGIN, lats.min() - AREA_MARGIN, lngs.min() - AREA_MARGIN),
                )
            }
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

    // Every stop is drawn by one overlay with two shared pins, instead of 209 markers
    val stopIcon = remember { pinIcon(context, fill = Lake.toArgb(), glyph = 0xFFFFFFFF.toInt(), radiusDp = 11f) }
    val openStopIcon = remember { pinIcon(context, fill = RouteYellow.toArgb(), glyph = 0xFF10212B.toInt(), radiusDp = 14f) }
    val language = currentLanguage()
    val stopGeoPoints = remember { data.stops.associate { it.id to GeoPoint(it.lat, it.lng) } }
    val stopsOverlay = remember {
        StopsOverlay(context.resources.displayMetrics.density, stopIcon.bitmap, openStopIcon.bitmap) { openStopId = it }
    }
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

    // Overlays are rebuilt when what they show changes. Opening a stop only redraws (below).
    LaunchedEffect(selectedLine, user) {
        val lines = selectedLine?.let { listOf(it) } ?: data.lines
        mapView.overlays.clear()
        // Added first so everything above it gets taps first; a tap on empty map closes the sheet
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
        stopsOverlay.stops = shownStops.map { it.id to (stopGeoPoints[it.id] ?: GeoPoint(it.lat, it.lng)) }
        mapView.overlays.add(stopsOverlay)
        mapView.overlays.add(CopyrightOverlay(context))
        mapView.invalidate()
    }

    LaunchedEffect(openStopId) {
        stopsOverlay.selectedId = openStopId
        mapView.invalidate()
    }

    // Buses, estimated from the timetable since they have no GPS yet. They are moved every few seconds
    // straight on the map, so the screen itself doesn't recompose each time.
    var hasBuses by remember { mutableStateOf(false) }
    val busIcons = remember { mutableMapOf<String, BitmapDrawable>() }
    val busMarkers = remember { mutableListOf<Marker>() }
    // Swap to the small or big buses as soon as the zoom crosses over, not at the next move
    DisposableEffect(mapView) {
        var wasSmall = mapView.zoomLevelDouble < BUS_SMALL_ZOOM
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = false

            override fun onZoom(event: ZoomEvent?): Boolean {
                val small = mapView.zoomLevelDouble < BUS_SMALL_ZOOM
                if (small != wasSmall) {
                    wasSmall = small
                    busMarkers.forEach { marker ->
                        val line = marker.relatedObject as? Line ?: return@forEach
                        marker.icon = busIcons.getOrPut("${line.id}-$small") { busIcon(context, line, small) }
                    }
                    mapView.invalidate()
                }
                return false
            }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }
    // Restarted with the overlays, since rebuilding them drops the bus markers
    LaunchedEffect(lifecycleOwner, selectedLine, user, language) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val buses = container.busEstimator.estimate(LocalDateTime.now())
                    .filter { selectedLine == null || it.line.id == selectedLine.id }
                val overlays = mapView.overlays
                while (busMarkers.size < buses.size) {
                    busMarkers.add(Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) })
                }
                while (busMarkers.size > buses.size) overlays.remove(busMarkers.removeAt(busMarkers.size - 1))
                val small = mapView.zoomLevelDouble < BUS_SMALL_ZOOM
                buses.forEachIndexed { index, bus ->
                    val marker = busMarkers[index]
                    // Above the stops, below the copyright notice
                    if (!overlays.contains(marker)) overlays.add((overlays.size - 1).coerceAtLeast(0), marker)
                    marker.position = GeoPoint(bus.lat, bus.lng)
                    marker.relatedObject = bus.line
                    marker.icon = busIcons.getOrPut("${bus.line.id}-$small") { busIcon(context, bus.line, small) }
                    marker.title = "${bus.line.number} → ${bus.direction.headsign.get(language)}"
                    marker.setOnMarkerClickListener { _, _ ->
                        onOpenLine(bus.line.id, bus.direction.id)
                        true
                    }
                }
                hasBuses = buses.isNotEmpty()
                mapView.invalidate()
                delay(BUS_REFRESH_MS)
            }
        }
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
            if (line == null) {
                // All lines: the town, not the whole network out to the villages
                mapView.whenLaidOut {
                    mapView.controller.setZoom(TOWN_ZOOM)
                    mapView.controller.setCenter(KASTORIA)
                }
            } else {
                val points = line.directions.flatMap { d -> data.directionStops(line, d.id) }.map { GeoPoint(it.lat, it.lng) }
                if (points.size > 1) {
                    mapView.whenLaidOut {
                        mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 96)
                    }
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
        )
        // osmdroid doesn't clip its drawing to the view, so without this the routes spill
        // over the line filters and the status bar, worst of all while pinch zooming
        Box(modifier = Modifier.weight(1f).clipToBounds()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            // Top left: a way into the chosen line's timetable, and the note that the buses are estimated.
            // The timetable button is clickable, so it gets the 48dp touch target around it; centring keeps
            // the note on the same line instead of sitting higher.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 10.dp, end = 12.dp),
            ) {
                if (selectedLine != null) {
                    Surface(
                        onClick = { onOpenLine(selectedLine.id, null) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Astika.colors.border),
                    ) {
                        Text(
                            stringResource(R.string.line_timetable) + " ›",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Astika.colors.link,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                if (hasBuses) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, Astika.colors.border),
                    ) {
                        Text(
                            stringResource(R.string.buses_estimated_short),
                            fontSize = 12.sp,
                            color = Astika.colors.muted,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
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
                            // Leaves room for the location button on the right
                            .padding(start = 12.dp, end = 80.dp, bottom = 20.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(stringResource(msg), fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                    }
                }

                // Zoom buttons for those who don't pinch, stacked in one pill just above the location button
                if (SHOW_ZOOM_BUTTONS) Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Astika.colors.border),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 84.dp)
                        .width(52.dp),
                ) {
                    Column {
                        IconButton(onClick = { mapView.controller.zoomIn() }, modifier = Modifier.size(52.dp)) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.zoom_in),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Box(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(Astika.colors.border))
                        IconButton(onClick = { mapView.controller.zoomOut() }, modifier = Modifier.size(52.dp)) {
                            // The core icons have no minus, so a bar as wide and thick as the plus's
                            Box(
                                modifier = Modifier
                                    .size(width = 14.dp, height = 2.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }

                // Bottom right, where the thumb is, like OSETH's. Hidden while a stop card covers that corner.
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
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 20.dp)
                        .size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_my_location),
                            contentDescription = stringResource(R.string.my_location),
                            tint = if (locating) Astika.colors.muted else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
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
) {
    var showNames by rememberSaveable { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // The row fades out before the list button, so a badge cut in half reads as "more this way"
                val fadeTo = MaterialTheme.colorScheme.background
                Box(modifier = Modifier.weight(1f)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
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
                            val on = selected == line.id
                            val toggle = { onSelect(if (on) null else line.id) }
                            val lineColor = line.color.toColor()
                            if (showNames) {
                                // One pill per line, no badge inside it: the number in the line's colour, then the name.
                                // Chosen, the whole pill takes the line's colour.
                                val pill = RoundedCornerShape(50)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clip(pill)
                                        .background(if (on) lineColor else MaterialTheme.colorScheme.surface)
                                        .border(1.dp, if (on) lineColor else Astika.colors.border, pill)
                                        .clickable(onClick = toggle)
                                        .padding(horizontal = 14.dp),
                                ) {
                                    Text(
                                        line.number.padStart(2, '0'),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (on) line.textColor.toColor() else lineColor,
                                    )
                                    Text(
                                        text = line.name.text(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (on) line.textColor.toColor() else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                }
                            } else {
                                // Just the badge, nothing drawn around it. The chosen one gets a short bar
                                // underneath, like a tab, and the others step back. The top padding matches the bar
                                // (4dp gap + 3dp) so the badge is the centre and lines up with the chip and button.
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = toggle)
                                        .padding(start = 4.dp, end = 4.dp, top = 11.dp, bottom = 4.dp)
                                        .alpha(if (selected == null || on) 1f else 0.45f),
                                ) {
                                    LineBadge(line, size = BadgeSize.MD)
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .width(20.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (on) lineColor else Color.Transparent),
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(24.dp)
                            .height(56.dp)
                            .background(Brush.horizontalGradient(listOf(fadeTo.copy(alpha = 0f), fadeTo))),
                    )
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
                    Text(stop.title(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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

/**
 * A bus, drawn like OSETH's: a white disc ringed in the line's colour, with a bus and the line number
 * inside in that colour. A thin white halo keeps it readable on top of its own route.
 */
private fun busIcon(context: Context, line: Line, small: Boolean = false): BitmapDrawable {
    if (small) return smallBusIcon(context, line)
    val density = context.resources.displayMetrics.density
    val r = 17f * density
    val ring = 2.5f * density
    val halo = 1.5f * density
    val size = ((r + halo) * 2).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val color = line.color.toColor().toArgb()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(c, c, r + halo, paint)
    paint.color = color
    canvas.drawCircle(c, c, r, paint)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(c, c, r - ring, paint)

    // The bus in the upper half: body, windscreen, headlights, wheels
    paint.color = color
    canvas.drawRoundRect(RectF(c - r * 0.36f, c - r * 0.66f, c + r * 0.36f, c - r * 0.04f), r * 0.12f, r * 0.12f, paint)
    canvas.drawRect(RectF(c - r * 0.28f, c - r * 0.06f, c - r * 0.14f, c + r * 0.06f), paint)
    canvas.drawRect(RectF(c + r * 0.14f, c - r * 0.06f, c + r * 0.28f, c + r * 0.06f), paint)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawRoundRect(RectF(c - r * 0.26f, c - r * 0.56f, c + r * 0.26f, c - r * 0.32f), r * 0.05f, r * 0.05f, paint)
    canvas.drawCircle(c - r * 0.2f, c - r * 0.17f, r * 0.06f, paint)
    canvas.drawCircle(c + r * 0.2f, c - r * 0.17f, r * 0.06f, paint)

    // The line number underneath, like the badges
    paint.color = color
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    val number = line.number.padStart(2, '0')
    paint.textSize = (if (number.length > 2) 9f else 10.5f) * density
    canvas.drawText(number, c, c + r * 0.6f, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/** A bus seen from further out: the same disc, smaller, with only the line number */
private fun smallBusIcon(context: Context, line: Line): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val r = 11f * density
    val ring = 2f * density
    val halo = 1.25f * density
    val size = ((r + halo) * 2).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val color = line.color.toColor().toArgb()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(c, c, r + halo, paint)
    paint.color = color
    canvas.drawCircle(c, c, r, paint)
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(c, c, r - ring, paint)
    paint.color = color
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    val number = line.number.padStart(2, '0')
    paint.textSize = (if (number.length > 2) 7.5f else 9f) * density
    canvas.drawText(number, c, c - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDrawable(context.resources, bitmap)
}

/** A map pin like OSETH's: a round head with a bus shelter drawn in it and a point at the bottom, outlined in white */
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

    // The shelter: a roof wider at the eaves, a see-through back panel, two posts and a bench
    paint.color = (glyph and 0x00FFFFFF) or (0x73 shl 24)
    canvas.drawRect(RectF(cx - r * 0.38f, cy - r * 0.36f, cx + r * 0.38f, cy + r * 0.04f), paint)
    paint.color = glyph
    val roof = Path().apply {
        moveTo(cx - r * 0.44f, cy - r * 0.6f)
        lineTo(cx + r * 0.44f, cy - r * 0.6f)
        lineTo(cx + r * 0.62f, cy - r * 0.38f)
        lineTo(cx - r * 0.62f, cy - r * 0.38f)
        close()
    }
    canvas.drawPath(roof, paint)
    canvas.drawRect(RectF(cx - r * 0.52f, cy - r * 0.4f, cx - r * 0.38f, cy + r * 0.55f), paint)
    canvas.drawRect(RectF(cx + r * 0.38f, cy - r * 0.4f, cx + r * 0.52f, cy + r * 0.55f), paint)
    canvas.drawRect(RectF(cx - r * 0.38f, cy + r * 0.14f, cx + r * 0.38f, cy + r * 0.27f), paint)
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


/**
 * All the stops in one overlay: pins from street level up, small dots further out where 209 pins would
 * cover the map. The open stop always gets its big pin, drawn last so it sits on top.
 */
private class StopsOverlay(
    density: Float,
    private val pin: Bitmap,
    private val openPin: Bitmap,
    private val onTap: (String) -> Unit,
) : Overlay() {
    /** Stop ids and where to draw them; set again when the line filter changes */
    var stops: List<Pair<String, GeoPoint>> = emptyList()

    /** The stop whose card is open, drawn with [openPin] at any zoom */
    var selectedId: String? = null

    private val point = Point()
    private val dotRadius = 3f * density
    private val dotRing = 1.25f * density
    private val tapRadius = 22f * density
    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Lake.toArgb() }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    private fun showPins(map: MapView) = map.zoomLevelDouble >= PIN_ZOOM

    /** Far out the routes say enough; the stops would only be a string of beads along them */
    private fun showStops(map: MapView) = map.zoomLevelDouble >= DOT_ZOOM

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = map.projection
        val pins = showPins(map)
        val stopsShown = showStops(map)
        val margin = pin.height
        var selected: GeoPoint? = null
        for ((id, position) in stops) {
            if (id == selectedId) {
                selected = position
                continue
            }
            if (!stopsShown) continue
            projection.toPixels(position, point)
            if (point.x < -margin || point.y < -margin || point.x > map.width + margin || point.y > map.height + margin) continue
            if (pins) {
                // The pin's point sits on the stop
                canvas.drawBitmap(pin, point.x - pin.width / 2f, point.y - pin.height.toFloat(), null)
            } else {
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), dotRadius + dotRing, dotStroke)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), dotRadius, dotFill)
            }
        }
        selected?.let {
            projection.toPixels(it, point)
            canvas.drawBitmap(openPin, point.x - openPin.width / 2f, point.y - openPin.height.toFloat(), null)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        if (!showStops(map)) return false
        val projection = map.projection
        // A pin is tapped on its head, above the stop; a dot on the stop itself
        val lift = if (showPins(map)) pin.height * 0.6f else 0f
        var best: String? = null
        var bestDistance = tapRadius * tapRadius
        for ((id, position) in stops) {
            projection.toPixels(position, point)
            val dx = e.x - point.x
            val dy = e.y - (point.y - lift)
            val d = dx * dx + dy * dy
            if (d < bestDistance) {
                bestDistance = d
                best = id
            }
        }
        val id = best ?: return false
        onTap(id)
        return true
    }

    private companion object {
        /** From this zoom up the stops are pins; below it, dots */
        const val PIN_ZOOM = 15.0

        /** Below this zoom the stops are not drawn at all */
        const val DOT_ZOOM = 13.5
    }
}
