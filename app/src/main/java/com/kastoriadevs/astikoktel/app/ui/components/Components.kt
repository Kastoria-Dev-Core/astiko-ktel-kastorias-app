package com.kastoriadevs.astikoktel.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.Departure
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.Stop
import com.kastoriadevs.astikoktel.app.domain.Geo
import com.kastoriadevs.astikoktel.app.domain.Schedule
import com.kastoriadevs.astikoktel.app.ui.common.currentLanguage
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.common.title
import com.kastoriadevs.astikoktel.app.ui.common.toColor
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import java.time.LocalDateTime

/** One badge shape everywhere: a small rounded rectangle, never a circle or a pill */
enum class BadgeSize(
    /** Height of the badge */
    val height: Dp,
    /** Wide enough for two digits; never narrower, so badges line up */
    val minWidth: Dp,
    /** Size of the number */
    val font: TextUnit,
    /** Corner radius */
    val radius: Dp,
) {
    SM(24.dp, 34.dp, 13.sp, 6.dp),
    MD(28.dp, 40.dp, 15.sp, 7.dp),
    LG(32.dp, 44.dp, 17.sp, 8.dp),
}

/** A line's number in its colour. School lines are outlined so they read as a different kind of service. */
@Composable
fun LineBadge(line: Line, size: BadgeSize = BadgeSize.SM) {
    val shape = RoundedCornerShape(size.radius)
    val color = line.color.toColor()
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = size.minWidth)
            .height(size.height)
            .clip(shape)
            .then(
                if (line.school) {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, color, shape)
                } else {
                    Modifier.background(color)
                },
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line.number.padStart(2, '0'),
            color = if (line.school) MaterialTheme.colorScheme.onSurface else line.textColor.toColor(),
            fontSize = size.font,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The app's card: white (or raised in dark mode), rounded, with a thin border; tappable when [onClick] is set */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(1.dp, Astika.colors.border)
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(horizontal = contentPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, border = border, content = inner)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, border = border, content = inner)
    }
}

/** Says the timetables are sample data, not the operator's. Shown when meta.json says `placeholder`. */
@Composable
fun PlaceholderBanner(visible: Boolean) {
    if (!visible) return
    val c = Astika.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.bannerBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = c.bannerText, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.placeholder_banner), color = c.bannerText, fontSize = 14.sp)
    }
}

/** Section heading: sentence case, no tracking, the same size everywhere */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** A small label in the banner colours, e.g. a walking distance */
@Composable
fun Pill(text: String) {
    val c = Astika.colors
    Text(
        text = text,
        color = c.bannerText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.bannerBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A star that adds the stop to the favourites or takes it out */
@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, tint: Color = Astika.colors.muted) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
            tint = if (isFavorite) Astika.colors.star else tint,
        )
    }
}

/**
 * One departure. Within the hour the minutes are what you read first, with the clock time under
 * them; later ones show the clock time only. A bus that already left is shown faded.
 */
@Composable
fun DepartureRow(dep: Departure, now: LocalDateTime, onClick: () -> Unit, departed: Boolean = false) {
    val mins = Schedule.minutesUntil(dep, now)
    val clock = Schedule.formatMinutes(dep.minutes)
    val within = !departed && dep.dayOffset == 0 && mins < 60
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .then(if (departed) Modifier.alpha(0.55f) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineBadge(dep.line)
        // The line's name says which bus it is; the last stop only says which way it goes
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dep.line.name.text(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = when {
                        departed -> stringResource(R.string.departed)
                        else -> stringResource(R.string.towards, dep.direction.headsign.text())
                    },
                    fontSize = 12.sp,
                    color = Astika.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!departed && dep.last && dep.dayOffset == 0) Pill(stringResource(R.string.last_trip))
            }
        }
        dep.note?.let { NoteButton(it) }
        Column(horizontalAlignment = Alignment.End) {
            when {
                departed -> Text(clock, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Astika.colors.muted)
                within -> {
                    LiveMark(dep.live) {
                        Text(
                            text = if (mins <= 0) stringResource(R.string.now) else stringResource(R.string.minutes_only, mins),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 24.sp,
                            color = if (dep.live) Astika.colors.live else Color.Unspecified,
                        )
                    }
                    Text(clock, fontSize = 12.sp, color = Astika.colors.muted)
                }
                else -> {
                    LiveMark(dep.live) {
                        Text(
                            clock,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dep.live) Astika.colors.live else Color.Unspecified,
                        )
                    }
                    if (dep.dayOffset > 0) Text(stringResource(R.string.tomorrow), fontSize = 12.sp, color = Astika.colors.muted)
                }
            }
        }
    }
}

/**
 * An info icon next to a departure the operator wrote something about, e.g. "Μαθητικό".
 * Tapping it shows the note in a dialog.
 */
