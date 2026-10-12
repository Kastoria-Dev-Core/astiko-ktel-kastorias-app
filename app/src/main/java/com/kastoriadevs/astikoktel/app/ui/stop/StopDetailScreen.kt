package com.kastoriadevs.astikoktel.app.ui.stop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.DepartureList
import com.kastoriadevs.astikoktel.app.ui.components.FavoriteButton
import com.kastoriadevs.astikoktel.app.ui.components.HeaderTitle
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.components.PlaceholderBanner
import com.kastoriadevs.astikoktel.app.ui.components.ScreenHeader
import com.kastoriadevs.astikoktel.app.ui.theme.Astika

@Composable
fun StopDetailScreen(
    stopId: String,
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenLine: (lineId: String, dirId: String?) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    StatusBarIcons(darkIcons = !isAppDark())
    val container = appContainer()
    val data = container.transit.data
    val stop = data.stop(stopId) ?: return
    val now = rememberNow()
    val favorites by appVm.favorites.collectAsStateWithLifecycle()
    val departures = container.schedule.nextDepartures(stop.id, now, 12)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            onBack = onBack,
            title = { HeaderTitle(stop.name.text()) },
            trailing = { FavoriteButton(stop.id in favorites, { appVm.toggleFavorite(stop.id) }) },
            below = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.lines_here), fontSize = 14.sp, color = Astika.colors.muted)
                    data.linesAtStop(stop.id).forEach { line ->
                        Box(Modifier.clickable { onOpenLine(line.id, null) }) { LineBadge(line) }
                    }
                }
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
                        stringResource(R.string.next_departures),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onOpenMap(stop.id) }) {
                        Icon(painterResource(R.drawable.ic_map), contentDescription = null, tint = Astika.colors.link, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.show_on_map),
                            color = Astika.colors.link,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                DepartureList(departures, now) { onOpenLine(it.line.id, it.direction.id) }
            }
        }
    }
}
