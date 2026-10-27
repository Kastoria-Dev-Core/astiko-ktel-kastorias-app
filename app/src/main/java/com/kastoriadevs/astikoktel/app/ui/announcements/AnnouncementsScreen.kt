package com.kastoriadevs.astikoktel.app.ui.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.Announcement
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.HeaderTitle
import com.kastoriadevs.astikoktel.app.ui.components.ScreenHeader
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnnouncementsScreen(
    onBack: (() -> Unit)?,
    vm: AnnouncementsViewModel = viewModel(factory = AnnouncementsViewModel.Factory),
) {
    StatusBarIcons(darkIcons = !isAppDark())
    val announcements by vm.announcements.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            onBack = onBack,
            title = { HeaderTitle(stringResource(R.string.announcements)) },
            trailing = {
                IconButton(onClick = { vm.refresh(force = true) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            },
        )
        val list = announcements
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (failed) {
                    item {
                        Text(
                            stringResource(if (list.isEmpty()) R.string.announcements_offline_empty else R.string.announcements_offline),
                            color = Astika.colors.muted,
                            fontSize = 14.sp,
                        )
                    }
                } else if (list.isEmpty()) {
                    item { Text(stringResource(R.string.announcements_none), color = Astika.colors.muted, fontSize = 14.sp) }
                }
                items(list, key = { it.id }) { announcement ->
                    AnnouncementCard(
                        announcement = announcement,
                        onOpenImage = { viewing = it },
                        onOpenWebsite = { uriHandler.openUri(announcement.url) },
                    )
                }
            }
        }
    }

    viewing?.let { url -> ImageViewer(url = url, onDismiss = { viewing = null }) }
}

@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    onOpenImage: (String) -> Unit,
    onOpenWebsite: () -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(announcement.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(publishedDate(announcement), fontSize = 13.sp, color = Astika.colors.muted)
        }
        announcement.image?.let { image ->
            SubcomposeAsyncImage(
                model = image,
                contentDescription = stringResource(R.string.announcement_image, announcement.title),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                loading = { ImagePlaceholder { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } },
                error = { ImagePlaceholder { Text(stringResource(R.string.image_unavailable), color = Astika.colors.muted, fontSize = 13.sp) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenImage(image) },
            )
        }
        if (announcement.text.isNotBlank()) {
            Text(announcement.text, fontSize = 15.sp, lineHeight = 21.sp)
        }
        TextButton(onClick = onOpenWebsite, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(R.string.open_website), color = Astika.colors.link, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ImagePlaceholder(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Compact row for the Home screen: thumbnail, title, date */
@Composable
fun AnnouncementRow(announcement: Announcement, onClick: () -> Unit) {
    AppCard(onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (announcement.image != null) {
                AsyncImage(
                    model = announcement.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    announcement.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(publishedDate(announcement), fontSize = 13.sp, color = Astika.colors.muted)
            }
        }
    }
}

@Composable
private fun publishedDate(announcement: Announcement): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    return announcement.publishedAt.atZone(ZoneId.systemDefault()).format(formatter)
}

/** Full-screen image with pinch to zoom, drag to pan, double tap to reset */
@Composable
private fun ImageViewer(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale == 1f) Offset.Zero else offset + pan * scale
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        })
                    }
                    .transformable(transform)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}
