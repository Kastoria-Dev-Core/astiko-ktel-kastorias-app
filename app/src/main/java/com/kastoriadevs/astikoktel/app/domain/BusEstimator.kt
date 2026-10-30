package com.kastoriadevs.astikoktel.app.domain

import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.TransitData
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/** A bus we think is on the road right now. The buses have no GPS yet, so this comes from the timetable. */
data class EstimatedBus(
    /** The line it's running */
    val line: Line,
    /** Which way it's going */
    val direction: Direction,
    /** Departure from the first stop, minutes since midnight */
    val departure: Int,
    /** Where it should be now, along the street path */
    val lat: Double,
    /** Where it should be now, along the street path */
    val lng: Double,
    /** Direction of travel, degrees clockwise from north */
    val bearing: Double,
    /** The stop the bus reaches next */
    val nextStopId: String,
)

/**
 * Works out where each bus should be from the timetable: a trip is on the road between leaving its
 * first stop and reaching its last, and between two stops it moves along the line's street path
 * at the pace the stop times give.
 */
class BusEstimator(private val data: TransitData, private val schedule: Schedule) {

    /** One direction laid out along its street path, in metres on a flat plane around Kastoria */
    private class Track(
        /** The line this is a direction of */
        val line: Line,
        /** The direction laid out */
        val direction: Direction,
        /** The street path as (lat, lng) */
        val points: List<Pair<Double, Double>>,
        /** The same points scaled to metres (x east, y north), so distances are plain Pythagoras */
        val xy: List<Pair<Double, Double>>,
        /** Distance along the path to each point */
        val cumulative: DoubleArray,
        /** The direction's stop ids in order */
        val stopIds: List<String>,
        /** Distance along the path to each stop */
        val stopDistance: DoubleArray,
        /** Seconds after the trip leaves its first stop that it reaches each stop */
        val stopTime: DoubleArray,
    )

    private val tracks: List<Track> by lazy {
        data.lines.flatMap { line -> line.directions.mapNotNull { track(line, it) } }
    }

    /** The buses on the road at [now] */
    fun estimate(now: LocalDateTime): List<EstimatedBus> {
        val nowSeconds = now.toLocalTime().toSecondOfDay().toDouble()
        val date = now.toLocalDate()
        return tracks.flatMap { track ->
            val tripSeconds = track.stopTime.last()
            schedule.departuresFor(track.line.id, track.direction.id, date).mapNotNull { departure ->
                val elapsed = nowSeconds - departure * 60.0
                if (elapsed < 0 || elapsed >= tripSeconds) null else position(track, departure, elapsed)
            }
        }
    }

    private fun position(track: Track, departure: Int, elapsed: Double): EstimatedBus? {
        val times = track.stopTime
        // The stretch between two stops the bus is on. Stretches of no time (same minute) are skipped.
        val i = (0 until times.size - 1).firstOrNull { elapsed >= times[it] && elapsed < times[it + 1] } ?: return null
        val fraction = (elapsed - times[i]) / (times[i + 1] - times[i])
        val distance = track.stopDistance[i] + fraction * (track.stopDistance[i + 1] - track.stopDistance[i])
        val (lat, lng, bearing) = pointAt(track, distance)
        return EstimatedBus(track.line, track.direction, departure, lat, lng, bearing, track.stopIds[i + 1])
    }

    /** The point [distance] metres along the track, and the bearing of the path there */
    private fun pointAt(track: Track, distance: Double): Triple<Double, Double, Double> {
        val cumulative = track.cumulative
        var segment = cumulative.indexOfLast { it <= distance }.coerceIn(0, cumulative.size - 2)
        // Step past zero-length segments so the bearing has a direction
        while (segment < cumulative.size - 2 && cumulative[segment + 1] == cumulative[segment]) segment++
        val length = cumulative[segment + 1] - cumulative[segment]
        val t = if (length == 0.0) 0.0 else ((distance - cumulative[segment]) / length).coerceIn(0.0, 1.0)
        val (aLat, aLng) = track.points[segment]
        val (bLat, bLng) = track.points[segment + 1]
        val (ax, ay) = track.xy[segment]
        val (bx, by) = track.xy[segment + 1]
        val bearing = (Math.toDegrees(atan2(bx - ax, by - ay)) + 360) % 360
        return Triple(aLat + t * (bLat - aLat), aLng + t * (bLng - aLng), bearing)
    }

