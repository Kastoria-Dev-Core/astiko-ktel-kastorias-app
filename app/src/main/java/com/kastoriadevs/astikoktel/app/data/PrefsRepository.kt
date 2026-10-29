package com.kastoriadevs.astikoktel.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prefs")

/** Favourite stops (ordered), theme and voice announcements. Language is kept by AppCompat per-app locales. */
class PrefsRepository(private val context: Context) {

    private val favoritesKey = stringPreferencesKey("favorites")
    private val themeKey = stringPreferencesKey("theme")
    private val voiceKey = booleanPreferencesKey("voice")

    val favorites: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[favoritesKey]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    }

    val theme: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    /** Read out a stop's next departures when it opens. Off unless turned on in Settings. */
    val voice: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[voiceKey] ?: false }

    suspend fun setVoice(on: Boolean) {
        context.dataStore.edit { it[voiceKey] = on }
    }

    suspend fun toggleFavorite(stopId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[favoritesKey]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            val next = if (stopId in current) current - stopId else current + stopId
            prefs[favoritesKey] = next.joinToString(",")
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }
}
