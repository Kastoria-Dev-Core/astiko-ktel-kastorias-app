package com.kastoriadevs.astikoktel.app.domain

import com.kastoriadevs.astikoktel.app.data.Departure
import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.Season
import com.kastoriadevs.astikoktel.app.data.ServiceDay
import com.kastoriadevs.astikoktel.app.data.TransitData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Timetable logic, pure Kotlin so it can be unit tested without Android */
class Schedule(private val data: TransitData) {

    /** Holidays use the Sunday timetable */
    fun serviceDayFor(date: LocalDate): ServiceDay = when {
        isHoliday(date) -> ServiceDay.SUNDAY
        date.dayOfWeek == DayOfWeek.SUNDAY -> ServiceDay.SUNDAY
        date.dayOfWeek == DayOfWeek.SATURDAY -> ServiceDay.SATURDAY
        else -> ServiceDay.WEEKDAY
    }

    fun seasonFor(date: LocalDate): Season {
        val key = mmdd(date)
        val summer = data.meta.summer
        return if (key >= summer.from && key <= summer.to) Season.SUMMER else Season.WINTER
    }

    fun isHoliday(date: LocalDate): Boolean {
        val key = mmdd(date)
        return key in data.meta.holidays || key in movableHolidays(date.year)
    }

    /** Departures from the first stop, minutes since midnight, sorted. Falls back to the winter timetable. */
    fun departuresFor(lineId: String, directionId: String, service: ServiceDay, season: Season): List<Int> {
        val tt = data.timetables.firstOrNull {
            it.line == lineId && it.direction == directionId && it.service == service && it.season == season
        } ?: data.timetables.firstOrNull {
            it.line == lineId && it.direction == directionId && it.service == service && it.season == Season.WINTER
        }
        return tt?.departures?.map(::toMinutes)?.sorted().orEmpty()
    }

    fun departuresFor(lineId: String, directionId: String, date: LocalDate): List<Int> =
        departuresFor(lineId, directionId, serviceDayFor(date), seasonFor(date))

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

    /** Next departure of a direction from its first stop, today only */
    fun nextTrip(line: Line, direction: Direction, now: LocalDateTime): Int? {
        val nowMin = minutesOfDay(now)
        return departuresFor(line.id, direction.id, now.toLocalDate()).firstOrNull { it >= nowMin }
    }

    /** All departures from a stop on a date. The last stop of a direction is skipped (buses arrive, they don't leave). */
    private fun departuresAtStopOn(stopId: String, date: LocalDate, dayOffset: Int): List<Departure> {
        val service = serviceDayFor(date)
        val season = seasonFor(date)
        val out = mutableListOf<Departure>()
        for (line in data.lines) {
            for (direction in line.directions) {
                val idx = direction.stops.indexOfFirst { it.stop == stopId }
                if (idx == -1 || idx == direction.stops.lastIndex) continue
                val offset = direction.stops[idx].offset
                val deps = departuresFor(line.id, direction.id, service, season)
                deps.forEachIndexed { i, dep ->
                    out += Departure(line, direction, dep + offset, dayOffset, last = i == deps.lastIndex)
                }
            }
        }
        return out
    }

    companion object {
        fun toMinutes(hhmm: String): Int {
            val (h, m) = hhmm.split(":").map { it.trim().toInt() }
            return h * 60 + m
        }

        fun formatMinutes(minutes: Int): String {
            val m = ((minutes % 1440) + 1440) % 1440
            return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
        }

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
