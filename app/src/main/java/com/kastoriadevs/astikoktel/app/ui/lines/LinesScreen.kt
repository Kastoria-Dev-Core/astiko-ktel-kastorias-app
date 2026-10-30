package com.kastoriadevs.astikoktel.app.ui.lines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.domain.Schedule
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.common.rememberNow
import com.kastoriadevs.astikoktel.app.ui.common.text
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.BadgeSize
import com.kastoriadevs.astikoktel.app.ui.components.HeaderTitle
import com.kastoriadevs.astikoktel.app.ui.components.LineBadge
import com.kastoriadevs.astikoktel.app.ui.components.PlaceholderBanner
import com.kastoriadevs.astikoktel.app.ui.components.RowDivider
import com.kastoriadevs.astikoktel.app.ui.components.ScreenHeader
import com.kastoriadevs.astikoktel.app.ui.theme.Astika

/** Every line in one list: number, name, where it goes, and when the next bus leaves */
@Composable
fun LinesScreen(onOpenLine: (String) -> Unit) {
    StatusBarIcons(darkIcons = !isAppDark())
    val container = appContainer()
    val data = container.transit.data
    val schedule = container.schedule
    val now = rememberNow()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = { HeaderTitle(stringResource(R.string.tab_lines)) })
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaceholderBanner(data.meta.placeholder)
            AppCard(contentPadding = 0.dp) {
                Column {
                    data.lines.forEachIndexed { i, line ->
                        if (i > 0) RowDivider(startInset = 66.dp)
                        // The soonest bus of either direction, today
                        val next = line.directions.mapNotNull { schedule.nextTrip(line, it, now) }.minOrNull()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .clickable { onOpenLine(line.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            LineBadge(line, BadgeSize.MD)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    line.name.text(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    // Long names wrap to a second line rather than lose their end
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Where the line starts and where it ends, e.g. "Αφετηρία - Πανηγύρι"
                                val route = line.directions.firstOrNull()?.let { d -> data.directionStops(line, d.id) }.orEmpty()
                                Text(
                                    if (route.size > 1) "${route.first().name.text()} - ${route.last().name.text()}" else line.name.text(),
                                    fontSize = 13.sp,
                                    color = Astika.colors.muted,
                                    // Long names wrap to a second line rather than lose their end
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.next_label), fontSize = 12.sp, color = Astika.colors.muted)
                                if (next != null) {
                                    Text(Schedule.formatMinutes(next), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                    val noneToday = stringResource(R.string.none_today)
                                    Text(
                                        "–",
                                        fontSize = 15.sp,
                                        color = Astika.colors.muted,
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .semantics { contentDescription = noneToday },
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
