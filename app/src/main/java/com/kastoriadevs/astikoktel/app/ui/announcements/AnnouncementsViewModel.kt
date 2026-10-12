package com.kastoriadevs.astikoktel.app.ui.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kastoriadevs.astikoktel.app.AstikaApp
import com.kastoriadevs.astikoktel.app.data.Announcement
import com.kastoriadevs.astikoktel.app.data.AnnouncementsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AnnouncementsViewModel(private val repo: AnnouncementsRepository) : ViewModel() {

    /** null while loading for the first time */
    val announcements: StateFlow<List<Announcement>?> = repo.announcements
    val failed: StateFlow<Boolean> = repo.failed

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch { repo.refresh(force) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AstikaApp
                AnnouncementsViewModel(app.container.announcements)
            }
        }
    }
}
