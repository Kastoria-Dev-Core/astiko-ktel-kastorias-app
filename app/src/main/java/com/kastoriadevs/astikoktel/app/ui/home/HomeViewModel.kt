package com.kastoriadevs.astikoktel.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kastoriadevs.astikoktel.app.AstikaApp
import com.kastoriadevs.astikoktel.app.data.LocationClient
import com.kastoriadevs.astikoktel.app.data.LocationDisabledException
import com.kastoriadevs.astikoktel.app.domain.Coords
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where finding the user's position is at, for stops nearby and "Where do you want to go?" */
sealed interface NearbyState {
    /** Not asked yet */
    data object Idle : NearbyState

    /** Waiting for a fix */
    data object Loading : NearbyState

    /** Found */
    data class Ready(
        /** Where the user is */
        val coords: Coords,
    ) : NearbyState

    /** The location permission was refused */
    data object Denied : NearbyState

    /** Location is off on the phone */
    data object Disabled : NearbyState

    /** No position came, e.g. no fix indoors and no recent one to fall back on */
    data object Error : NearbyState
}

/** Home's state that outlives a rotation: the user's position */
class HomeViewModel(private val location: LocationClient) : ViewModel() {

    private val _nearby = MutableStateFlow<NearbyState>(NearbyState.Idle)
    /** [NearbyState.Idle] until [locate] or [onPermissionDenied] */
    val nearby: StateFlow<NearbyState> = _nearby.asStateFlow()

    /** The location permission is granted already, so [locate] can be called without asking */
    fun hasPermission(): Boolean = location.hasPermission()

    /** Call after the location permission is granted */
    fun locate() {
        _nearby.value = NearbyState.Loading
        viewModelScope.launch {
            _nearby.value = try {
                NearbyState.Ready(location.current())
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocationDisabledException) {
                NearbyState.Disabled
            } catch (e: Exception) {
                NearbyState.Error
            }
        }
    }

    /** Call when the user refuses the location permission */
    fun onPermissionDenied() {
        _nearby.value = NearbyState.Denied
    }

    companion object {
        /** Builds it from the [AstikaApp]'s container, for `viewModel(factory = …)` */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AstikaApp
                HomeViewModel(app.container.location)
            }
        }
    }
}
