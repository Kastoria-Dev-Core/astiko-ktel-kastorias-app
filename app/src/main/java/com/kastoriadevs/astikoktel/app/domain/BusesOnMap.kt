package com.kastoriadevs.astikoktel.app.domain

import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.LiveBus
import com.kastoriadevs.astikoktel.app.data.TransitData

/** A bus to draw on the map, where it really is or where the timetable says it should be */
data class BusOnMap(
    val line: Line,
    val direction: Direction,
    val lat: Double,
    val lng: Double,
    /** Sent by the phone in the bus, rather than estimated */
    val live: Boolean,
)

/**
 * The buses to draw: the ones sending where they are, and the timetable's estimates for the other
 * lines. A line with any bus sending shows only its real buses, so the same bus isn't drawn twice
 * (once where it is and once where the timetable thinks it is). Live buses of a line or direction
 * the app doesn't know are left out.
 */
fun busesOnMap(data: TransitData, estimated: List<EstimatedBus>, live: List<LiveBus>): List<BusOnMap> {
    val real = live.mapNotNull { bus ->
        val line = data.line(bus.line) ?: return@mapNotNull null
        val direction = line.directions.firstOrNull { it.id == bus.direction } ?: return@mapNotNull null
        BusOnMap(line, direction, bus.lat, bus.lng, live = true)
    }
    val liveLines = real.map { it.line.id }.toSet()
    return real + estimated
        .filter { it.line.id !in liveLines }
        .map { BusOnMap(it.line, it.direction, it.lat, it.lng, live = false) }
}
