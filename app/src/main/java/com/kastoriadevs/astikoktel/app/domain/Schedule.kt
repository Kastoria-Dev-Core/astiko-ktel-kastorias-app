package com.kastoriadevs.astikoktel.app.domain

import com.kastoriadevs.astikoktel.app.data.Departure
import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.Season
import com.kastoriadevs.astikoktel.app.data.ServiceDay
import com.kastoriadevs.astikoktel.app.data.Timetable
import com.kastoriadevs.astikoktel.app.data.TransitData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Timetable logic, pure Kotlin so it can be unit tested without Android */
class Schedule(private val data: TransitData) {

    /**
     * Stops a bus leaves from on at least one day. Leaves out stops that are only the
     * last stop of a direction (buses arrive there, like Αφετηρία 2) and stops served
     * only by lines that have no timetable.
     */
    val departureStopIds: Set<String> by lazy {
        data.lines.flatMap { line ->
            line.directions
                .filter { d -> data.timetables.any { it.line == line.id && it.direction == d.id && it.departures.isNotEmpty() } }
                .flatMap { d -> d.stops.dropLast(1).map { it.stop } }
        }.toSet()
    }

    /** Holidays use the Sunday timetable */
    fun serviceDayFor(date: LocalDate): ServiceDay = when {
        isHoliday(date) -> ServiceDay.SUNDAY
        date.dayOfWeek == DayOfWeek.SUNDAY -> ServiceDay.SUNDAY
        date.dayOfWeek == DayOfWeek.SATURDAY -> ServiceDay.SATURDAY
        else -> ServiceDay.WEEKDAY
    }

    /** Summer between the [Meta.summer] dates, both included, winter the rest of the year */
    fun seasonFor(date: LocalDate): Season {
        val key = mmdd(date)
        val summer = data.meta.summer
        return if (key >= summer.from && key <= summer.to) Season.SUMMER else Season.WINTER
    }

    /** One of the fixed holidays in meta.json, or one that moves with Orthodox Easter */
    fun isHoliday(date: LocalDate): Boolean {
        val key = mmdd(date)
        return key in data.meta.holidays || key in movableHolidays(date.year)
    }

    /** Whether a line has its own Wednesday timetable, in either direction */
    fun hasWednesday(lineId: String): Boolean =
        data.timetables.any { it.line == lineId && it.service == ServiceDay.WEDNESDAY }

    /**
     * The timetable a line runs on [date]: [serviceDayFor], except on a Wednesday that isn't a holiday,
     * when a line with a Wednesday timetable runs that one
     */
    fun timetableDayFor(lineId: String, date: LocalDate): ServiceDay {
        val day = serviceDayFor(date)
        return if (day == ServiceDay.WEEKDAY && date.dayOfWeek == DayOfWeek.WEDNESDAY && hasWednesday(lineId)) {
            ServiceDay.WEDNESDAY
        } else {
            day
        }
    }

    /**
     * Departures from the first stop, minutes since midnight, sorted. Falls back to the winter timetable,
     * and from the Wednesday timetable to the weekday one.
     */
    fun departuresFor(lineId: String, directionId: String, service: ServiceDay, season: Season): List<Int> =
        timetableFor(lineId, directionId, service, season)?.departures?.map(::toMinutes)?.sorted().orEmpty()

    /** The operator's notes on the same departures as [departuresFor], by minutes since midnight */
    fun notesFor(lineId: String, directionId: String, service: ServiceDay, season: Season): Map<Int, String> =
        timetableFor(lineId, directionId, service, season)?.notes.orEmpty().mapKeys { toMinutes(it.key) }

    /** The timetable [departuresFor] reads, with its fallbacks */
    private fun timetableFor(lineId: String, directionId: String, service: ServiceDay, season: Season): Timetable? {
        val tt = data.timetables.firstOrNull {
            it.line == lineId && it.direction == directionId && it.service == service && it.season == season
        } ?: data.timetables.firstOrNull {
            it.line == lineId && it.direction == directionId && it.service == service && it.season == Season.WINTER
        }
        if (tt == null && service == ServiceDay.WEDNESDAY) return timetableFor(lineId, directionId, ServiceDay.WEEKDAY, season)
        return tt
    }

    /** Departures from the first stop on [date], from the timetable of that day and season */
    fun departuresFor(lineId: String, directionId: String, date: LocalDate): List<Int> =
        departuresFor(lineId, directionId, timetableDayFor(lineId, date), seasonFor(date))

    /** Upcoming departures from a stop: the rest of today, then tomorrow's first ones */
    fun nextDepartures(stopId: String, now: LocalDateTime, limit: Int): List<Departure> {
        val nowMin = minutesOfDay(now)
        val today = departuresAtStopOn(stopId, now.toLocalDate(), dayOffset = 0)
            .filter { it.minutes >= nowMin }
            .sortedBy { it.minutes }
        if (today.size >= limit) return today.take(limit)
        val tomorrow = departuresAtStopOn(stopId, now.toLocalDate().plusDays(1), dayOffset = 1)
            .sortedBy { it.minutes }
        return (today + tomorrow).take(limit)
    }

