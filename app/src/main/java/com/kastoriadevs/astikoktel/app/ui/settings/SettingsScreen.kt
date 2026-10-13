package com.kastoriadevs.astikoktel.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kastoriadevs.astikoktel.app.BuildConfig
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.ThemeMode
import com.kastoriadevs.astikoktel.app.ui.AppViewModel
import com.kastoriadevs.astikoktel.app.ui.common.StatusBarIcons
import com.kastoriadevs.astikoktel.app.ui.common.appContainer
import com.kastoriadevs.astikoktel.app.ui.common.currentLanguage
import com.kastoriadevs.astikoktel.app.ui.common.isAppDark
import com.kastoriadevs.astikoktel.app.ui.components.AppCard
import com.kastoriadevs.astikoktel.app.ui.components.HeaderTitle
import com.kastoriadevs.astikoktel.app.ui.components.PlaceholderBanner
import com.kastoriadevs.astikoktel.app.ui.components.ScreenHeader
import com.kastoriadevs.astikoktel.app.ui.components.Segmented
import com.kastoriadevs.astikoktel.app.ui.theme.Astika
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(appVm: AppViewModel) {
    StatusBarIcons(darkIcons = !isAppDark())
    val meta = appContainer().transit.data.meta
    val theme by appVm.theme.collectAsStateWithLifecycle()
    val language = currentLanguage()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = { HeaderTitle(stringResource(R.string.tab_settings)) })
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                CardTitle(stringResource(R.string.language))
                Segmented(
                    options = listOf("el" to "Ελληνικά", "en" to "English"),
                    selected = language,
                    // Per-app language; AppCompat recreates the activity and remembers the choice
                    onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
                )
            }

            AppCard {
                CardTitle(stringResource(R.string.theme))
                Segmented(
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                    ),
                    selected = theme ?: ThemeMode.SYSTEM,
                    onSelect = appVm::setTheme,
                )
            }

            AppCard {
                CardTitle(stringResource(R.string.about))
                // "Name (website)" in meta.json: the name, then the website as a link
                val website = Regex("""\(([^)]+\.[a-z]+)\)$""").find(meta.source)?.groupValues?.get(1)
                val sourceName = if (website == null) meta.source else meta.source.substringBeforeLast(" (")
                Column {
                    AboutRow(stringResource(R.string.data_source), sourceName)
                    if (website != null) {
                        HorizontalDivider(color = Astika.colors.border)
                        AboutRow(stringResource(R.string.website), website, onClick = { uriHandler.openUri("https://$website") })
                    }
                    HorizontalDivider(color = Astika.colors.border)
                    AboutRow(stringResource(R.string.data_updated), formatDate(meta.updated))
                    HorizontalDivider(color = Astika.colors.border)
                    AboutRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
                }
                CardTitle(stringResource(R.string.contributors))
                Column {
                    stringArrayResource(R.array.contributors).forEachIndexed { index, name ->
                        if (index > 0) HorizontalDivider(color = Astika.colors.border)
                        Text(name, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
                PlaceholderBanner(meta.placeholder)
                Text(stringResource(R.string.unofficial), fontSize = 14.sp, color = Astika.colors.muted)
                Text(stringResource(R.string.map_attribution), fontSize = 12.sp, color = Astika.colors.muted)
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

/** A date from meta.json (yyyy-MM-dd) written out in the app's language, e.g. 13 October 2026 */
@Composable
private fun formatDate(date: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    return runCatching { LocalDate.parse(date).format(formatter) }.getOrDefault(date)
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The label keeps its width and a long value wraps beside it, instead of squeezing the label
        Text(label, color = Astika.colors.muted)
        Text(
            value,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) Astika.colors.link else Color.Unspecified,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
