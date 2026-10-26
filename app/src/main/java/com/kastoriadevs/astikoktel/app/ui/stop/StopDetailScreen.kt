package com.kastoriadevs.astikoktel.app.ui.stop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.announcements.AnnouncementsViewModel
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.common.labelRes
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.AppChip
import com.kastoriadevs.astikoktel.app.ui.components.DepartureList
import com.kastoriadevs.astikoktel.app.ui.components.FavoriteButton
import com.kastoriadevs.astikoktel.app.ui.components.HeaderTitle
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.components.PlaceholderBanner
import com.kastoriadevs.astikoktel.app.ui.components.ScreenHeader
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import java.time.Instant

@Composable
fun StopDetailScreen(
    stopId: String,
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenLine: (lineId: String, dirId: String?) -> Unit,
    onOpenMap: (String) -> Unit,
    onOpenAnnouncements: () -> Unit,
    announcementsVm: AnnouncementsViewModel = viewModel(factory = AnnouncementsViewModel.Factory),
) {
    StatusBarIcons(darkIcons = !isAppDark())
    val container = appContainer()
    val data = container.transit.data
    val schedule = container.schedule
    val stop = data.stop(stopId) ?: return
    val now = rememberNow()
    val favorites by appVm.favorites.collectAsStateWithLifecycle()
    val announcements by announcementsVm.announcements.collectAsStateWithLifecycle()
    val lines = remember(stop.id) { data.linesAtStop(stop.id) }
    var lineFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val departures = schedule.nextDepartures(stop.id, now, 40)
        .filter { lineFilter == null || it.line.id == lineFilter }
        .take(12)
    val previous = schedule.previousDeparture(stop.id, now)?.takeIf { lineFilter == null || it.line.id == lineFilter }
    // The feed doesn't say which lines a post is about, so the newest one still in force is shown
    val notice = remember(announcements, now.toLocalDate()) {
        announcements?.firstOrNull { it.isActive(now.toLocalDate(), Instant.now()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            onBack = onBack,
            title = { HeaderTitle(stop.name.text()) },
            subtitle = stringResource(R.string.stop_code, stop.id) + " · " + stringResource(R.string.lines_count, lines.size),
            trailing = {
                IconButton(onClick = { onOpenMap(stop.id) }) {
                    Icon(
                        painterResource(R.drawable.ic_map),
                        contentDescription = stringResource(R.string.show_on_map),
                        tint = Astika.colors.link,
                        modifier = Modifier.size(22.dp),
                    )
                }
                FavoriteButton(stop.id in favorites, { appVm.toggleFavorite(stop.id) })
            },
        )
        if (lines.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    AppChip(selected = lineFilter == null, onClick = { lineFilter = null }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Text(stringResource(R.string.all_filter), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(lines, key = { it.id }) { line ->
                    AppChip(
                        selected = lineFilter == line.id,
                        onClick = { lineFilter = if (lineFilter == line.id) null else line.id },
                        contentPadding = PaddingValues(start = 5.dp, end = 12.dp),
                    ) {
                        LineBadge(line)
                        Text(line.name.text(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaceholderBanner(data.meta.placeholder)
            if (notice != null) {
                NoticeRow(title = notice.title, onClick = onOpenAnnouncements)
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    stringResource(R.string.next_departures),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(schedule.serviceDayFor(now.toLocalDate()).labelRes()),
                    fontSize = 12.sp,
                    color = Astika.colors.muted,
                )
            }
            AppCard {
                DepartureList(departures, now, previous = previous) { onOpenLine(it.line.id, it.direction.id) }
            }
        }
    }
}

/** An announcement still in force, shown above the departures */
@Composable
private fun NoticeRow(title: String, onClick: () -> Unit) {
    val c = Astika.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.bannerBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = c.bannerText, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = c.bannerText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.notice_details), color = c.bannerText, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.bannerText)
    }
}