@Composable
fun NoteButton(note: String, tint: Color = Astika.colors.link) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(R.string.note_show),
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
            title = { Text(stringResource(R.string.note_title)) },
            text = { Text(note) },
        )
    }
}

/** A time, with the live mark before it when it comes from where the bus really is */
@Composable
private fun LiveMark(live: Boolean, time: @Composable () -> Unit) {
    if (!live) return time()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painterResource(R.drawable.ic_live),
            contentDescription = stringResource(R.string.live_time),
            tint = Astika.colors.live,
            modifier = Modifier.size(15.dp),
        )
        time()
    }
}

/** Departures in one list; [previous] is the bus that just left, shown faded on top */
@Composable
fun DepartureList(
    departures: List<Departure>,
    now: LocalDateTime,
    previous: Departure? = null,
    onOpen: (Departure) -> Unit,
) {
    if (departures.isEmpty() && previous == null) {
        Text(stringResource(R.string.no_service), color = Astika.colors.muted, fontSize = 14.sp)
        return
    }
    Column {
        if (previous != null) {
            DepartureRow(previous, now, onClick = { onOpen(previous) }, departed = true)
        }
        departures.forEachIndexed { i, dep ->
            if (i > 0 || previous != null) RowDivider()
            DepartureRow(dep, now, onClick = { onOpen(dep) })
        }
        if (departures.isEmpty()) {
            RowDivider()
            Text(
                stringResource(R.string.no_more_today),
                color = Astika.colors.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

/** Hairline between rows, starting after the badge column */
@Composable
fun RowDivider(startInset: Dp = 46.dp) {
    HorizontalDivider(color = Astika.colors.divider, modifier = Modifier.padding(start = startInset))
}

/** A stop with its next departures and a star, as on Home; [distanceMeters] adds how far it is */
@Composable
fun StopCard(
    stop: Stop,
    departures: List<Departure>,
    now: LocalDateTime,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenStop: () -> Unit,
    onOpenDeparture: (Departure) -> Unit,
    distanceMeters: Double? = null,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenStop)
                    .padding(vertical = 2.dp),
            ) {
                Text(stop.title(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (distanceMeters != null) {
                        stringResource(R.string.stop_code, stop.id) + " · " + Geo.formatDistance(distanceMeters, currentLanguage())
                    } else {
                        stringResource(R.string.stop_code, stop.id)
                    },
                    fontSize = 12.sp,
                    color = Astika.colors.muted,
                )
            }
            FavoriteButton(isFavorite, onToggleFavorite)
        }
        DepartureList(departures, now, onOpen = onOpenDeparture)
    }
}

/** Header used by every screen except Home; draws behind the status bar */
@Composable
fun ScreenHeader(
    title: @Composable () -> Unit,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    rounded: Boolean = false,
    below: (@Composable () -> Unit)? = null,
) {
    val shape = if (rounded) RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp) else RoundedCornerShape(0.dp)
    Surface(color = container, contentColor = contentColor, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    title()
                    if (subtitle != null) {
                        Text(subtitle, fontSize = 13.sp, color = if (container == MaterialTheme.colorScheme.background) Astika.colors.muted else contentColor.copy(alpha = 0.8f))
                    }
                }
                trailing?.invoke()
            }
            if (below != null) {
                Box(modifier = Modifier.padding(horizontal = 8.dp)) { below() }
            }
        }
    }
}

/** The big title in a [ScreenHeader] */
@Composable
fun HeaderTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 27.sp, letterSpacing = (-0.2).sp)
}

/** Segmented control; on a tinted header the selected option turns white */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    tinted: Boolean = false,
) {
    val track = if (tinted) Color.Black.copy(alpha = 0.2f) else Astika.colors.track
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg = when {
                !on -> Color.Transparent
                tinted -> Color.White
                else -> Astika.colors.segmentOn
            }
            val fg = when {
                on && tinted -> Color(0xFF10212B)
                on -> MaterialTheme.colorScheme.onSurface
                tinted -> Color.White.copy(alpha = 0.92f)
                else -> Astika.colors.ink2
            }
            SegmentOption(label, bg, fg, on, Modifier.weight(1f)) { onSelect(value) }
        }
    }
}

@Composable
private fun RowScope.SegmentOption(
    label: String,
    bg: Color,
    fg: Color,
    on: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                selected = on
                contentDescription = label
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Rounded filter chip: dark when selected, outlined otherwise */
@Composable
fun AppChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val c = Astika.colors
    val on = selected
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (on) c.chipOn else MaterialTheme.colorScheme.surface,
        contentColor = if (on) c.chipOnText else c.ink2,
        border = if (on) null else BorderStroke(1.dp, c.border),
        modifier = modifier.semantics { this.selected = on },
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/** A row of text chips, one selected */
@Composable
fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            item {
                AppChip(selected = value == selected, onClick = { onSelect(value) }) {
                    Text(label, fontSize = 13.sp, fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