    private fun track(line: Line, direction: Direction): Track? {
        val stops = direction.stops.mapNotNull { s -> data.stop(s.stop)?.let { it to s.offset } }
        if (stops.size < 2) return null
        val points = data.directionPath(line, direction.id).ifEmpty { stops.map { it.first.lat to it.first.lng } }
        if (points.size < 2) return null

        val lat0 = points.first().first
        val kx = 111_320.0 * cos(Math.toRadians(lat0))
        val ky = 110_540.0
        fun xy(lat: Double, lng: Double) = lng * kx to lat * ky
        val xy = points.map { (lat, lng) -> xy(lat, lng) }
        val cumulative = DoubleArray(points.size)
        for (k in 1 until xy.size) {
            cumulative[k] = cumulative[k - 1] + hypot(xy[k].first - xy[k - 1].first, xy[k].second - xy[k - 1].second)
        }

        // Each stop's place along the path. Always searching forward from the previous stop,
        // so a path that passes the same street twice puts each stop on the right pass.
        val stopDistance = DoubleArray(stops.size)
        var segment = 0
        stops.forEachIndexed { index, (stop, _) ->
            val (sx, sy) = xy(stop.lat, stop.lng)
            val reach = cumulative[segment] + SEARCH_AHEAD_METERS
            var best = Double.MAX_VALUE
            var bestSegment = segment
            var bestAlong = cumulative[segment]
            var j = segment
            while (j < xy.size - 1 && cumulative[j] <= reach) {
                val (d, along) = project(sx, sy, xy[j], xy[j + 1])
                if (d < best) {
                    best = d
                    bestSegment = j
                    bestAlong = cumulative[j] + along
                }
                j++
            }
            segment = bestSegment
            stopDistance[index] = if (index == 0) bestAlong else maxOf(bestAlong, stopDistance[index - 1])
        }

        return Track(line, direction, points, xy, cumulative, stops.map { it.first.id }, stopDistance, smoothTimes(stops.map { it.second }, stopDistance))
    }

    companion object {
        /** How far along the path to look for the next stop */
        private const val SEARCH_AHEAD_METERS = 3_000.0

        /** Distance from point (px, py) to segment ab, and how far along ab the closest point is, in metres */
        private fun project(px: Double, py: Double, a: Pair<Double, Double>, b: Pair<Double, Double>): Pair<Double, Double> {
            val dx = b.first - a.first
            val dy = b.second - a.second
            val length2 = dx * dx + dy * dy
            val t = if (length2 == 0.0) 0.0 else (((px - a.first) * dx + (py - a.second) * dy) / length2).coerceIn(0.0, 1.0)
            return hypot(px - a.first - t * dx, py - a.second - t * dy) to t * kotlin.math.sqrt(length2)
        }

        /**
         * Stop times in seconds. The timetable gives whole minutes, so several stops in a row can share
         * a minute and the bus would jump between them. Those in-between stops get times spread by
         * distance between the last stop before them and the next stop with a later minute.
         */
        fun smoothTimes(offsetMinutes: List<Int>, stopDistance: DoubleArray): DoubleArray {
            val seconds = DoubleArray(offsetMinutes.size) { offsetMinutes[it] * 60.0 }
            var i = 0
            while (i < offsetMinutes.size) {
                var next = i + 1
                while (next < offsetMinutes.size && offsetMinutes[next] == offsetMinutes[i]) next++
                if (next < offsetMinutes.size && next > i + 1) {
                    val span = stopDistance[next] - stopDistance[i]
                    for (m in i + 1 until next) {
                        val share = if (span > 0) (stopDistance[m] - stopDistance[i]) / span else (m - i).toDouble() / (next - i)
                        seconds[m] = seconds[i] + share * (seconds[next] - seconds[i])
                    }
                }
                i = next
            }
            return seconds
        }
    }
}
