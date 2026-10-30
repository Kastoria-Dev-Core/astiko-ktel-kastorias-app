package com.kastoriadevs.astikoktel.app.domain

import com.kastoriadevs.astikoktel.app.data.Stop
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A position from the phone */
data class Coords(
    /** Latitude, WGS84 */
    val lat: Double,
    /** Longitude, WGS84 */
    val lng: Double,
    /** Radius in metres the real position is likely within */
    val accuracy: Float,
)

/** Distances and street paths on the map */
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

    /** The [count] stops closest to ([lat], [lng]), closest first, each with its distance in metres */
    fun nearestStops(stops: List<Stop>, lat: Double, lng: Double, count: Int): List<Pair<Stop, Double>> =
        stops.map { it to distanceMeters(lat, lng, it.lat, it.lng) }
            .sortedBy { it.second }
            .take(count)

    /** Decodes an encoded polyline (Google's format, 5 decimals, as OSRM returns it) to (lat, lng) points */
    fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val points = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0
        fun next(): Int {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            return if (result and 1 != 0) (result shr 1).inv() else result shr 1
        }
        while (index < encoded.length) {
            lat += next()
            lng += next()
            points.add(lat / 1e5 to lng / 1e5)
        }
        return points
    }

    /**
     * A distance as people say it: to the nearest 10 m under a kilometre ("250 m", "250 μ."),
     * then in kilometres with one decimal ("1.4 km", "1,4 χλμ.")
     */
    fun formatDistance(meters: Double, language: String): String {
        if (meters < 1000) {
            val rounded = maxOf(10, (meters / 10).roundToInt() * 10)
            return if (language == "el") "$rounded μ." else "$rounded m"
        }
        val km = "%.1f".format(java.util.Locale.US, meters / 1000)
        return if (language == "el") "${km.replace('.', ',')} χλμ." else "$km km"
    }
}
