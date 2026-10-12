package com.kastoriadevs.astikoktel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Palette taken from the app icon (B3, "route by the lake")
val Lake = Color(0xFF0E4C6D) // icon background
val LakeMid = Color(0xFF16627F) // icon lake shape
val LakeLight = Color(0xFF1B7394) // icon inner lake shape
val RouteYellow = Color(0xFFF2B33D) // icon route line and windshield
val OffWhite = Color(0xFFF7F3EA) // icon bus disc and stop rings

// Derived tones (same hues, adjusted lightness for text contrast and dark mode)
private val Ink = Color(0xFF12232D) // near-black with a lake tint, body text on light
private val LakeDeep = Color(0xFF071E2A) // dark mode background
private val LakeNight = Color(0xFF0C2B3A) // dark mode cards
private val LakeDusk = Color(0xFF123B4F) // dark mode raised/selected surfaces
private val SandLine = Color(0xFFE5DDCB) // light mode borders, from OffWhite
private val Sand = Color(0xFFEDE6D6) // light mode segmented track, from OffWhite
private val Amber = Color(0xFF8F5E05) // RouteYellow darkened for text on light (AA on white)

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
)

private val LightExtra = AstikaColors(
    hero = Lake,
    heroText = OffWhite,
    heroMuted = Color(0xFFBFD6E1),
    link = Lake,
    soon = Amber,
    bannerBg = Color(0xFFFCF0D3),
    bannerBorder = RouteYellow,
    bannerText = Color(0xFF5A3D00),
    star = Color(0xFFB97E10), // RouteYellow darkened to 3:1 against white

    muted = Color(0xFF52626C),
    border = SandLine,
    stopRing = Lake,
    stopFill = Color.White,
)

private val DarkExtra = AstikaColors(
    hero = Lake,
    heroText = OffWhite,
    heroMuted = Color(0xFFA9C4D1),
    link = RouteYellow,
    soon = RouteYellow,
    bannerBg = Color(0xFF2B2410),
    bannerBorder = Color(0xFF8A6A1E),
    bannerText = Color(0xFFF6E2AE),
    star = RouteYellow,
    muted = Color(0xFF9DB4BF),
    border = Color(0xFF1C4A5E),
    stopRing = OffWhite,
    stopFill = LakeNight,
)

private val LightScheme = lightColorScheme(
    primary = Lake,
    onPrimary = OffWhite,
    primaryContainer = LakeLight,
    onPrimaryContainer = OffWhite,
    secondary = RouteYellow,
    onSecondary = Ink,
    background = OffWhite,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Color(0xFF52626C),
    surfaceContainer = Color.White,
    outline = Color(0xFFC8BFAC),
    outlineVariant = SandLine,
)

private val DarkScheme = darkColorScheme(
    primary = RouteYellow,
    onPrimary = Ink,
    primaryContainer = LakeMid,
    onPrimaryContainer = OffWhite,
    secondary = RouteYellow,
    onSecondary = Ink,
    background = LakeDeep,
    onBackground = OffWhite,
    surface = LakeNight,
    onSurface = OffWhite,
    surfaceVariant = LakeDusk,
    onSurfaceVariant = Color(0xFF9DB4BF),
    surfaceContainer = LakeNight,
    outline = LakeMid,
    outlineVariant = Color(0xFF1C4A5E),
)

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
            content = content,
        )
    }
}
