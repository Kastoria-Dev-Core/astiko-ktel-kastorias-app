package com.kastoriadevs.astikoktel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kastoriadevs.astikoktel.app.R

// Palette taken from the app icon (B3, "route by the lake")
val Lake = Color(0xFF0E4C6D) // icon background
val LakeMid = Color(0xFF16627F) // icon lake shape
val LakeLight = Color(0xFF1B7394) // icon inner lake shape
val RouteYellow = Color(0xFFF2B33D) // icon route line and windshield
val OffWhite = Color(0xFFF7F3EA) // icon bus disc and stop rings

// Text and surfaces, same hues as the icon
private val Ink = Color(0xFF10212B)
private val Ink2 = Color(0xFF4D5C66)
private val Ink3 = Color(0xFF5F6D76)
private val Paper = Color(0xFFF6F4EF)
private val SandLine = Color(0xFFE4DFD3)
private val SandDivider = Color(0xFFEEEAE0)
private val SandTrack = Color(0xFFECE7DC)
// Dark mode: near-neutral graphite with a faint cool tint, so the lake blue and the line colours
// carry the colour instead of a navy background. Text steps and borders kept low in contrast.
private val Graphite = Color(0xFF101315) // background
private val GraphiteRaised = Color(0xFF181C1F) // cards, sheets, bars
private val GraphiteHigh = Color(0xFF22272B) // segmented track, pressed
private val GraphiteLine = Color(0xFF2A3034) // borders
private val NightText = Color(0xFFE6E9EB)
private val NightText2 = Color(0xFFB3BBC0)
private val NightText3 = Color(0xFF8E979D)
private val LakeOnDark = Color(0xFF7CB6D4) // the lake blue lifted for text and icons on dark

/**
 * Commissioner, bundled from Google Fonts (OFL). Latin, Latin Extended and Greek merged per weight,
 * with the digits widened to one width so times line up in lists.
 */
val Commissioner = FontFamily(
    Font(R.font.commissioner_regular, FontWeight.Normal),
    Font(R.font.commissioner_medium, FontWeight.Medium),
    Font(R.font.commissioner_semibold, FontWeight.SemiBold),
    Font(R.font.commissioner_bold, FontWeight.Bold),
)

/** Brand colours that Material's scheme has no slot for */
@Immutable
data class AstikaColors(
    val hero: Color,
    val heroText: Color,
    val heroMuted: Color,
    val link: Color,
    val soon: Color,
    val bannerBg: Color,
    val bannerBorder: Color,
    val bannerText: Color,
    val star: Color,
    val muted: Color,
    val border: Color,
    val stopRing: Color,
    val stopFill: Color,
    /** Secondary text, one step stronger than [muted] */
    val ink2: Color,
    /** Hairlines between rows inside a card */
    val divider: Color,
    /** Track behind segmented controls */
    val track: Color,
    /** Selected chip, nav indicator */
    val activeBg: Color,
    /** Real-time data (the signal icon and its minutes) */
    val live: Color,
    /** Row background for the next bus */
    val highlight: Color,
    /** Selected filter chip */
    val chipOn: Color,
    val chipOnText: Color,
)

private val LightExtra = AstikaColors(
    hero = Lake,
    heroText = OffWhite,
    heroMuted = Color(0xFFBFD6E1),
    link = Lake,
    soon = Lake,
    bannerBg = Color(0xFFFCEFD2),
    bannerBorder = Color(0xFFF1DDAE),
    bannerText = Color(0xFF5A3D00),
    star = Color(0xFFC88A12),
    muted = Ink3,
    border = SandLine,
    stopRing = Lake,
    stopFill = Color.White,
    ink2 = Ink2,
    divider = SandDivider,
    track = SandTrack,
    activeBg = Color(0xFFE1ECF1),
    live = Color(0xFF0E5F86),
    highlight = Color(0xFFFBF6EA),
    chipOn = Ink,
    chipOnText = Color.White,
)

private val DarkExtra = AstikaColors(
    hero = Lake,
    heroText = OffWhite,
    heroMuted = Color(0xFFA9C4D1),
    link = LakeOnDark,
    soon = LakeOnDark,
    bannerBg = Color(0xFF2A2416),
    bannerBorder = Color(0xFF453A1E),
    bannerText = Color(0xFFE8C57A),
    star = RouteYellow,
    muted = NightText3,
    border = GraphiteLine,
    stopRing = OffWhite,
    stopFill = GraphiteRaised,
    ink2 = NightText2,
    divider = Color(0xFF23282B),
    track = GraphiteHigh,
    activeBg = Color(0xFF1F2C33),
    live = LakeOnDark,
    highlight = Color(0xFF1C2328),
    chipOn = NightText,
    chipOnText = Graphite,
)

private val LightScheme = lightColorScheme(
    primary = Lake,
    onPrimary = Color.White,
    primaryContainer = LakeLight,
    onPrimaryContainer = OffWhite,
    secondary = RouteYellow,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SandTrack,
    onSurfaceVariant = Ink2,
    surfaceContainer = Color.White,
    outline = Color(0xFFC9D7DF),
    outlineVariant = SandLine,
)

private val DarkScheme = darkColorScheme(
    primary = LakeOnDark,
    onPrimary = Color(0xFF0B1820),
    primaryContainer = LakeMid,
    onPrimaryContainer = OffWhite,
    secondary = RouteYellow,
    onSecondary = Ink,
    background = Graphite,
    onBackground = NightText,
    surface = GraphiteRaised,
    onSurface = NightText,
    surfaceVariant = GraphiteHigh,
    onSurfaceVariant = NightText2,
    surfaceContainer = GraphiteRaised,
    outline = Color(0xFF3A4247),
    outlineVariant = GraphiteLine,
)

private fun TextStyle.withBrandFont() = copy(fontFamily = Commissioner)

private val AstikaTypography = Typography().run {
    copy(
        displayLarge = displayLarge.withBrandFont(),
        displayMedium = displayMedium.withBrandFont(),
        displaySmall = displaySmall.withBrandFont(),
        headlineLarge = headlineLarge.withBrandFont(),
        headlineMedium = headlineMedium.withBrandFont(),
        headlineSmall = headlineSmall.withBrandFont(),
        titleLarge = titleLarge.withBrandFont(),
        titleMedium = titleMedium.withBrandFont(),
        titleSmall = titleSmall.withBrandFont(),
        bodyLarge = bodyLarge.withBrandFont(),
        bodyMedium = bodyMedium.withBrandFont(),
        bodySmall = bodySmall.withBrandFont(),
        labelLarge = labelLarge.withBrandFont(),
        labelMedium = labelMedium.withBrandFont(),
        labelSmall = labelSmall.withBrandFont(),
    )
}

val LocalAstikaColors = staticCompositionLocalOf { LightExtra }

object Astika {
    val colors: AstikaColors
        @Composable get() = LocalAstikaColors.current
}

@Composable
fun AstikaTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAstikaColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AstikaTypography,
            content = content,
        )
    }
}
