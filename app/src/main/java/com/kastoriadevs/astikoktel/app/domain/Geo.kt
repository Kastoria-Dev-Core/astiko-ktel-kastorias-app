package com.kastoriadevs.astikoktel.app.domain

import com.kastoriadevs.astikoktel.app.data.Stop
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Coords(val lat: Double, val lng: Double, val accuracy: Float)

object Geo {
    /** Distance in metres between two points */
    fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = 6_371_000.0
        val rad = Math.PI / 180
        val dLat = (bLat - aLat) * rad
        val dLng = (bLng - aLng) * rad
        val h = sin(dLat / 2).pow(2) + cos(aLat * rad) * cos(bLat * rad) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun nearestStops(stops: List<Stop>, lat: Double, lng: Double, count: Int): List<Pair<Stop, Double>> =
        stops.map { it to distanceMeters(lat, lng, it.lat, it.lng) }
            .sortedBy { it.second }
            .take(count)

    fun formatDistance(meters: Double, language: String): String {
        if (meters < 1000) {
            val rounded = maxOf(10, (meters / 10).roundToInt() * 10)
            return if (language == "el") "$rounded μ." else "$rounded m"
        }
        val km = "%.1f".format(java.util.Locale.US, meters / 1000)
        return if (language == "el") "${km.replace('.', ',')} χλμ." else "$km km"
    }
}
