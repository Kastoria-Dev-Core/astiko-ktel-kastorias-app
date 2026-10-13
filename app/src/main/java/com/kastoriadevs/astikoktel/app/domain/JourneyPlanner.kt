package com.kastoriadevs.astikoktel.app.domain

import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.Stop
import com.kastoriadevs.astikoktel.app.data.TransitData
import java.time.LocalDateTime
import kotlin.math.ceil

/** The places offered in "Where do you want to go?", each with the stops that count as "there" */
enum class Destination(val stopIds: Set<String>) {
    /** Αφετηρία and Αφετηρία 2, the terminal in the centre: almost every line starts and ends there */
    CENTRE(setOf("16001", "16053", "16600")),

    /** Every stop of Μανιάκοι (Γήπεδο, Γυμνάσιο, Δ. Μανιακών, Εκκλησάκι, Τέρμα, Υπέρ, both sides) */
    MANIAKOI(setOf("16193", "16127", "16131", "16194", "16195", "16128", "16130", "16129", "16132")),

    /** Πανεπιστήμιο and Πανεπιστήμιο 2, one on each side of the road */
    UNIVERSITY(setOf("16114", "16119")),
}

/** A stop and how far it is from the user */
data class NearbyStop(val stop: Stop, val distanceMeters: Double) {
    /** Minutes to walk there, rounded up. Straight-line distance, so we walk a bit slowly. */
    val walkMinutes: Int get() = ceil(distanceMeters / WALK_METERS_PER_MINUTE).toInt()

    private companion object {
        const val WALK_METERS_PER_MINUTE = 70.0
    }
}

/** A direction that takes you from [board] (near you) to [alightStopId] (at the destination) */
data class Ride(
    val line: Line,
    val direction: Direction,
    val board: NearbyStop,
    /** Minutes after the trip leaves its first stop that it reaches [board] */
    val boardOffset: Int,
    val alightStopId: String,
)

/** The next bus of a [Ride] you can still walk to in time, and the one after it (minutes since midnight) */
data class JourneyOption(val ride: Ride, val minutes: Int, val minutesUntil: Int, val later: Int?)

enum class JourneyStatus { TooFar, AlreadyThere, NoRoute, NoMoreBuses, Ready }

data class JourneyPlan(
    val status: JourneyStatus,
    val nearestStop: NearbyStop,
    /** The closest stop with a bus to the destination, when it's not [nearestStop] */
    val usefulStop: NearbyStop?,
    /** Soonest first */
    val options: List<JourneyOption>,
)

/**
 * Finds the buses that go from near the user to a [Destination], using only the
 * direction stop lists and today's timetables.
 */
object JourneyPlanner {

    /** Stops up to this far are an easy walk: we look here first */
    const val MAX_WALK_METERS = 1_000.0

    /** If no stop within [MAX_WALK_METERS] goes there, we look this far for one that does */
    const val MAX_SEARCH_METERS = 3_000.0

    /** Closer than this to a destination stop: you are already there */
    const val ALREADY_THERE_METERS = 300.0

    /** Rides from stops this much further than the closest useful stop are shown too */
    private const val EXTRA_WALK_METERS = 300.0