    /** The bus that left a stop most recently today, if any left in the last [withinMinutes] */
    fun previousDeparture(stopId: String, now: LocalDateTime, withinMinutes: Int = 30): Departure? {
        val nowMin = minutesOfDay(now)
        return departuresAtStopOn(stopId, now.toLocalDate(), dayOffset = 0)
            .filter { it.minutes < nowMin && it.minutes >= nowMin - withinMinutes }
            .maxByOrNull { it.minutes }
    }

    /**
     * When the next bus of a direction reaches each of its stops today, minutes since midnight,
     * in stop order. null for a stop no more buses reach today.
     */
    fun nextArrivals(line: Line, direction: Direction, now: LocalDateTime): List<Int?> {
        val nowMin = minutesOfDay(now)
        val deps = departuresFor(line.id, direction.id, now.toLocalDate())
        return direction.stops.map { s -> deps.map { it + s.offset }.firstOrNull { it >= nowMin } }
    }

    /**
     * Where the buses on the road are along a direction right now, from the timetable, counted in
     * stops from the first: 2.5 is halfway between the third and the fourth stop.
     */
    fun positionsOnRoute(line: Line, direction: Direction, now: LocalDateTime): List<Double> {
        val offsets = direction.stops.map { it.offset }
        if (offsets.size < 2) return emptyList()
        val nowMin = now.toLocalTime().toSecondOfDay() / 60.0
        val total = offsets.last()
        return departuresFor(line.id, direction.id, now.toLocalDate()).mapNotNull { dep ->
            val elapsed = nowMin - dep
            if (elapsed < 0 || elapsed >= total) return@mapNotNull null
            // The last stretch that has started; stops in the same minute are passed together
            val i = offsets.indices.lastOrNull { it < offsets.lastIndex && offsets[it] <= elapsed && offsets[it + 1] > offsets[it] }
                ?: return@mapNotNull null
            i + (elapsed - offsets[i]) / (offsets[i + 1] - offsets[i])
        }
    }

    /** Next departure of a direction from its first stop, today only */
    fun nextTrip(line: Line, direction: Direction, now: LocalDateTime): Int? {
        val nowMin = minutesOfDay(now)
        return departuresFor(line.id, direction.id, now.toLocalDate()).firstOrNull { it >= nowMin }
    }

    /** All departures from a stop on a date. The last stop of a direction is skipped (buses arrive, they don't leave). */
    private fun departuresAtStopOn(stopId: String, date: LocalDate, dayOffset: Int): List<Departure> {
        val season = seasonFor(date)
        val out = mutableListOf<Departure>()
        for (line in data.lines) {
            for (direction in line.directions) {
                val idx = direction.stops.indexOfFirst { it.stop == stopId }
                if (idx == -1 || idx == direction.stops.lastIndex) continue
                val offset = direction.stops[idx].offset
                val day = timetableDayFor(line.id, date)
                val deps = departuresFor(line.id, direction.id, day, season)
                val notes = notesFor(line.id, direction.id, day, season)
                deps.forEachIndexed { i, dep ->
                    out += Departure(line, direction, dep + offset, dayOffset, last = i == deps.lastIndex, note = notes[dep])
                }
            }
        }
        return out
    }

    companion object {
        /** "HH:MM" to minutes since midnight */
        fun toMinutes(hhmm: String): Int {
            val (h, m) = hhmm.split(":").map { it.trim().toInt() }
            return h * 60 + m
        }

        /** Minutes since midnight to "HH:MM"; past midnight wraps round, so 1450 is "00:10" */
        fun formatMinutes(minutes: Int): String {
            val m = ((minutes % 1440) + 1440) % 1440
            return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
        }

        /** Minutes since midnight, seconds dropped */
        fun minutesOfDay(t: LocalDateTime): Int = t.hour * 60 + t.minute

        /** Minutes from now until a departure */
        fun minutesUntil(dep: Departure, now: LocalDateTime): Int =
            dep.minutes + dep.dayOffset * 1440 - minutesOfDay(now)

        /** Orthodox Easter Sunday (Gregorian date), valid 1900-2099 */
        fun orthodoxEaster(year: Int): LocalDate {
            val a = year % 4
            val b = year % 7
            val c = year % 19
            val d = (19 * c + 15) % 30
            val e = (2 * a + 4 * b - d + 34) % 7
            val month = (d + e + 114) / 31
            val day = ((d + e + 114) % 31) + 1
            return LocalDate.of(year, month, day).plusDays(13)
        }

        /** Clean Monday, Good Friday, Easter Monday, Holy Spirit Monday */
        fun movableHolidays(year: Int): List<String> {
            val easter = orthodoxEaster(year)
            return listOf(-48L, -2L, 1L, 50L).map { mmdd(easter.plusDays(it)) }
        }

        private fun mmdd(d: LocalDate): String = String.format(Locale.US, "%02d-%02d", d.monthValue, d.dayOfMonth)
    }
}
