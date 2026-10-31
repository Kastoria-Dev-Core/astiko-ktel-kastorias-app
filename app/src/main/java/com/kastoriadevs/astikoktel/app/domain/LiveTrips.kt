package com.kastoriadevs.astikoktel.app.domain

import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.LiveBus
import com.kastoriadevs.astikoktel.app.data.TransitData
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A trip of the timetable that's sending where its bus is, and how far off the timetable it runs */
data class LiveTrip(
    /** [Line.id] */
    val lineId: String,
    /** [Direction.id] */
    val directionId: String,
    /** When the trip left its first stop by the timetable, minutes since midnight: which trip it is */
    val tripStart: Int,
    /** Minutes behind the timetable, or ahead when negative */
    val delay: Int,
    /** The last stop the bus has passed, as an index in the direction's stops */
    val passedIndex: Int,
)

/** A bus further than this from its route is somewhere else: no times from it */
private const val MAX_OFF_ROUTE_METERS = 300.0

/** Delays outside this, in minutes, are more likely the wrong trip than a bus that far off */
private const val EARLIEST = -5.0
private const val LATEST = 30.0

/**
 * Which trip each live bus is, and how late. The bus is placed on its direction between the two
 * stops it's nearest to, which by the timetable it reaches some minutes after leaving the first
 * stop; of the day's trips, the one that should be there closest to when the position was taken
 * is the bus, and the difference is its delay.
 *
 * Nothing comes from a bus that's off its route, still at its first stop (waiting there says nothing
 * about when it leaves), or that no trip of the day fits.
 */
fun liveTrips(
    data: TransitData,
    schedule: Schedule,
    buses: List<LiveBus>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<LiveTrip> = buses.mapNotNull { bus ->
    val line = data.line(bus.line) ?: return@mapNotNull null
    val direction = line.directions.firstOrNull { it.id == bus.direction } ?: return@mapNotNull null
    val stops = direction.stops.mapNotNull { ds -> data.stop(ds.stop)?.let { Triple(it.lat, it.lng, ds.offset) } }
    if (stops.size < 2 || stops.size != direction.stops.size) return@mapNotNull null

    // Flat metres around the bus, close enough within a town
    val kx = 111_320.0 * cos(Math.toRadians(bus.lat))
    val ky = 110_540.0
    fun xy(lat: Double, lng: Double) = (lng - bus.lng) * kx to (lat - bus.lat) * ky

    var best: Triple<Int, Double, Double>? = null // segment, fraction along it, distance
    for (i in 0 until stops.lastIndex) {
        val (ax, ay) = xy(stops[i].first, stops[i].second)
        val (bx, by) = xy(stops[i + 1].first, stops[i + 1].second)
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx * dx + dy * dy
        val t = if (length2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / length2).coerceIn(0.0, 1.0)
        val distance = hypot(ax + t * dx, ay + t * dy)
        if (best == null || distance < best.third) best = Triple(i, t, distance)
    }
    val (segment, fraction, distance) = best ?: return@mapNotNull null
    if (distance > MAX_OFF_ROUTE_METERS) return@mapNotNull null
    if (segment == 0 && fraction < 0.05) return@mapNotNull null

    val scheduledOffset = stops[segment].third + fraction * (stops[segment + 1].third - stops[segment].third)
    val seen = runCatching { LocalDateTime.ofInstant(Instant.parse(bus.t), zone) }.getOrNull() ?: return@mapNotNull null
    val seenMinutes = seen.hour * 60 + seen.minute + seen.second / 60.0
    val trip = schedule.departuresFor(line.id, direction.id, seen.toLocalDate())
        .map { start -> start to seenMinutes - (start + scheduledOffset) }
        .filter { (_, delay) -> delay in EARLIEST..LATEST }
        .minByOrNull { (_, delay) -> abs(delay) }
        ?: return@mapNotNull null

    LiveTrip(
        lineId = line.id,
        directionId = direction.id,
        tripStart = trip.first,
        delay = trip.second.roundToInt(),
        // Right at the next stop counts as passed too
        passedIndex = if (fraction > 0.95) segment + 1 else segment,
    )
}
