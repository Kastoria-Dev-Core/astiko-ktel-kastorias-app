package com.kastoriadevs.astikoktel.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kastoriadevs.astikoktel.app.domain.Coords
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationDisabledException : Exception("Location services are off")

/** One-shot current location through the fused provider */
class LocationClient(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun locationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Call only after the permission is granted. Indoors a fresh GPS fix can take very
     * long or never come, so after [FIX_TIMEOUT_MS] it settles for the last known position.
     */
    suspend fun current(): Coords {
        if (!locationEnabled()) throw LocationDisabledException()
        return withTimeoutOrNull(FIX_TIMEOUT_MS) { freshFix() }
            ?: lastKnown()
            ?: throw IllegalStateException("No location")
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(): Coords {
        val cancel = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cancel.cancel() }
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) cont.resume(Coords(loc.latitude, loc.longitude, loc.accuracy))
                    else cont.resumeWithException(IllegalStateException("No location"))
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(): Coords? = suspendCancellableCoroutine { cont ->
        fused.lastLocation
            .addOnSuccessListener { loc -> cont.resume(loc?.let { Coords(it.latitude, it.longitude, it.accuracy) }) }
            .addOnFailureListener { cont.resume(null) }
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 10_000L
    }
}
