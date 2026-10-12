package com.kastoriadevs.astikoktel.app.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.Departure
import com.kastoriadevs.astikoktel.app.domain.Geo
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.announcements.AnnouncementRow
import com.kastoriadevs.astikoktel.app.ui.announcements.AnnouncementsViewModel
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.labelRes
import com.kastoriadevs.astikoktel.app.ui.common.normalizeForSearch
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.components.PlaceholderBanner
import com.kastoriadevs.astikoktel.app.ui.components.SectionTitle
import com.kastoriadevs.astikoktel.app.ui.components.StopCard
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import java.time.Instant
import java.time.format.DateTimeFormatter

private val POPULAR = listOf("16001", "16052", "16114")

@Composable
fun HomeScreen(
    appVm: AppViewModel,
    onOpenStop: (String) -> Unit,
    onOpenLine: (lineId: String, dirId: String?) -> Unit,
    onOpenAnnouncements: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    announcementsVm: AnnouncementsViewModel = viewModel(factory = AnnouncementsViewModel.Factory),
) {
    StatusBarIcons(darkIcons = false)
    val container = appContainer()
    val data = container.transit.data
    val schedule = container.schedule
    val now = rememberNow()
    val favorites by appVm.favorites.collectAsStateWithLifecycle()
    val nearby by vm.nearby.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val announcements by announcementsVm.announcements.collectAsStateWithLifecycle()
    val activeAnnouncements = remember(announcements, now.toLocalDate()) {
        val nowInstant = Instant.now()
        announcements.orEmpty().filter { it.isActive(now.toLocalDate(), nowInstant) }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.locate() else vm.onPermissionDenied()
    }
    val locate = {
        if (vm.hasPermission()) {
            vm.locate()
        } else {
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val openDeparture: (Departure) -> Unit = { onOpenLine(it.line.id, it.direction.id) }
    val results = remember(query) {
        val q = normalizeForSearch(query.trim())
        if (q.isEmpty()) emptyList()
        else data.stops.filter { normalizeForSearch(it.name.el).contains(q) || normalizeForSearch(it.name.en).contains(q) }
    }
    val near = (nearby as? NearbyState.Ready)?.let { Geo.nearestStops(data.stops, it.coords.lat, it.coords.lng, 2) }.orEmpty()
    val favStops = favorites.mapNotNull { data.stop(it) }
    val popular = POPULAR.mapNotNull { data.stop(it) }
        .filter { it.id !in favorites && near.none { n -> n.first.id == it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Hero(
                dateLine = buildDateLine(now, schedule.serviceDayFor(now.toLocalDate()).labelRes(), schedule.seasonFor(now.toLocalDate()).labelRes()),
                query = query,
                onQuery = { query = it },
                hasAnnouncements = activeAnnouncements.isNotEmpty(),
                onOpenAnnouncements = onOpenAnnouncements,
            )
        }
        item { Padded { PlaceholderBanner(data.meta.placeholder) } }

        if (query.isNotBlank()) {
            item {
                Padded {
                    AppCard(contentPadding = 16.dp) {
                        if (results.isEmpty()) {
                            Text(stringResource(R.string.no_results), color = Astika.colors.muted, fontSize = 14.sp)
                        } else {
                            Column {
                                results.forEachIndexed { i, stop ->
                                    if (i > 0) HorizontalDivider(color = Astika.colors.border)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .clickable { onOpenStop(stop.id) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Astika.colors.muted)
                                        Text(stop.name.text(), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            data.linesAtStop(stop.id).forEach { LineBadge(it) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // Current announcements (strikes, holiday timetables, …)
        if (activeAnnouncements.isNotEmpty()) {
            item {
                Padded {
                    SectionTitle(stringResource(R.string.announcements)) {
                        TextButton(onClick = onOpenAnnouncements) {
                            Text(stringResource(R.string.announcements_all), color = Astika.colors.link)
                        }
                    }
                }
            }
            items(activeAnnouncements, key = { "ann-" + it.id }) { announcement ->
                Padded { AnnouncementRow(announcement, onClick = onOpenAnnouncements) }
            }
        }

        // Near me
        item {
            Padded {
                SectionTitle(stringResource(R.string.near_me)) {
                    if (nearby is NearbyState.Ready) {
                        TextButton(onClick = locate) { Text(stringResource(R.string.refresh), color = Astika.colors.link) }
                    }
                }
            }
        }
        if (nearby is NearbyState.Ready) {
            items(near, key = { "near-" + it.first.id }) { (stop, distance) ->
                Padded {
                    StopCard(
                        stop = stop,
                        departures = schedule.nextDepartures(stop.id, now, 3),
                        now = now,
                        isFavorite = stop.id in favorites,
                        onToggleFavorite = { appVm.toggleFavorite(stop.id) },
                        onOpenStop = { onOpenStop(stop.id) },
                        onOpenDeparture = openDeparture,
                        distanceMeters = distance,
                    )
                }
            }
        } else {
            item {
                Padded {
                    val loading = nearby is NearbyState.Loading
                    OutlinedButton(
                        onClick = locate,
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Astika.colors.border),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Astika.colors.link, modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(if (loading) R.string.locating else R.string.find_nearby),
                            color = Astika.colors.link,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
            val message = when (nearby) {
                NearbyState.Denied -> R.string.location_denied
                NearbyState.Disabled -> R.string.location_unsupported
                NearbyState.Error -> R.string.location_error
                else -> null
            }
            if (message != null) {
                item { Padded { Text(stringResource(message), color = Astika.colors.muted, fontSize = 14.sp) } }
            }
        }

        // Favourites
        item { Padded { SectionTitle(stringResource(R.string.favorites)) } }
        if (favStops.isEmpty()) {
            item { Padded { Text(stringResource(R.string.favorites_empty), color = Astika.colors.muted, fontSize = 14.sp) } }
        }
        items(favStops, key = { "fav-" + it.id }) { stop ->
            Padded {
                StopCard(
                    stop = stop,
                    departures = schedule.nextDepartures(stop.id, now, 3),
                    now = now,
                    isFavorite = true,
                    onToggleFavorite = { appVm.toggleFavorite(stop.id) },
                    onOpenStop = { onOpenStop(stop.id) },
                    onOpenDeparture = openDeparture,
                )
            }
        }

        // Popular
        if (popular.isNotEmpty()) {
            item { Padded { SectionTitle(stringResource(R.string.popular_stops)) } }
            items(popular, key = { "pop-" + it.id }) { stop ->
                Padded {
                    StopCard(
                        stop = stop,
                        departures = schedule.nextDepartures(stop.id, now, 3),
                        now = now,
                        isFavorite = false,
                        onToggleFavorite = { appVm.toggleFavorite(stop.id) },
                        onOpenStop = { onOpenStop(stop.id) },
                        onOpenDeparture = openDeparture,
                    )
                }
            }
        }

        // All lines
        item { Padded { SectionTitle(stringResource(R.string.all_lines)) } }
        items(data.lines, key = { "line-" + it.id }) { line ->
            Padded {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpenLine(line.id, null) }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LineBadge(line)
                    Text(line.name.text(), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) { content() }
}

@Composable
private fun buildDateLine(now: java.time.LocalDateTime, serviceRes: Int, seasonRes: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val date = now.format(formatter).replaceFirstChar { it.titlecase(locale) }
    return "$date · ${stringResource(serviceRes)} · ${stringResource(seasonRes)}"
}

@Composable
private fun Hero(
    dateLine: String,
    query: String,
    onQuery: (String) -> Unit,
    hasAnnouncements: Boolean,
    onOpenAnnouncements: () -> Unit,
) {
    val c = Astika.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(c.hero)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.app_title), color = c.heroText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(dateLine, color = c.heroMuted, fontSize = 14.sp)
            }
            IconButton(onClick = onOpenAnnouncements) {
                BadgedBox(badge = { if (hasAnnouncements) Badge() }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.announcements), tint = c.heroText)
                }
            }
        }
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_stops)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
