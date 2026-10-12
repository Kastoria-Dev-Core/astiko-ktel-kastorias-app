package com.michadasis.astikaktel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.michadasis.astikaktel.R
import com.michadasis.astikaktel.data.Departure
import com.michadasis.astikaktel.data.Line
import com.michadasis.astikaktel.data.Stop
import com.michadasis.astikaktel.domain.Geo
import com.michadasis.astikaktel.domain.Schedule
import com.michadasis.astikaktel.ui.common.currentLanguage
import com.michadasis.astikaktel.ui.common.text
import com.michadasis.astikaktel.ui.common.toColor
import com.michadasis.astikaktel.ui.theme.Astika
import java.time.LocalDateTime

enum class BadgeSize(val box: Dp, val font: TextUnit, val radius: Dp) {
    SM(28.dp, 15.sp, 8.dp),
    MD(34.dp, 17.sp, 9.dp),
    LG(48.dp, 22.sp, 13.dp),
}

@Composable
fun LineBadge(line: Line, size: BadgeSize = BadgeSize.SM) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = size.box)
            .height(size.box)
            .clip(RoundedCornerShape(size.radius))
            .background(line.color.toColor())
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line.number,
            color = line.textColor.toColor(),
            fontSize = size.font,
            fontWeight = FontWeight.Bold,
        )
    }
}

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

@Composable
fun PlaceholderBanner(visible: Boolean) {
    if (!visible) return
    val c = Astika.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.bannerBg)
            .border(1.dp, c.bannerBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = c.bannerText, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.placeholder_banner), color = c.bannerText, fontSize = 14.sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            color = Astika.colors.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

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
            .clip(RoundedCornerShape(50))
            .background(c.bannerBg)
            .border(1.dp, c.bannerBorder, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

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

@Composable
fun DepartureRow(dep: Departure, now: LocalDateTime, onClick: () -> Unit) {
    val mins = Schedule.minutesUntil(dep, now)
    val soon = dep.dayOffset == 0 && mins <= 10
    val label = when {
        dep.dayOffset > 0 -> stringResource(R.string.tomorrow)
        mins <= 0 -> stringResource(R.string.now)
        mins < 60 -> stringResource(R.string.in_min, mins)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineBadge(dep.line)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = dep.direction.headsign.text(),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (dep.last && dep.dayOffset == 0) Pill(stringResource(R.string.last_trip))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Schedule.formatMinutes(dep.minutes), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (label != null) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = if (soon) Astika.colors.soon else Astika.colors.muted,
                    fontWeight = if (soon) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun DepartureList(departures: List<Departure>, now: LocalDateTime, onOpen: (Departure) -> Unit) {
    if (departures.isEmpty()) {
        Text(stringResource(R.string.no_service), color = Astika.colors.muted, fontSize = 14.sp)
        return
    }
    Column {
        departures.forEachIndexed { i, dep ->
            if (i > 0) HorizontalDivider(color = Astika.colors.border)
            DepartureRow(dep, now) { onOpen(dep) }
        }
    }
}

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
                    .padding(vertical = 4.dp),
            ) {
                Text(stop.name.text(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (distanceMeters != null) {
                    Text(Geo.formatDistance(distanceMeters, currentLanguage()), fontSize = 13.sp, color = Astika.colors.muted)
                }
            }
            FavoriteButton(isFavorite, onToggleFavorite)
        }
        DepartureList(departures, now, onOpenDeparture)
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
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
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
                        Text(subtitle, fontSize = 14.sp, color = contentColor.copy(alpha = 0.8f))
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

@Composable
fun HeaderTitle(text: String) {
    Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
}

/** Segmented control; on a tinted header the selected option turns white */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    tinted: Boolean = false,
) {
    val track = if (tinted) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg = when {
                !on -> Color.Transparent
                tinted -> Color.White
                else -> MaterialTheme.colorScheme.surface
            }
            val fg = when {
                on && tinted -> Color(0xFF14222B)
                on -> MaterialTheme.colorScheme.onSurface
                tinted -> Color.White.copy(alpha = 0.92f)
                else -> Astika.colors.muted
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
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                selected = on
                contentDescription = label
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 17.sp)
    }
}
