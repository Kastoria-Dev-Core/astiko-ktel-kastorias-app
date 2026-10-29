package com.kastoriadevs.astikoktel.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kastoriadevs.astikoktel.app.AstikaApp
import com.kastoriadevs.astikoktel.app.data.PrefsRepository
import com.kastoriadevs.astikoktel.app.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide preferences, scoped to the activity and shared by all screens */
class AppViewModel(private val prefs: PrefsRepository) : ViewModel() {

    val favorites: StateFlow<List<String>> =
        prefs.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** null until DataStore has been read, so the first frame doesn't flash the wrong theme */
    val theme: StateFlow<ThemeMode?> =
        prefs.theme.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Default for voice announcements; each stop screen can still mute or unmute them */
    val voice: StateFlow<Boolean> =
        prefs.voice.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setVoice(on: Boolean) {
        viewModelScope.launch { prefs.setVoice(on) }
    }

    fun toggleFavorite(stopId: String) {
        viewModelScope.launch { prefs.toggleFavorite(stopId) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setTheme(mode) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AstikaApp
                AppViewModel(app.container.prefs)
            }
        }
    }
}
