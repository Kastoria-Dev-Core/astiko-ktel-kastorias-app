package com.kastoriadevs.astikoktel.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.TransitData
import com.kastoriadevs.astikoktel.app.domain.Destination
import com.kastoriadevs.astikoktel.app.domain.Geo
import com.kastoriadevs.astikoktel.app.domain.JourneyOption
import com.kastoriadevs.astikoktel.app.domain.JourneyPlan
import com.kastoriadevs.astikoktel.app.domain.JourneyStatus
import com.kastoriadevs.astikoktel.app.domain.NearbyStop
import com.kastoriadevs.astikoktel.app.domain.Schedule
import com.kastoriadevs.astikoktel.app.ui.common.currentLanguage
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.BadgeSize
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.components.Pill
import com.kastoriadevs.astikoktel.app.ui.theme.Astika

/** How many buses we show before "All buses", to keep the box small */
private const val COLLAPSED_OPTIONS = 3

private val Destination.label: Int
    get() = when (this) {
        Destination.CENTRE -> R.string.destination_centre
        Destination.MANIAKOI -> R.string.destination_maniakoi
        Destination.UNIVERSITY -> R.string.destination_university
    }

private val Destination.icon: Int
    get() = when (this) {
        Destination.CENTRE -> R.drawable.ic_city
        Destination.MANIAKOI -> R.drawable.ic_home
        Destination.UNIVERSITY -> R.drawable.ic_school
    }

/**
 * "Where do you want to go?" on Home: pick a place and see every bus that goes there
 * from a stop near you, the one that comes first at the top.
 */
@Composable
fun JourneySection(
    data: TransitData,
    destination: Destination?,
    nearby: NearbyState,
    plan: JourneyPlan?,
    onDestinationClick: (Destination) -> Unit,
    onLocate: () -> Unit,
    onOpenStop: (String) -> Unit,
) {
    val c = Astika.colors
    // Only the 3 soonest buses, until "All buses". Reset when you pick another destination.
    var showAll by rememberSaveable(destination) { mutableStateOf(false) }

    AppCard(contentPadding = 14.dp) {
        Column {
            // Title centred on the card; the refresh button sits on the right without pushing it off centre
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp),
                ) {
                    Text(
                        stringResource(R.string.journey_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.journey_subtitle),
                        fontSize = 13.sp,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                if (destination != null) {
                    // Find the location again, e.g. after walking somewhere else
                    IconButton(
                        onClick = onLocate,
                        enabled = nearby !is NearbyState.Loading,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.journey_refresh_location),
                            tint = if (nearby is NearbyState.Loading) c.muted else c.link,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                // Sized to their names, so "Πανεπιστήμιο" is never cut short
                Destination.entries.forEach { d ->
                    DestinationTile(d, selected = d == destination, onClick = { onDestinationClick(d) })
                }
            }

            if (destination != null) {
                JourneyBody(data, nearby, plan, showAll, { showAll = !showAll }, onLocate, onOpenStop)
            }
        }
    }
}

@Composable
private fun JourneyBody(
    data: TransitData,
    nearby: NearbyState,
    plan: JourneyPlan?,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onLocate: () -> Unit,
    onOpenStop: (String) -> Unit,
) {
    if (nearby !is NearbyState.Ready || plan == null) {
        val loading = nearby is NearbyState.Loading
        JourneyMessage(
            stringResource(if (loading) R.string.locating else R.string.journey_need_location),
            actionText = if (loading) null else stringResource(R.string.find_nearby),
            onAction = onLocate,
        )
        return
    }

    if (plan.status != JourneyStatus.AlreadyThere && plan.status != JourneyStatus.TooFar) {
        val stop = plan.usefulStop ?: plan.nearestStop
        StopRow(
            stop = stop,
            label = stringResource(if (plan.usefulStop != null) R.string.journey_useful_stop else R.string.journey_nearest_stop),
            onClick = { onOpenStop(stop.stop.id) },
        )
        if (plan.usefulStop != null) {
            Text(
                stringResource(
                    R.string.journey_nearest_no_bus,
                    plan.nearestStop.stop.name.text(),
                    Geo.formatDistance(plan.nearestStop.distanceMeters, currentLanguage()),
                ),
                fontSize = 13.sp,
                color = Astika.colors.muted,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }

    when (plan.status) {
        JourneyStatus.TooFar -> JourneyMessage(stringResource(R.string.journey_too_far))
        JourneyStatus.AlreadyThere -> JourneyMessage(stringResource(R.string.journey_already_there, plan.nearestStop.stop.name.text()))
        JourneyStatus.NoRoute -> JourneyMessage(stringResource(R.string.journey_no_route))
        JourneyStatus.NoMoreBuses -> JourneyMessage(stringResource(R.string.journey_no_more_buses))
        JourneyStatus.Ready -> Column {
            val shown = if (showAll) plan.options else plan.options.take(COLLAPSED_OPTIONS)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                shown.forEachIndexed { index, option ->
                    JourneyOptionRow(
                        option = option,
                        alightName = data.stop(option.ride.alightStopId)?.name?.text() ?: option.ride.alightStopId,
                        highlighted = index == 0,
                        onClick = { onOpenStop(option.ride.board.stop.id) },
                    )
                }
            }
            if (plan.options.size > COLLAPSED_OPTIONS) {
                TextButton(onClick = onToggleShowAll, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(
                        if (showAll) stringResource(R.string.journey_show_less)
                        else stringResource(R.string.journey_show_all, plan.options.size),
                        color = Astika.colors.link,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        if (showAll) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Astika.colors.link,
                    )
                }
            }
            Text(
                stringResource(R.string.journey_note),
                fontSize = 12.sp,
                color = Astika.colors.muted,
                modifier = Modifier.padding(start = 4.dp, top = if (plan.options.size > COLLAPSED_OPTIONS) 0.dp else 8.dp),
            )
        }
    }
}

/** One of the three destinations, as a text chip: dark when selected */
@Composable
private fun DestinationTile(destination: Destination, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Astika.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) c.chipOn else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) c.chipOn else c.border, RoundedCornerShape(20.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            stringResource(destination.label),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) c.chipOnText else c.ink2,
            maxLines = 1,
        )
    }
}

