package com.kastoriadevs.astikoktel.app.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kastoriadevs.astikoktel.app.data.Stop
import com.kastoriadevs.astikoktel.app.domain.Destination
import com.kastoriadevs.astikoktel.app.domain.JourneyPlanner
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.announcements.AnnouncementRow
import com.kastoriadevs.astikoktel.app.ui.announcements.AnnouncementsViewModel
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.currentLanguage
import com.kastoriadevs.astikoktel.app.ui.common.labelRes
import com.kastoriadevs.astikoktel.app.ui.common.normalizeForSearch
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.FavoriteButton
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.components.PlaceholderBanner
import com.kastoriadevs.astikoktel.app.ui.components.SectionTitle
import com.kastoriadevs.astikoktel.app.ui.components.StopCard
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import java.time.format.DateTimeFormatter

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
    val latestAnnouncement = remember(announcements) { announcements?.firstOrNull() }
    var favPickerExpanded by rememberSaveable { mutableStateOf(false) }
    var favSearchQuery by rememberSaveable { mutableStateOf("") }

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
    val favStops = favorites.mapNotNull { data.stop(it) }
    var destination by rememberSaveable { mutableStateOf<Destination?>(null) }
    val coords = (nearby as? NearbyState.Ready)?.coords
    val plan = remember(destination, coords, now) {
        val d = destination
        if (d == null || coords == null) null
        else JourneyPlanner.plan(data, schedule, d, coords.lat, coords.lng, now)
    }

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

        item {
            Padded {
                JourneySection(
                    data = data,
                    destination = destination,
                    nearby = nearby,
                    plan = plan,
                    onDestinationClick = { clicked ->
                        // Tapping the selected destination again clears it
                        destination = clicked.takeUnless { it == destination }
                        if (destination != null && nearby is NearbyState.Idle) locate()
                    },
                    onLocate = locate,
                    onOpenStop = onOpenStop,
                )
            }
        }

        // Favourites
        item { Padded { SectionTitle(stringResource(R.string.favorites)) } }
        item {
            Padded {
                FavoriteStopsDropdown(
                    stops = data.stops,
                    favorites = favorites.toSet(),
                    expanded = favPickerExpanded,
                    onToggleExpanded = { favPickerExpanded = !favPickerExpanded },
                    query = favSearchQuery,
                    onQueryChange = { favSearchQuery = it },
                    onToggleFavorite = { appVm.toggleFavorite(it) },
                )
            }
        }
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

        // Announcements
        item {
            Padded {
                SectionTitle(stringResource(R.string.announcements)) {
                    TextButton(onClick = onOpenAnnouncements) {
                        Text(stringResource(R.string.announcements_all), color = Astika.colors.link)
                    }
                }
            }
        }
        if (latestAnnouncement == null) {
            item {
                Padded {
                    Text(stringResource(R.string.announcements_none), color = Astika.colors.muted, fontSize = 14.sp)
                }
            }
        } else {
            item(key = "ann-" + latestAnnouncement.id) {
                Padded { AnnouncementRow(latestAnnouncement, onClick = onOpenAnnouncements) }
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

@Composable
private fun FavoriteStopsDropdown(
    stops: List<Stop>,
    favorites: Set<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val c = Astika.colors
    val language = currentLanguage()
    val title = if (favorites.isNotEmpty()) {
        "${stringResource(R.string.select_favorite_stops)} (${favorites.size})"
    } else {
        stringResource(R.string.select_favorite_stops)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (favorites.isNotEmpty()) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (favorites.isNotEmpty()) c.star else c.link,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = c.muted,
                modifier = Modifier.size(22.dp),
            )
        }

        if (expanded) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_stops), fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.clear_search),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
            )

            val allStops = remember(stops, language) { stops.sortedBy { it.name.get(language) } }
            val q = normalizeForSearch(query.trim())
            val filtered = remember(allStops, q) {
                if (q.isEmpty()) allStops
                else allStops.filter {
                    normalizeForSearch(it.name.el).contains(q) || normalizeForSearch(it.name.en).contains(q)
                }
            }

            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_results),
                    color = c.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    filtered.forEach { stop ->
                        val isFav = stop.id in favorites
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleFavorite(stop.id) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stop.name.text(),
                                fontSize = 14.sp,
                                fontWeight = if (isFav) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            FavoriteButton(
                                isFavorite = isFav,
                                onToggle = { onToggleFavorite(stop.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
