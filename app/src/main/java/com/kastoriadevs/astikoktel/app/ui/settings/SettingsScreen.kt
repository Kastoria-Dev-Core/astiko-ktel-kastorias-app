package com.kastoriadevs.astikoktel.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.ui.Modifier
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

@Composable
fun SettingsScreen(appVm: AppViewModel) {
    StatusBarIcons(darkIcons = !isAppDark())
    val meta = appContainer().transit.data.meta
    val theme by appVm.theme.collectAsStateWithLifecycle()
    val language = currentLanguage()

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
                Column {
                    AboutRow(stringResource(R.string.data_source), meta.source)
                    HorizontalDivider(color = Astika.colors.border)
                    AboutRow(stringResource(R.string.data_updated), meta.updated)
                    HorizontalDivider(color = Astika.colors.border)
                    AboutRow(stringResource(R.string.version), BuildConfig.VERSION_NAME)
                }
                PlaceholderBanner(meta.placeholder)
                Text(stringResource(R.string.unofficial), fontSize = 14.sp, color = Astika.colors.muted)
                Text("Map data © OpenStreetMap contributors", fontSize = 12.sp, color = Astika.colors.muted)
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = Astika.colors.muted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}
