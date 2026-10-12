package com.michadasis.astikaktel.data

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

    /** Lines that stop at a stop, in line order */
    fun linesAtStop(stopId: String): List<Line> =
        lines.filter { line -> line.directions.any { d -> d.stops.any { it.stop == stopId } } }

    /** Stops of a direction in order, for drawing it on the map */
    fun directionStops(line: Line, directionId: String): List<Stop> {
        val direction = line.directions.firstOrNull { it.id == directionId } ?: line.directions.first()
        return direction.stops.mapNotNull { stop(it.stop) }
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