/** The stop to walk to: a small row with its name and distance. Tap to open the stop. */
@Composable
private fun StopRow(stop: NearbyStop, label: String, onClick: () -> Unit) {
    val c = Astika.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(painterResource(R.drawable.ic_walk), contentDescription = null, tint = c.link, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Text(label, fontSize = 12.sp, color = c.muted)
            Text(stop.stop.name.text(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Pill(Geo.formatDistance(stop.distanceMeters, currentLanguage()))
    }
}

/** A tinted box with a short message and, optionally, a button */
@Composable
private fun JourneyMessage(text: String, actionText: String? = null, onAction: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Astika.colors.link, modifier = Modifier.size(20.dp))
        Text(
            text,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (actionText != null) {
            TextButton(onClick = onAction) { Text(actionText, color = Astika.colors.link, fontWeight = FontWeight.Bold) }
        }
    }
}

/** One bus that takes you there. The first (soonest) one is filled. */
@Composable
private fun JourneyOptionRow(option: JourneyOption, alightName: String, highlighted: Boolean, onClick: () -> Unit) {
    val c = Astika.colors
    val board = option.ride.board
    val strong = if (highlighted) c.heroText else MaterialTheme.colorScheme.onSurface
    val soft = if (highlighted) c.heroMuted else c.muted
    val number = if (highlighted) c.heroText else c.link

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) c.hero else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        LineBadge(option.ride.line, BadgeSize.MD)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            if (highlighted) {
                Text(stringResource(R.string.journey_fastest), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = soft)
            }
            Text(
                option.ride.line.name.text(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = strong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // "Get on: Καρυδιά  🚶18′": a long stop name gets "…", the walking time never does
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.journey_board, board.stop.name.text()),
                    fontSize = 13.sp,
                    color = soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    painterResource(R.drawable.ic_walk),
                    contentDescription = null,
                    tint = soft,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(13.dp),
                )
                Text(stringResource(R.string.journey_walk_minutes, board.walkMinutes), fontSize = 13.sp, color = soft, maxLines = 1)
            }
            Text(
                stringResource(R.string.journey_alight, alightName),
                fontSize = 13.sp,
                color = soft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val big = if (highlighted) 28.sp else 22.sp
            when {
                option.minutesUntil == 0 -> Text(stringResource(R.string.now), color = strong, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                // "368 min" is hard to read: an hour or more away we write "6h 8′"
                option.minutesUntil > 59 -> {
                    val hours = option.minutesUntil / 60
                    val minutes = option.minutesUntil % 60
                    Text(
                        if (minutes == 0) stringResource(R.string.duration_hours, hours)
                        else stringResource(R.string.duration_hours_minutes, hours, minutes),
                        color = number,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (highlighted) 22.sp else 18.sp,
                    )
                }
                else -> {
                    Text(option.minutesUntil.toString(), color = number, fontWeight = FontWeight.ExtraBold, fontSize = big, lineHeight = big)
                    Text(stringResource(R.string.minutes_short), color = soft, fontSize = 11.sp)
                }
            }
            Text(Schedule.formatMinutes(option.minutes), color = strong, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            option.later?.let {
                Text(stringResource(R.string.journey_later, Schedule.formatMinutes(it)), color = soft, fontSize = 11.sp)
            }
        }
    }
}
