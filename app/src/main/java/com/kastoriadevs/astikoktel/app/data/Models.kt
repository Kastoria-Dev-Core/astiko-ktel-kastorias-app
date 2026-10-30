package com.kastoriadevs.astikoktel.app.data

import com.kastoriadevs.astikoktel.app.domain.Geo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A text in Greek and in English, e.g. a stop or line name */
@Serializable
data class Localized(
    /** Greek */
    val el: String,
    /** English, may be blank when there's no translation yet */
    val en: String,
) {
    /** The text in [language] ("el" or "en"), falling back to Greek when the English is blank */
    fun get(language: String): String = if (language == "el") el else en.ifBlank { el }
}

/** A bus stop, from stops.json */
@Serializable
data class Stop(
    /** The operator's stop code, e.g. "206"; shown as "Stop 206" */
    val id: String,
    /** Its name, e.g. "Αφετηρία"; shown with [qualifier] where two share it */
    val name: Localized,
    /** Latitude, WGS84 */
    val lat: Double,
    /** Longitude, WGS84 */
    val lng: Double,
    /**
     * Tells apart stops that share a name at the same place, e.g. the two "Αφετηρία" codes: one where
     * the buses leave from, one where they end their trip. Shown after the name wherever a stop is picked.
     */
    val qualifier: Localized? = null,
)

/** A stop along a direction, with how long the bus takes to get there */
@Serializable
data class DirectionStop(
    /** [Stop.id] */
    val stop: String,
    /** Minutes after the trip leaves the first stop */
    val offset: Int,
)

/** One way a line runs, e.g. out to Πανηγύρι or back to the Αφετηρία */
@Serializable
data class Direction(
    /** "out" or "in", unique within its line */
    val id: String,
    /** Where it's going, shown as "To …" */
    val headsign: Localized,
    /** Its stops in order, the first being where the timetable's times are from */
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
    /** [Stop.id]s from the one where it turns off to its last */
    val stops: List<String>,
    /** Street path as an encoded polyline, like [Direction.shape] */
    val shape: String? = null,
)

/** A bus line with its directions, from lines.json */
@Serializable
data class Line(
    /** Two digits, e.g. "01"; what timetables and routes refer to */
    val id: String,
    /** As the operator writes it, e.g. "1"; badges pad it to two digits */
    val number: String,
    /** Where it serves, e.g. "Μεσοποταμία" */
    val name: Localized,
    /** Hex colour, e.g. "#C2410C" */
    val color: String,
    /** Hex colour of the number on the badge, readable on [color] */
    val textColor: String,
    /** Out and back: every line has two */
    val directions: List<Direction>,
    /** A line that mainly serves the schools (13-17, 54); drawn as an outlined badge */
    val school: Boolean = false,
)

/** Which timetable a day runs on. Holidays run on the Sunday one. */
@Serializable
enum class ServiceDay {
    @SerialName("weekday") WEEKDAY,
    @SerialName("saturday") SATURDAY,
    @SerialName("sunday") SUNDAY,
}

/** Winter or summer timetable; the summer period is [Meta.summer] */
@Serializable
enum class Season {
    @SerialName("winter") WINTER,
    @SerialName("summer") SUMMER,
}

/** The departures of one direction of a line on one kind of day, from timetables.json */
@Serializable
data class Timetable(
    /** [Line.id] */
    val line: String,
    /** [Direction.id] */
    val direction: String,
    /** The kind of day it's for */
    val service: ServiceDay,
    /** The season it's for */
    val season: Season,
    /** Departure times from the first stop, "HH:MM" */
    val departures: List<String>,
)

/** A period of every year, e.g. the summer timetable from 20 June to 10 September */
@Serializable
data class SeasonRange(
    /** "MM-DD", inclusive */
    val from: String,
    /** "MM-DD", inclusive */
    val to: String,
)

/** About the data itself, from meta.json */
@Serializable
data class Meta(
    /** The timetables are made up, not the operator's; a banner says so on every screen */
    val placeholder: Boolean,
    /** Where the timetables come from */
    val source: String,
    /** When the data was last updated, "YYYY-MM-DD" */
    val updated: String,
    /** Summer timetable period */
    val summer: SeasonRange,
    /** Fixed public holidays "MM-DD", served with the Sunday timetable */
    val holidays: List<String>,
)

/** Everything the app knows about the network, loaded once from assets */
class TransitData(
    /** stops.json */
    val stops: List<Stop>,
    /** lines.json */
    val lines: List<Line>,
    /** timetables.json */
    val timetables: List<Timetable>,
    /** meta.json */
    val meta: Meta,
) {
    private val stopById = stops.associateBy { it.id }
    private val lineById = lines.associateBy { it.id }

    /** The stop with this [Stop.id], or null when there's none or [id] is null */
    fun stop(id: String?): Stop? = id?.let { stopById[it] }

    /** The line with this [Line.id], or null when there's none or [id] is null */
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
    /** The line of the bus */
    val line: Line,
    /** Which way it's going */
    val direction: Direction,
    /** Minutes since midnight of the service date */
    val minutes: Int,
    /** Days from today: 0 today, 1 tomorrow */
    val dayOffset: Int,
    /** Last trip of this line and direction on that service day */
    val last: Boolean,
)
