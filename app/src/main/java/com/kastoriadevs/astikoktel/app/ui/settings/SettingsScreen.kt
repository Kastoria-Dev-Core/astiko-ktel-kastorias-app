package com.kastoriadevs.astikoktel.app.ui.settings

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
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
import com.kastoriadevs.astikoktel.app.ui.components.SectionTitle
import com.kastoriadevs.astikoktel.app.ui.components.Segmented
import com.kastoriadevs.astikoktel.app.ui.theme.Astika

/** Opened from the gear on Home: the app's preferences, with "About this app" one level down */
@Composable
fun SettingsScreen(appVm: AppViewModel, onBack: () -> Unit, onOpenAbout: () -> Unit) {
    StatusBarIcons(darkIcons = !isAppDark())
    val theme by appVm.theme.collectAsStateWithLifecycle()
    val voice by appVm.voice.collectAsStateWithLifecycle()
    val language = currentLanguage()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(onBack = onBack, title = { HeaderTitle(stringResource(R.string.tab_settings)) })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.language))
            Segmented(
                options = listOf("el" to "Ελληνικά", "en" to "English"),
                selected = language,
                // Per-app language; AppCompat recreates the activity and remembers the choice
                onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
            )

            SectionTitle(stringResource(R.string.theme), modifier = Modifier.padding(top = 8.dp))
            Segmented(
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                ),
                selected = theme ?: ThemeMode.SYSTEM,
                onSelect = appVm::setTheme,
            )

            SectionTitle(stringResource(R.string.voice_title), modifier = Modifier.padding(top = 8.dp))
            AppCard(onClick = { appVm.setVoice(!voice) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.voice_description),
                        fontSize = 14.sp,
                        color = Astika.colors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = voice, onCheckedChange = appVm::setVoice)
                }
            }

        }

        // At the foot of the page, the way most apps keep "About"; the version is on the About page
        AppCard(
            onClick = onOpenAbout,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = Astika.colors.ink2)
                Text(stringResource(R.string.about_app), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Astika.colors.muted)
            }
        }
    }
}

/** How to reach Αστικό ΚΤΕΛ Καστοριάς, who made the app, and the fine print */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    StatusBarIcons(darkIcons = !isAppDark())
    val meta = appContainer().transit.data.meta
    val uriHandler = LocalUriHandler.current
    // A phone with no dialer, maps or mail app throws here; then the row just does nothing
    val open: (String) -> Unit = { uri -> runCatching { uriHandler.openUri(uri) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(onBack = onBack, title = { HeaderTitle(stringResource(R.string.about_app)) })
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Image(painterResource(R.drawable.ic_app_icon), contentDescription = null, modifier = Modifier.size(64.dp))
                Text(stringResource(R.string.app_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), fontSize = 13.sp, color = Astika.colors.muted)
            }

            PlaceholderBanner(meta.placeholder)

            SectionTitle(stringResource(R.string.contact))
            AppCard(contentPadding = 16.dp) {
                Column {
                    val address = stringResource(R.string.contact_address)
                    AboutRow(
                        stringResource(R.string.contact_address_label),
                        address,
                        onClick = { open("geo:0,0?q=" + Uri.encode(address.replace('\n', ' '))) },
                    )
                    HorizontalDivider(color = Astika.colors.divider)
                    AboutRow(stringResource(R.string.contact_phone), Contact.PHONE, onClick = { open("tel:" + Contact.PHONE.replace(" ", "")) })
                    HorizontalDivider(color = Astika.colors.divider)
                    AboutRow(stringResource(R.string.contact_fax), Contact.FAX)
                    HorizontalDivider(color = Astika.colors.divider)
                    AboutRow(stringResource(R.string.contact_email), Contact.EMAIL, onClick = { open("mailto:" + Contact.EMAIL) })
                }
            }

            SectionTitle(stringResource(R.string.contributors))
            AppCard(contentPadding = 16.dp) {
                Column {
                    stringArrayResource(R.array.contributors).forEachIndexed { index, name ->
                        if (index > 0) HorizontalDivider(color = Astika.colors.divider)
                        Text(
                            name,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.official_app), fontSize = 13.sp, color = Astika.colors.muted)
                Text(stringResource(R.string.map_attribution), fontSize = 12.sp, color = Astika.colors.muted)
                Text(stringResource(R.string.font_attribution), fontSize = 12.sp, color = Astika.colors.muted)
            }
        }
    }
}

/** Αστικό ΚΤΕΛ Καστοριάς */
private object Contact {
    const val PHONE = "+30 24670 83959"
    const val FAX = "+30 24670 21364"
    const val EMAIL = "astikast@otenet.gr"
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The label keeps its width and a long value wraps beside it, instead of squeezing the label
        Text(label, fontSize = 15.sp, color = Astika.colors.muted)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) Astika.colors.link else Color.Unspecified,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
