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

sealed interface NearbyState {
    data object Idle : NearbyState
    data object Loading : NearbyState
    data class Ready(val coords: Coords) : NearbyState
    data object Denied : NearbyState
    data object Disabled : NearbyState
    data object Error : NearbyState
}

class HomeViewModel(private val location: LocationClient) : ViewModel() {

    private val _nearby = MutableStateFlow<NearbyState>(NearbyState.Idle)
    val nearby: StateFlow<NearbyState> = _nearby.asStateFlow()

    fun hasPermission(): Boolean = location.hasPermission()

    fun hasPrecisePermission(): Boolean = location.hasPrecisePermission()

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

    fun onPermissionDenied() {
        _nearby.value = NearbyState.Denied
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AstikaApp
                HomeViewModel(app.container.location)
            }
        }
    }
}