    fun plan(
        data: TransitData,
        schedule: Schedule,
        destination: Destination,
        lat: Double,
        lng: Double,
        now: LocalDateTime,
    ): JourneyPlan? {
        val nearby = data.stops
            .map { NearbyStop(it, Geo.distanceMeters(lat, lng, it.lat, it.lng)) }
            .sortedBy { it.distanceMeters }
        val nearest = nearby.firstOrNull() ?: return null
        // Outside Kastoria no stop is a walk away, however close the "closest" one is
        if (nearest.distanceMeters > MAX_SEARCH_METERS) {
            return JourneyPlan(JourneyStatus.TooFar, nearest, null, emptyList())
        }
        if (isAlreadyThere(nearby, destination)) {
            return JourneyPlan(JourneyStatus.AlreadyThere, nearest, null, emptyList())
        }
        // A direction with no timetable never has a bus to show, so it can't take you there
        val timed = data.timetables.filter { it.departures.isNotEmpty() }.map { it.line to it.direction }.toSet()
        val lines = data.lines.map { line -> line.copy(directions = line.directions.filter { (line.id to it.id) in timed }) }
        // First the stops within an easy walk; if none of them goes there, look further
        val rides = nearestRides(findRides(stopsWithin(nearby, MAX_WALK_METERS), lines, destination))
            .ifEmpty { nearestRides(findRides(stopsWithin(nearby, MAX_SEARCH_METERS), lines, destination)) }
        if (rides.isEmpty()) return JourneyPlan(JourneyStatus.NoRoute, nearest, null, emptyList())

        val options = options(rides, schedule, now)
        // The closest stop among the buses we show (a stop whose bus already left today
        // would be confusing), or among all rides when no bus is left today
        val useful = (options.map { it.ride }.ifEmpty { rides })
            .minBy { it.board.distanceMeters }.board.takeIf { it.stop.id != nearest.stop.id }
        return JourneyPlan(
            status = if (options.isEmpty()) JourneyStatus.NoMoreBuses else JourneyStatus.Ready,
            nearestStop = nearest,
            usefulStop = useful,
            options = options,
        )
    }

    /** The stops up to [meters] away, closest first. The closest stop is always included. */
    fun stopsWithin(nearbySorted: List<NearbyStop>, meters: Double): List<NearbyStop> =
        nearbySorted.filterIndexed { index, stop -> index == 0 || stop.distanceMeters <= meters }

    /**
     * Keeps the rides worth walking to: those from stops within [MAX_WALK_METERS] or, when
     * the closest stop that goes there is further away, from that stop and the ones up to
     * [EXTRA_WALK_METERS] further (another line may leave sooner from a stop just beyond it).
     */
    fun nearestRides(rides: List<Ride>): List<Ride> {
        val closest = rides.minOfOrNull { it.board.distanceMeters } ?: return emptyList()
        val limit = maxOf(MAX_WALK_METERS, closest + EXTRA_WALK_METERS)
        return rides.filter { it.board.distanceMeters <= limit }
    }

    fun isAlreadyThere(nearbySorted: List<NearbyStop>, destination: Destination): Boolean =
        nearbySorted.any { it.stop.id in destination.stopIds && it.distanceMeters <= ALREADY_THERE_METERS }

    /**
     * For each direction, the closest boarding stop from which it LATER reaches a destination
     * stop (a bus only drives forward along its stop list, so a stop after the destination is
     * the wrong way). [boardingStops] must be closest first.
     */
    fun findRides(boardingStops: List<NearbyStop>, lines: List<Line>, destination: Destination): List<Ride> =
        lines.flatMap { line ->
            line.directions.mapNotNull { direction ->
                val ids = direction.stops.map { it.stop }
                boardingStops.firstNotNullOfOrNull { board ->
                    if (board.stop.id in destination.stopIds) return@firstNotNullOfOrNull null
                    // A stop can appear twice in a direction (rare): try every pass
                    ids.indices.filter { ids[it] == board.stop.id }.firstNotNullOfOrNull { boardIndex ->
                        (boardIndex + 1 until ids.size).firstOrNull { ids[it] in destination.stopIds }?.let {
                            Ride(line, direction, board, direction.stops[boardIndex].offset, ids[it])
                        }
                    }
                }
            }
        }

    /**
     * The next catchable bus of every ride today, soonest first. A bus that leaves before you
     * could walk to the stop is skipped.
     */
    fun options(rides: List<Ride>, schedule: Schedule, now: LocalDateTime): List<JourneyOption> {
        val nowMin = Schedule.minutesOfDay(now)
        return rides.mapNotNull { ride ->
            val catchable = schedule.departuresFor(ride.line.id, ride.direction.id, now.toLocalDate())
                .map { it + ride.boardOffset }
                .filter { it - nowMin >= ride.board.walkMinutes }
            catchable.firstOrNull()?.let { JourneyOption(ride, it, it - nowMin, catchable.getOrNull(1)) }
        }.sortedWith(compareBy<JourneyOption> { it.minutesUntil }.thenBy { it.ride.board.distanceMeters })
    }
}
