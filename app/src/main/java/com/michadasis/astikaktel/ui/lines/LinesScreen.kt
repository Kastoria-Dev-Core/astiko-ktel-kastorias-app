package com.michadasis.astikaktel.ui.lines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michadasis.astikaktel.R
import com.michadasis.astikaktel.domain.Schedule
import com.michadasis.astikaktel.ui.common.StatusBarIcons
import com.michadasis.astikaktel.ui.common.appContainer
import com.michadasis.astikaktel.ui.common.isAppDark
import com.michadasis.astikaktel.ui.common.rememberNow
import com.michadasis.astikaktel.ui.common.text
import com.michadasis.astikaktel.ui.common.toColor
import com.michadasis.astikaktel.ui.components.AppCard
import com.michadasis.astikaktel.ui.components.BadgeSize
import com.michadasis.astikaktel.ui.components.HeaderTitle
import com.michadasis.astikaktel.ui.components.LineBadge
import com.michadasis.astikaktel.ui.components.PlaceholderBanner
import com.michadasis.astikaktel.ui.components.ScreenHeader
import com.michadasis.astikaktel.ui.theme.Astika

@Composable
fun LinesScreen(onOpenLine: (String) -> Unit) {
    StatusBarIcons(darkIcons = !isAppDark())
    val container = appContainer()
    val data = container.transit.data
    val schedule = container.schedule
    val now = rememberNow()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = { HeaderTitle(stringResource(R.string.tab_lines)) },
                subtitle = stringResource(R.string.lines_count, data.lines.size),
            )
        }
        item { Column(Modifier.padding(horizontal = 16.dp)) { PlaceholderBanner(data.meta.placeholder) } }
        items(data.lines, key = { it.id }) { line ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppCard(onClick = { onOpenLine(line.id) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LineBadge(line, BadgeSize.LG)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(line.name.text(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            line.directions.forEach { dir ->
                                val next = schedule.nextTrip(line, dir, now)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("→", color = line.color.toColor(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = dir.headsign.text(),
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = next?.let { Schedule.formatMinutes(it) } ?: "–",
                                        fontSize = 14.sp,
                                        color = Astika.colors.muted,
                                    )
                                }
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Astika.colors.muted,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }
    }
}
