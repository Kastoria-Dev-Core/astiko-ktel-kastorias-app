package com.kastoriadevs.astikoktel.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kastoriadevs.astikoktel.app.AppContainer
import com.kastoriadevs.astikoktel.app.AstikaApp
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.Localized
import com.kastoriadevs.astikoktel.app.data.Season
import com.kastoriadevs.astikoktel.app.data.ServiceDay
import com.kastoriadevs.astikoktel.app.data.Stop
import com.kastoriadevs.astikoktel.app.domain.LiveTrip
import com.kastoriadevs.astikoktel.app.domain.liveTrips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.LocalDateTime
import java.util.Locale

/** Current time, refreshed every [intervalMs] while the screen is composed */
@Composable
fun rememberNow(intervalMs: Long = 15_000L): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = LocalDateTime.now()
        }
    }
    return now
}

/**
 * The trips whose bus is sending where it is, with how late each runs, asked again every 15 seconds
 * while the screen shows. Empty when none are sending or there's no signal.
 */
@Composable
fun rememberLiveTrips(): List<LiveTrip> {
    val container = appContainer()
    val lifecycleOwner = LocalLifecycleOwner.current
    var trips by remember { mutableStateOf(emptyList<LiveTrip>()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val buses = container.liveBuses.buses()
                trips = withContext(Dispatchers.Default) { liveTrips(container.transit.data, container.schedule, buses) }
                delay(LIVE_REFRESH_MS)
            }
        }
    }
    return trips
}

/** How often the live times are asked for; the server's answer is cached for 10 seconds anyway */
private const val LIVE_REFRESH_MS = 15_000L

/** "el" or "en", following the app language */
@Composable
fun currentLanguage(): String {
    val tag = LocalConfiguration.current.locales[0].language
    return if (tag == "el") "el" else "en"
}

/** The text in the app's current language */
@Composable
fun Localized.text(): String = get(currentLanguage())

/** A stop's name as shown where it's picked or opened: "Αφετηρία (αναχωρήσεις)" when it has a qualifier */
@Composable
fun Stop.title(): String {
    val base = name.text()
    val extra = qualifier?.text() ?: return base
    // An English name that already ends in brackets, "Afetiria (Terminal)", gets "(Terminal, departures)"
    return if (base.endsWith(")")) "${base.dropLast(1)}, $extra)" else "$base ($extra)"
}

/** The app's [AppContainer], from inside any composable */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as AstikaApp).container

/** "#C2410C" to a Compose colour; throws on anything that isn't a colour */
fun String.toColor(): Color = Color(android.graphics.Color.parseColor(this))

/** The activity this context belongs to, unwrapping ContextWrappers, or null when there's none */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Whether the app (not necessarily the system) is currently in dark theme */
@Composable
fun isAppDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The name of this kind of day, e.g. "Weekdays" */
@StringRes
fun ServiceDay.labelRes(): Int = when (this) {
    ServiceDay.WEEKDAY -> R.string.weekday
    ServiceDay.SATURDAY -> R.string.saturday
    ServiceDay.SUNDAY -> R.string.sunday
    ServiceDay.WEDNESDAY -> R.string.wednesday
}

/** "Winter" or "Summer" */
@StringRes
fun Season.labelRes(): Int = when (this) {
    Season.WINTER -> R.string.winter
    Season.SUMMER -> R.string.summer
}

/** Lower-case, accent-insensitive form for searching Greek and English names */
fun normalizeForSearch(s: String): String = normalizeSearch(s)

/** Light or dark status bar icons while this screen is shown */
@Composable
fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(darkIcons) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
        }
        onDispose { }
    }
}
