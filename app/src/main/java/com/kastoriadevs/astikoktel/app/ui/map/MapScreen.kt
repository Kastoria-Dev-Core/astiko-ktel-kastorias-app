package com.kastoriadevs.astikoktel.app.ui.map

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kastoriadevs.astikoktel.app.BuildConfig
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
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import com.kastoriadevs.astikoktel.app.ui.theme.RouteYellow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private val KASTORIA = GeoPoint(40.5196, 21.266)

/**
 * CARTO's dark basemap (OpenStreetMap data), used in dark theme.
 * CARTO watermarks tiles requested without an API key, so the key from
 * local.properties (or the CARTO_API_KEY secret on CI) goes on every tile URL.
 * The cache name includes "Keyed" so watermarked tiles cached by older builds aren't reused.
 */
private val CartoDark: ITileSource = object : OnlineTileSourceBase(
    "CartoDarkMatterKeyed",
    0,
    19,
    256,
    ".png",
    arrayOf("https://basemaps.cartocdn.com/rastertiles/dark_all/"),
    "© OpenStreetMap contributors © CARTO",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val url = baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) +
            "/" + MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
        return if (BuildConfig.CARTO_API_KEY.isEmpty()) url else "$url?key=${BuildConfig.CARTO_API_KEY}"
    }
}

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
        mapView.setTileSource(if (dark) CartoDark else TileSourceFactory.MAPNIK)
    }

    // Two icons shared by every stop marker instead of one bitmap per marker
    val stopRing = Astika.colors.stopRing.toArgb()
    val stopFill = Astika.colors.stopFill.toArgb()
    val stopIcon = remember(stopRing, stopFill) { dotIcon(context, fill = stopFill, ring = stopRing, radiusDp = 7f) }
    val openStopIcon = remember(stopRing) { dotIcon(context, fill = RouteYellow.toArgb(), ring = stopRing, radiusDp = 10f) }
    val language = currentLanguage()
    val markers = remember { mutableMapOf<String, Marker>() }

    val selectedLine = data.line(lineFilter)
    val shownStops = remember(selectedLine) {
        if (selectedLine != null) {
            data.stops.filter { s -> selectedLine.directions.any { d -> d.stops.any { it.stop == s.id } } }
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
            line.directions.forEach { d -> mapView.overlays.add(routeOverlay(mapView, line, data.directionStops(line, d.id))) }
        }
        user?.let { addUserOverlays(mapView, it) }
        shownStops.forEach { stop ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(stop.lat, stop.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = if (stop.id == openStopId) openStopIcon else stopIcon
                title = stop.name.get(language)
                setOnMarkerClickListener { _, _ ->
                    openStopId = stop.id
                    true
                }
            }
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
private fun LineFilters(lines: List<Line>, selected: String?, onSelect: (String?) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        LazyRow(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(label = stringResource(R.string.all_filter), on = selected == null, onClick = { onSelect(null) })
            }
            items(lines, key = { it.id }) { line ->
                FilterChip(label = line.name.text(), on = selected == line.id, onClick = { onSelect(line.id) }) {
                    LineBadge(line)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val bg = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface
    val fg = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.dp, if (on) bg else Astika.colors.border),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .padding(start = if (leading != null) 8.dp else 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
    val now = rememberNow()
    val departures = container.schedule.nextDepartures(stop.id, now, 3)
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Astika.colors.border),
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 24.dp)
            .fillMaxWidth()
            .heightIn(max = 420.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stop.name.text(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        container.transit.data.linesAtStop(stop.id).forEach { LineBadge(it) }
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
            Column(modifier = Modifier.padding(end = 10.dp)) {
                DepartureList(departures, now) { onOpenLine(it.line.id, it.direction.id) }
                HorizontalDivider(color = Astika.colors.border)
                Text(
                    text = stringResource(R.string.view_stop) + " →",
                    color = Astika.colors.link,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenStop)
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

private fun routeOverlay(map: MapView, line: Line, stops: List<Stop>): Polyline =
    Polyline(map).apply {
        setPoints(stops.map { GeoPoint(it.lat, it.lng) })
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

