package com.kastoriadevs.astikoktel.app.ui.stop

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.kastoriadevs.astikoktel.app.R
import com.kastoriadevs.astikoktel.app.data.Departure
import com.kastoriadevs.astikoktel.app.domain.Schedule
import com.kastoriadevs.astikoktel.app.ui.common.text
import java.time.LocalDateTime
import java.util.Locale

/**
 * Reads text out loud with the phone's own text-to-speech engine, in the app's language.
 * The engine starts in the background; whatever is asked before it's ready is said once it is.
 */
class Announcer(
    context: Context,
    private val locale: Locale,
    private val onLanguageMissing: () -> Unit,
) {
    private lateinit var tts: TextToSpeech
    private var ready = false
    private var languageOk = true
    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(locale)
        // Greek text read by an English voice is noise, so without a voice for the language we stay quiet
        languageOk = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        ready = true
        pending?.let { say(it) }
        pending = null
    }

    fun speak(text: String) {
        if (ready) say(text) else pending = text
    }

    private fun say(text: String) {
        if (!languageOk) {
            onLanguageMissing()
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        pending = null
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "astika-departures"
    }
}

/** An [Announcer] for the current screen, released when the screen goes away */
@Composable
fun rememberAnnouncer(onLanguageMissing: () -> Unit): Announcer {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val announcer = remember(locale) { Announcer(context, locale, onLanguageMissing) }
    DisposableEffect(announcer) { onDispose { announcer.shutdown() } }
    return announcer
}

/**
 * What is said for a stop: its name, then up to three departures, e.g.
 * "Στάση Αφετηρία 2. Γραμμή 4, Πανεπιστήμιο, σε 14 λεπτά. Γραμμή 7, Εργατικές Κατοικίες, στις 16."
 * Line numbers are said without the leading zero the badges show, and times in words a voice reads well.
 */
@Composable
fun departuresAnnouncement(stopName: String, departures: List<Departure>, now: LocalDateTime): String {
    if (departures.isEmpty()) return stringResource(R.string.voice_none, stopName)
    val parts = departures.take(3).map { dep ->
        val mins = Schedule.minutesUntil(dep, now)
        val whenText = when {
            dep.dayOffset > 0 -> stringResource(R.string.voice_tomorrow_at, spokenTime(dep.minutes))
            mins <= 0 -> stringResource(R.string.voice_now)
            mins < 60 -> pluralStringResource(R.plurals.voice_in_minutes, mins, mins)
            else -> stringResource(R.string.voice_at, spokenTime(dep.minutes))
        }
        stringResource(R.string.voice_departure, dep.line.number.trimStart('0'), dep.line.name.text(), whenText)
    }
    return stringResource(R.string.voice_intro, stopName) + " " + parts.joinToString(" ")
}

@Composable
private fun spokenTime(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    val hour = m / 60
    val minute = m % 60
    return if (minute == 0) stringResource(R.string.voice_time_hour, hour) else stringResource(R.string.voice_time, hour, minute)
}
