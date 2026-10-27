package com.kastoriadevs.astikoktel.app.ui.line

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.domain.Schedule
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.common.toColor
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.BadgeSize
import com.kastoriadevs.astikoktel.app.ui.components.HeaderTitle
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.components.ScreenHeader
import com.kastoriadevs.astikoktel.app.ui.components.Segmented
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import kotlin.math.ceil

/** Every stop of a direction, when the next bus reaches each, and where the buses are between them */
@Composable
fun RouteStopsScreen(
    lineId: String,
    initialDirId: String?,
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenStop: (String) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    StatusBarIcons(darkIcons = !isAppDark())
    val container = appContainer()
    val data = container.transit.data
    val schedule = container.schedule
    val line = data.line(lineId) ?: return
    val now = rememberNow()
    val favorites by appVm.favorites.collectAsStateWithLifecycle()

    var dirId by rememberSaveable { mutableStateOf(initialDirId ?: line.directions.first().id) }
    val direction = line.directions.firstOrNull { it.id == dirId } ?: line.directions.first()
    val lineColor = line.color.toColor()
    val stops = direction.stops
    val arrivals = schedule.nextArrivals(line, direction, now)
    val positions = schedule.positionsOnRoute(line, direction, now)
    val nowMin = Schedule.minutesOfDay(now)
    // Other lines at each stop, for changing buses
    val others = remember(line.id, direction.id) {
        stops.map { s -> data.linesAtStop(s.stop).filter { it.id != line.id } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            onBack = onBack,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineBadge(line, BadgeSize.LG)
                    HeaderTitle(stringResource(R.string.stops))
                }
            },
            subtitle = stringResource(R.string.stops_count, stops.size) +
                (stops.lastOrNull()?.offset?.takeIf { it > 0 }?.let { " · " + stringResource(R.string.trip_minutes, it) } ?: ""),
            trailing = {
                IconButton(onClick = { onOpenMap(line.id) }) {
                    Icon(
                        painterResource(R.drawable.ic_map),
                        contentDescription = stringResource(R.string.show_on_map),
                        tint = Astika.colors.link,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            below = {
                if (line.directions.size > 1) {
                    Segmented(
                        options = line.directions.map { it.id to stringResource(R.string.towards_cap, it.headsign.text()) },
                        selected = direction.id,
                        onSelect = { dirId = it },
                    )
                }
            },
        )

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.next_bus_each_stop),
                fontSize = 13.sp,
                color = Astika.colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            AppCard(contentPadding = 0.dp) {
                Column {
                    stops.forEachIndexed { i, ds ->
                        val stop = data.stop(ds.stop) ?: return@forEachIndexed
                        val first = i == 0
                        val last = i == stops.lastIndex
                        // Buses between the previous stop and this one (or at this one), drawn on this row
                        val busesHere = positions.filter { p -> if (first) p == 0.0 else ceil(p).toInt() == i && p > i - 1 }
                        val surface = MaterialTheme.colorScheme.surface
                        val busColor = MaterialTheme.colorScheme.onSurface
                        if (i > 0) HorizontalDivider(color = Astika.colors.divider, modifier = Modifier.padding(start = 48.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable { onOpenStop(stop.id) }
                                .padding(end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .fillMaxHeight()
                                    .drawBehind {
                                        val cx = size.width / 2
                                        val cy = size.height / 2
                                        val w = 4.dp.toPx()
                                        val r = if (first || last) 8.dp.toPx() else 6.dp.toPx()
                                        // The line runs on through the divider above, so it looks continuous
                                        drawLine(
                                            color = lineColor,
                                            start = Offset(cx, if (first) cy else -1.dp.toPx()),
                                            end = Offset(cx, if (last) cy else size.height),
                                            strokeWidth = w,
                                        )
                                        drawCircle(color = if (first || last) lineColor else surface, radius = r, center = Offset(cx, cy))
                                        if (!first && !last) {
                                            drawCircle(color = lineColor, radius = r - w / 2, center = Offset(cx, cy), style = Stroke(width = w * 0.75f))
                                        }
                                        busesHere.forEach { p ->
                                            // 0 is this row's stop, 1 the previous stop one row up
                                            val back = (i - p).toFloat()
                                            busMarker(Offset(cx, cy - back * size.height), busColor, surface)
                                        }
                                    },
                            )
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stop.name.text(),
                                    fontSize = 15.sp,
                                    fontWeight = if (first || last) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (stop.id in favorites) {
                                    Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.remove_favorite), tint = Astika.colors.star, modifier = Modifier.size(14.dp))
                                }
                                others[i].take(3).forEach { LineBadge(it) }
                            }
                            ArrivalLabel(arrivals.getOrNull(i), nowMin, isLastStop = last)
                        }
                    }
                }
            }
            if (positions.isNotEmpty()) {
                Text(
                    stringResource(R.string.buses_estimated),
                    fontSize = 12.sp,
                    color = Astika.colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Within the hour: minutes, with the clock time under them. Later: the clock time. None left today: a dash. */
@Composable
private fun ArrivalLabel(arrival: Int?, nowMin: Int, isLastStop: Boolean) {
    if (arrival == null) {
        Text("–", fontSize = 15.sp, color = Astika.colors.muted)
        return
    }
    val mins = arrival - nowMin
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
        if (mins < 60) {
            Text(
                if (mins <= 0) stringResource(R.string.now) else stringResource(R.string.in_min, mins),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(Schedule.formatMinutes(arrival), fontSize = 12.sp, color = Astika.colors.muted)
        } else {
            Text(
                Schedule.formatMinutes(arrival),
                fontSize = 15.sp,
                fontWeight = if (isLastStop) FontWeight.Medium else FontWeight.Normal,
                color = Astika.colors.ink2,
            )
        }
    }
}
