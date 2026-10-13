package com.kastoriadevs.astikoktel.app.data

import com.kastoriadevs.astikoktel.app.domain.Geo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Localized(val el: String, val en: String) {
    fun get(language: String): String = if (language == "el") el else en.ifBlank { el }
}

@Serializable
data class Stop(
    val id: String,
    val name: Localized,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class DirectionStop(
    val stop: String,
    /** Minutes after the trip leaves the first stop */
    val offset: Int,
)

@Serializable
data class Direction(
    val id: String,
    val headsign: Localized,
    val stops: List<DirectionStop>,
    /**
     * Street path through the stops as an encoded polyline, made by scripts/snap_routes.py.
     * Without it the map draws straight lines between the stops.
     */
    val shape: String? = null,
    /** Other ways this direction can end, e.g. along the lake to Αφετηρία 2 instead of past the ΚΤΕΛ */
    val branches: List<Branch> = emptyList(),
)

/** An alternative ending: the stops from where it leaves the direction to its own last stop */
@Serializable
data class Branch(
    val stops: List<String>,
    val shape: String? = null,
)

@Serializable
data class Line(
    val id: String,
    val number: String,
    val name: Localized,
    /** Hex colour, e.g. "#C2410C" */
    val color: String,
    val textColor: String,
    val directions: List<Direction>,
)

@Serializable
enum class ServiceDay {
    @SerialName("weekday") WEEKDAY,
    @SerialName("saturday") SATURDAY,
    @SerialName("sunday") SUNDAY,
}

@Serializable
enum class Season {
    @SerialName("winter") WINTER,
    @SerialName("summer") SUMMER,
}

@Serializable
data class Timetable(
    val line: String,
    val direction: String,
    val service: ServiceDay,
    val season: Season,
    /** Departure times from the first stop, "HH:MM" */
    val departures: List<String>,
)

@Serializable
data class SeasonRange(
    /** "MM-DD", inclusive */
    val from: String,
    val to: String,
)

@Serializable
data class Meta(
    val placeholder: Boolean,
    val source: String,
    val updated: String,
    /** Summer timetable period */
    val summer: SeasonRange,
    /** Fixed public holidays "MM-DD", served with the Sunday timetable */
    val holidays: List<String>,
)

/** Everything the app knows about the network, loaded once from assets */
class TransitData(
    val stops: List<Stop>,
    val lines: List<Line>,
    val timetables: List<Timetable>,
    val meta: Meta,
) {
    private val stopById = stops.associateBy { it.id }
    private val lineById = lines.associateBy { it.id }

    fun stop(id: String?): Stop? = id?.let { stopById[it] }

    fun line(id: String?): Line? = id?.let { lineById[it] }

    /** Lines that stop at a stop, in line order, including the stops of alternative endings */
    fun linesAtStop(stopId: String): List<Line> =
        lines.filter { line -> line.directions.any { d -> d.stops.any { it.stop == stopId } || d.branches.any { stopId in it.stops } } }

    /** Every stop a line can call at, in either direction or on an alternative ending */
    fun lineStopIds(line: Line): Set<String> =
        line.directions.flatMap { d -> d.stops.map { it.stop } + d.branches.flatMap { it.stops } }.toSet()

    /** Stops of a direction in order, for drawing it on the map */
    fun directionStops(line: Line, directionId: String): List<Stop> {
        val direction = line.directions.firstOrNull { it.id == directionId } ?: line.directions.first()
        return direction.stops.mapNotNull { stop(it.stop) }
    }

    /** The path a direction takes on the map as (lat, lng): along the streets when known, else stop to stop */
    fun directionPath(line: Line, directionId: String): List<Pair<Double, Double>> {
        val direction = line.directions.firstOrNull { it.id == directionId } ?: line.directions.first()
        direction.shape?.let { return Geo.decodePolyline(it) }
        return directionStops(line, directionId).map { it.lat to it.lng }
    }

    /** Paths of a direction's alternative endings */
    fun branchPaths(line: Line, directionId: String): List<List<Pair<Double, Double>>> {
        val direction = line.directions.firstOrNull { it.id == directionId } ?: return emptyList()
        return direction.branches.map { branch ->
            branch.shape?.let { Geo.decodePolyline(it) } ?: branch.stops.mapNotNull { stop(it) }.map { it.lat to it.lng }
        }
    }
}

/** One bus leaving a stop */
data class Departure(
    val line: Line,
    val direction: Direction,
    /** Minutes since midnight of the service date */
    val minutes: Int,
    /** Days from today: 0 today, 1 tomorrow */
    val dayOffset: Int,
    /** Last trip of this line and direction on that service day */
    val last: Boolean,
)
