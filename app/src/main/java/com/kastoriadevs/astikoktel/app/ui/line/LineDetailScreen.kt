package com.kastoriadevs.astikoktel.app.ui.line

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.ServiceDay
import com.kastoriadevs.astikoktel.app.domain.Schedule
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.common.labelRes
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.common.toColor
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.BadgeSize
import com.kastoriadevs.astikoktel.app.ui.components.ChipRow
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.components.PlaceholderBanner
import com.kastoriadevs.astikoktel.app.ui.components.ScreenHeader
import com.kastoriadevs.astikoktel.app.ui.components.Segmented
import com.kastoriadevs.astikoktel.app.ui.theme.Astika

/** A line's full timetable from its first stop, for each direction and kind of day, in the current season */
@Composable
fun LineDetailScreen(
    lineId: String,
    initialDirId: String?,
    onBack: () -> Unit,
    onOpenStops: (lineId: String, dirId: String) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    StatusBarIcons(darkIcons = !isAppDark())
    val container = appContainer()
    val data = container.transit.data
    val schedule = container.schedule
    val line = data.line(lineId) ?: return
    val now = rememberNow()
    val today = now.toLocalDate()
    val todayService = schedule.serviceDayFor(today)
    val season = schedule.seasonFor(today)

    var dirId by rememberSaveable { mutableStateOf(initialDirId ?: line.directions.first().id) }
    var service by rememberSaveable { mutableStateOf(todayService) }
    val direction = line.directions.firstOrNull { it.id == dirId } ?: line.directions.first()
    val lineColor = line.color.toColor()
    val lineText = line.textColor.toColor()

    val deps = schedule.departuresFor(line.id, direction.id, service, season)
    val isToday = service == todayService
    val nowMin = Schedule.minutesOfDay(now)
    val nextIdx = if (isToday) deps.indexOfFirst { it >= nowMin } else -1
    val nextMin = deps.getOrNull(nextIdx)
    val doneToday = isToday && deps.isNotEmpty() && nextIdx == -1
    // Today: departed trips are hidden, except the one that just left (greyed out)
    val shown = when {
        !isToday -> deps
        doneToday -> deps.takeLast(1)
        else -> deps.drop(maxOf(0, nextIdx - 1))
    }
    val firstStop = data.stop(direction.stops.firstOrNull()?.stop)

    val listState = rememberLazyListState()
    LaunchedEffect(dirId, service) { listState.scrollToItem(0) }

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
                    Text(
                        line.name.text(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 24.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            subtitle = serviceSummary(deps),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaceholderBanner(data.meta.placeholder)

            ChipRow(
                options = ServiceDay.entries.map { it to stringResource(it.labelRes()) },
                selected = service,
                onSelect = { service = it },
            )

            if (firstStop != null) {
                Text(
                    stringResource(R.string.departures_from, firstStop.name.text()) + " · " + stringResource(season.labelRes()),
                    fontSize = 13.sp,
                    color = Astika.colors.muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            AppCard(contentPadding = 0.dp) {
                if (deps.isEmpty()) {
                    Text(
                        stringResource(R.string.no_service),
                        color = Astika.colors.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 352.dp),
                    ) {
                        itemsIndexed(shown, key = { _, m -> m }) { i, m ->
                            val past = isToday && m < nowMin
                            val next = m == nextMin
                            Column {
                                if (i > 0 && !next && shown.getOrNull(i - 1) != nextMin) {
                                    HorizontalDivider(color = Astika.colors.divider)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = if (next) 52.dp else 46.dp)
                                        .background(if (next) lineColor else Color.Transparent)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = Schedule.formatMinutes(m),
                                        fontSize = if (next) 18.sp else 16.sp,
                                        fontWeight = if (next) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            next -> lineText
                                            past -> Astika.colors.muted.copy(alpha = 0.7f)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    // Only the one that just left and the next one say anything; the rest is just the time
                                    if (past) {
                                        Text(stringResource(R.string.departed_short), fontSize = 12.sp, color = Astika.colors.muted)
                                    } else if (next) {
                                        val mins = m - nowMin
                                        Text(
                                            if (mins <= 0) stringResource(R.string.now) else stringResource(R.string.in_min, mins),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = lineText,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (doneToday) {
                Text(stringResource(R.string.no_more_today), color = Astika.colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
            } else if (deps.isNotEmpty()) {
                Text(
                    stringResource(R.string.last_trip_of_day, Schedule.formatMinutes(deps.last())),
                    color = Astika.colors.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            RouteNowCard(
                direction = direction,
                firstName = firstStop?.name?.text().orEmpty(),
                lastName = data.stop(direction.stops.lastOrNull()?.stop)?.name?.text().orEmpty(),
                positions = schedule.positionsOnRoute(line, direction, now),
                lineColor = lineColor,
                onClick = { onOpenStops(line.id, direction.id) },
            )
        }
    }
}

/** "Every 20′ · 07:00 to 20:15", or just the first and last time when the gaps differ */
@Composable
private fun serviceSummary(deps: List<Int>): String? {
    if (deps.isEmpty()) return null
    val span = stringResource(R.string.first_last, Schedule.formatMinutes(deps.first()), Schedule.formatMinutes(deps.last()))
    val gaps = deps.zipWithNext { a, b -> b - a }.toSet()
    return if (gaps.size == 1) stringResource(R.string.every_min, gaps.first()) + " · " + span else span
}

/** The direction as a strip of stops, with the buses the timetable puts on it now */
@Composable
private fun RouteNowCard(
    direction: Direction,
    firstName: String,
    lastName: String,
    positions: List<Double>,
    lineColor: Color,
    onClick: () -> Unit,
) {
    val c = Astika.colors
    val surface = MaterialTheme.colorScheme.surface
    val busColor = MaterialTheme.colorScheme.onSurface
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.on_route_now), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (positions.isEmpty()) stringResource(R.string.no_bus_on_route)
                else pluralStringResource(R.plurals.buses_on_route, positions.size, positions.size),
                fontSize = 13.sp,
                color = c.muted,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
        val stopCount = direction.stops.size
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
        ) {
            val r = 6.dp.toPx()
            val y = size.height / 2
            val left = r
            val right = size.width - r
            fun xAt(p: Double) = if (stopCount < 2) left else (left + (right - left) * (p / (stopCount - 1))).toFloat()
            drawLine(lineColor, Offset(left, y), Offset(right, y), strokeWidth = 4.dp.toPx())
            for (i in 0 until stopCount) {
                val end = i == 0 || i == stopCount - 1
                drawCircle(if (end) lineColor else surface, radius = r, center = Offset(xAt(i.toDouble()), y))
                if (!end) drawCircle(lineColor, radius = r - 1.5.dp.toPx(), center = Offset(xAt(i.toDouble()), y), style = Stroke(3.dp.toPx()))
            }
            positions.forEach { busMarker(Offset(xAt(it), y), busColor, surface) }
        }
        Row {
            Text(firstName, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(lastName, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (positions.isNotEmpty()) {
            Text(stringResource(R.string.buses_estimated), fontSize = 12.sp, color = c.muted)
        }
    }
}

/** A bus on a route line: a dark rounded square with a white outline and a simple bus inside */
internal fun DrawScope.busMarker(center: Offset, fill: Color, outline: Color) {
    val half = 13.dp.toPx()
    val ring = 2.dp.toPx()
    drawRoundRect(outline, Offset(center.x - half - ring, center.y - half - ring), Size((half + ring) * 2, (half + ring) * 2), CornerRadius(half))
    drawRoundRect(fill, Offset(center.x - half, center.y - half), Size(half * 2, half * 2), CornerRadius(half))
    // Body, windscreen, wheels
    val w = 6.5.dp.toPx()
    val h = 7.5.dp.toPx()
    drawRoundRect(outline, Offset(center.x - w, center.y - h), Size(w * 2, h * 1.7f), CornerRadius(2.dp.toPx()))
    drawRect(fill, Offset(center.x - w + 1.5.dp.toPx(), center.y - h + 1.5.dp.toPx()), Size(w * 2 - 3.dp.toPx(), h * 0.75f))
    drawRect(outline, Offset(center.x - w + 1.dp.toPx(), center.y + h * 0.7f), Size(2.5.dp.toPx(), 2.5.dp.toPx()))
    drawRect(outline, Offset(center.x + w - 3.5.dp.toPx(), center.y + h * 0.7f), Size(2.5.dp.toPx(), 2.5.dp.toPx()))
}
