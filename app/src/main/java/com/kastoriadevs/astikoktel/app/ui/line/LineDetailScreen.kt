package com.kastoriadevs.astikoktel.app.ui.line

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.ServiceDay
import com.kastoriadevs.astikoktel.app.domain.Schedule
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.labelRes
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.common.toColor
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.PlaceholderBanner
import com.kastoriadevs.astikoktel.app.ui.components.ScreenHeader
import com.kastoriadevs.astikoktel.app.ui.components.Segmented
import com.kastoriadevs.astikoktel.app.ui.theme.Astika

@Composable
fun LineDetailScreen(
    lineId: String,
    initialDirId: String?,
    onBack: () -> Unit,
    onOpenStop: (String) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    StatusBarIcons(darkIcons = false)
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

    val listState = rememberLazyListState()
    LaunchedEffect(dirId, service) { listState.scrollToItem(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            onBack = onBack,
            container = lineColor,
            contentColor = lineText,
            rounded = true,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 38.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.White)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(line.number, color = lineColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(line.name.text(), fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                }
            },
            subtitle = "${stringResource(season.labelRes())} · ${stringResource(R.string.stops_count, direction.stops.size)}",
            below = {
                Segmented(
                    options = line.directions.map { it.id to "→ ${it.headsign.text()}" },
                    selected = direction.id,
                    onSelect = { dirId = it },
                    tinted = true,
                )
            },
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaceholderBanner(data.meta.placeholder)

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.timetable),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onOpenMap(line.id) }) {
                        Icon(painterResource(R.drawable.ic_map), contentDescription = null, tint = Astika.colors.link, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.show_on_map),
                            color = Astika.colors.link,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Segmented(
                    options = ServiceDay.entries.map { it to stringResource(it.labelRes()) },
                    selected = service,
                    onSelect = { service = it },
                )

                if (deps.isEmpty()) {
                    Text(stringResource(R.string.no_service), color = Astika.colors.muted, fontSize = 14.sp)
                } else {
                    HorizontalDivider(color = Astika.colors.border)
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
                                    HorizontalDivider(color = Astika.colors.border)
                                }
                                Text(
                                    text = Schedule.formatMinutes(m),
                                    fontSize = 17.sp,
                                    fontWeight = if (next) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        next -> lineText
                                        past -> Astika.colors.muted.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (next) lineColor else Color.Transparent)
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Astika.colors.border)
                }
                if (doneToday) {
                    Text(stringResource(R.string.no_more_today), color = Astika.colors.muted, fontSize = 14.sp)
                } else if (deps.isNotEmpty()) {
                    Text(
                        stringResource(R.string.last_trip_of_day, Schedule.formatMinutes(deps.last())),
                        color = Astika.colors.muted,
                        fontSize = 14.sp,
                    )
                }
            }

            AppCard {
                Text(stringResource(R.string.stops), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                val surface = MaterialTheme.colorScheme.surface
                Column {
                    direction.stops.forEachIndexed { i, ds ->
                        val stop = data.stop(ds.stop) ?: return@forEachIndexed
                        val first = i == 0
                        val last = i == direction.stops.lastIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { onOpenStop(stop.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .fillMaxHeight()
                                    .drawBehind {
                                        val cx = size.width / 2
                                        val cy = size.height / 2
                                        val w = 4.dp.toPx()
                                        val r = 7.dp.toPx()
                                        drawLine(
                                            color = lineColor,
                                            start = Offset(cx, if (first) cy else 0f),
                                            end = Offset(cx, if (last) cy else size.height),
                                            strokeWidth = w,
                                        )
                                        drawCircle(color = if (first || last) lineColor else surface, radius = r, center = Offset(cx, cy))
                                        drawCircle(color = lineColor, radius = r - w / 2, center = Offset(cx, cy), style = Stroke(width = w))
                                    },
                            )
                            Text(
                                text = stop.name.text(),
                                fontSize = 16.sp,
                                fontWeight = if (first || last) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            )
                            Text("+${ds.offset}′", fontSize = 14.sp, color = Astika.colors.muted)
                        }
                    }
                }
            }
        }
    }
}
