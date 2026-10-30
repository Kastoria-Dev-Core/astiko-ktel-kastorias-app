package com.kastoriadevs.astikoktel.app.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.Departure
import com.kastoriadevs.astikoktel.app.data.ServiceDay
import com.kastoriadevs.astikoktel.app.data.Stop
import com.kastoriadevs.astikoktel.app.domain.Destination
import com.kastoriadevs.astikoktel.app.domain.JourneyPlanner
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.announcements.AnnouncementRow
import com.kastoriadevs.astikoktel.app.ui.announcements.AnnouncementsViewModel
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.currentLanguage
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.common.normalizeForSearch
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.searchLines
import com.kastoriadevs.astikoktel.app.ui.common.searchStops
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.common.title
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
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    announcementsVm: AnnouncementsViewModel = viewModel(factory = AnnouncementsViewModel.Factory),
) {
    StatusBarIcons(darkIcons = !isAppDark())
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
    val currentLang = currentLanguage()
    val stopResults = remember(query, currentLang) { searchStops(data.stops, query, currentLang) }
    val lineResults = remember(query, currentLang) { searchLines(data.lines, query, currentLang) }
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
                dateLine = buildDateLine(now, dayLabelRes(schedule, now.toLocalDate())),
                query = query,
                onQuery = { query = it },
                onOpenSettings = onOpenSettings,
            )
        }
        item { Padded { PlaceholderBanner(data.meta.placeholder) } }

        if (query.isNotBlank()) {
            item {
                Padded {
                    AppCard(contentPadding = 16.dp) {
                        if (stopResults.isEmpty() && lineResults.isEmpty()) {
                            Text(stringResource(R.string.no_results), color = Astika.colors.muted, fontSize = 14.sp)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (lineResults.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.tab_lines),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Astika.colors.muted,
                                        modifier = Modifier.padding(bottom = 2.dp),
                                    )
                                    for ((i, line) in lineResults.withIndex()) {
                                        if (i > 0) HorizontalDivider(color = Astika.colors.border)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .clickable { onOpenLine(line.id, null) },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            LineBadge(line)
                                            Text(
                                                line.name.text(),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (stopResults.isNotEmpty()) {
                                        HorizontalDivider(
                                            color = Astika.colors.border,
                                            modifier = Modifier.padding(vertical = 6.dp),
                                        )
                                    }
                                }
                                if (stopResults.isNotEmpty()) {
                                    if (lineResults.isNotEmpty()) {
                                        Text(
                                            stringResource(R.string.find_stop),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Astika.colors.muted,
                                            modifier = Modifier.padding(bottom = 2.dp),
                                        )
                                    }
                                    for ((i, stop) in stopResults.withIndex()) {
                                        if (i > 0) HorizontalDivider(color = Astika.colors.border)
                                        val linesAtStop = data.linesAtStop(stop.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 52.dp)
                                                .clickable { onOpenStop(stop.id) },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Astika.colors.muted)
                                            Text(
                                                stop.title(),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (linesAtStop.isNotEmpty()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    linesAtStop.take(3).forEach { LineBadge(it) }
                                                    if (linesAtStop.size > 3) {
                                                        Text(
                                                            "+${linesAtStop.size - 3}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = Astika.colors.muted,
                                                        )
                                                    }
                                                }
                                            }
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

        // Favourites. The picker stays out of the way until "Edit" (or "Find a stop" when there are none).
        item {
            Padded {
                SectionTitle(stringResource(R.string.favorites)) {
                    if (favStops.isNotEmpty() || favPickerExpanded) {
                        TextButton(onClick = { favPickerExpanded = !favPickerExpanded }) {
                            Text(
                                stringResource(if (favPickerExpanded) R.string.done else R.string.edit),
                                color = Astika.colors.link,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        if (favPickerExpanded) {
            item {
                Padded {
                    FavoriteStopsDropdown(
                        stops = data.stops,
                        favorites = favorites.toSet(),
                        expanded = true,
                        onToggleExpanded = { favPickerExpanded = false },
                        query = favSearchQuery,
                        onQueryChange = { favSearchQuery = it },
                        onToggleFavorite = { appVm.toggleFavorite(it) },
                    )
                }
            }
        } else if (favStops.isEmpty()) {
            item {
                Padded {
                    AppCard {
                        Text(stringResource(R.string.favorites_empty), color = Astika.colors.muted, fontSize = 14.sp)
                        TextButton(
                            onClick = { favPickerExpanded = true },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(stringResource(R.string.find_stop), color = Astika.colors.link, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
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

/** What kind of day it is, in one word for the header; the line pages keep the timetable's full names */
private fun dayLabelRes(schedule: com.kastoriadevs.astikoktel.app.domain.Schedule, date: java.time.LocalDate): Int = when {
    schedule.isHoliday(date) -> R.string.day_holiday
    else -> when (schedule.serviceDayFor(date)) {
        ServiceDay.WEEKDAY -> R.string.day_weekday
        ServiceDay.SATURDAY -> R.string.day_saturday
        ServiceDay.SUNDAY -> R.string.day_sunday
    }
}

@Composable
private fun buildDateLine(now: java.time.LocalDateTime, dayRes: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val date = now.format(formatter).replaceFirstChar { it.titlecase(locale) }
    return "$date · ${stringResource(dayRes)}"
}

@Composable
private fun Hero(
    dateLine: String,
    query: String,
    onQuery: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = Astika.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The app's mark in the theme colour, not the full launcher icon with its own background
            Icon(
                painter = painterResource(R.drawable.ic_app_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(width = 36.dp, height = 35.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.app_title), color = MaterialTheme.colorScheme.onBackground, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(dateLine, color = c.muted, fontSize = 13.sp)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.tab_settings), tint = c.ink2)
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
                unfocusedLeadingIconColor = c.muted,
                unfocusedPlaceholderColor = c.muted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.border, RoundedCornerShape(14.dp)),
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
            val filtered = remember(allStops, query, language) {
                if (query.isBlank()) allStops
                else searchStops(stops, query, language)
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
                                text = stop.title(),
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
