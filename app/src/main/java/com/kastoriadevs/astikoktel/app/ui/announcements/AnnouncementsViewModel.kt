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

/** The announcements list and the dot on Home's bell; fetches the feed when it's made */
class AnnouncementsViewModel(private val repo: AnnouncementsRepository) : ViewModel() {

    /** null while loading for the first time */
    val announcements: StateFlow<List<Announcement>?> = repo.announcements
    /** The feed couldn't be reached; what's shown is from the cache */
    val failed: StateFlow<Boolean> = repo.failed

    init {
        refresh()
    }

    /** Fetches the feed again, unless it was fetched in the last 15 minutes and [force] is false */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch { repo.refresh(force) }
    }

    companion object {
        /** Builds it from the [AstikaApp]'s container, for `viewModel(factory = …)` */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AstikaApp
                AnnouncementsViewModel(app.container.announcements)
            }
        }
    }
}
