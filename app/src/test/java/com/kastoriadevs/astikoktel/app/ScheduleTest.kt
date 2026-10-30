package com.kastoriadevs.astikoktel.app

import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.DirectionStop
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.Localized
import com.kastoriadevs.astikoktel.app.data.Meta
import com.kastoriadevs.astikoktel.app.data.Season
import com.kastoriadevs.astikoktel.app.data.SeasonRange
import com.kastoriadevs.astikoktel.app.data.ServiceDay
import com.kastoriadevs.astikoktel.app.data.Stop
import com.kastoriadevs.astikoktel.app.data.Timetable
import com.kastoriadevs.astikoktel.app.data.TransitData
import com.kastoriadevs.astikoktel.app.domain.Geo
import com.kastoriadevs.astikoktel.app.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Timetable logic: days and seasons, holidays, departures at a stop, and the helpers in Geo */
class ScheduleTest {

    private val l = Localized("x", "x")
    private val line = Line(
        id = "1", number = "1", name = l, color = "#000000", textColor = "#FFFFFF",
        directions = listOf(
            Direction(
                id = "out", headsign = l,
                stops = listOf(DirectionStop("a", 0), DirectionStop("b", 5), DirectionStop("c", 10)),
            ),
        ),
    )
    private val data = TransitData(
        stops = listOf(Stop("a", l, 0.0, 0.0), Stop("b", l, 0.0, 0.0), Stop("c", l, 0.0, 0.0)),
        lines = listOf(line),
        timetables = listOf(
            Timetable("1", "out", ServiceDay.WEEKDAY, Season.WINTER, listOf("08:00", "07:00", "22:00")),
            Timetable("1", "out", ServiceDay.SUNDAY, Season.WINTER, listOf("10:00")),
        ),
        meta = Meta(
            placeholder = true, source = "test", updated = "2026-10-12",
            summer = SeasonRange("06-20", "09-10"),
            holidays = listOf("10-28", "11-11"),
        ),
    )
    private val schedule = Schedule(data)

    @Test
    fun orthodoxEaster_knownDates() {
        assertEquals(LocalDate.of(2025, 4, 20), Schedule.orthodoxEaster(2025))
        assertEquals(LocalDate.of(2026, 4, 12), Schedule.orthodoxEaster(2026))
        assertEquals(LocalDate.of(2027, 5, 2), Schedule.orthodoxEaster(2027))
    }

    @Test
    fun serviceDay_weekdaysWeekendsAndHolidays() {
        assertEquals(ServiceDay.WEEKDAY, schedule.serviceDayFor(LocalDate.of(2026, 10, 12))) // Monday
        assertEquals(ServiceDay.SATURDAY, schedule.serviceDayFor(LocalDate.of(2026, 10, 17)))
        assertEquals(ServiceDay.SUNDAY, schedule.serviceDayFor(LocalDate.of(2026, 10, 18)))
        assertEquals(ServiceDay.SUNDAY, schedule.serviceDayFor(LocalDate.of(2026, 10, 28))) // fixed holiday
        assertEquals(ServiceDay.SUNDAY, schedule.serviceDayFor(LocalDate.of(2026, 2, 23))) // Clean Monday 2026
        assertEquals(ServiceDay.SUNDAY, schedule.serviceDayFor(LocalDate.of(2026, 4, 13))) // Easter Monday 2026
    }

    @Test
    fun season_boundariesAreInclusive() {
        assertEquals(Season.WINTER, schedule.seasonFor(LocalDate.of(2026, 6, 19)))
        assertEquals(Season.SUMMER, schedule.seasonFor(LocalDate.of(2026, 6, 20)))
        assertEquals(Season.SUMMER, schedule.seasonFor(LocalDate.of(2026, 9, 10)))
        assertEquals(Season.WINTER, schedule.seasonFor(LocalDate.of(2026, 9, 11)))
    }

    @Test
    fun departuresFor_sortsAndFallsBackToWinter() {
        assertEquals(listOf(420, 480, 1320), schedule.departuresFor("1", "out", ServiceDay.WEEKDAY, Season.SUMMER))
        assertTrue(schedule.departuresFor("1", "out", ServiceDay.SATURDAY, Season.WINTER).isEmpty())
    }

    @Test
    fun nextDepartures_addsStopOffsetAndSkipsTerminus() {
        val now = LocalDateTime.of(2026, 10, 12, 7, 30)
        val deps = schedule.nextDepartures("b", now, 2)
        assertEquals(listOf(485, 1325), deps.map { it.minutes })
        assertTrue(deps.last().last)
        assertTrue(schedule.nextDepartures("c", now, 5).isEmpty())
    }

    @Test
    fun nextDepartures_rollsOverToTomorrow() {
        val now = LocalDateTime.of(2026, 10, 12, 23, 0)
        val deps = schedule.nextDepartures("a", now, 2)
        assertEquals(listOf(1, 1), deps.map { it.dayOffset })
        assertEquals(listOf(420, 480), deps.map { it.minutes })
        assertEquals(8 * 60, Schedule.minutesUntil(deps.first(), now))
    }

    @Test
    fun formatMinutes_wrapsAroundMidnight() {
        assertEquals("07:05", Schedule.formatMinutes(425))
        assertEquals("00:15", Schedule.formatMinutes(1455))
    }

    @Test
    fun formatDistance_greekAndEnglish() {
        assertEquals("110 μ.", Geo.formatDistance(112.0, "el"))
        assertEquals("1,5 χλμ.", Geo.formatDistance(1500.0, "el"))
        assertEquals("1.5 km", Geo.formatDistance(1500.0, "en"))
    }

    @Test
    fun departureStops_leaveOutTheLastStop() {
        // "c" is the last stop of the only direction: buses arrive there but never leave
        assertEquals(setOf("a", "b"), schedule.departureStopIds)
    }

    @Test
    fun decodesGooglesPolylineExample() {
        // The worked example from Google's encoded polyline format documentation
        val points = Geo.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].first, 1e-9)
        assertEquals(-120.2, points[0].second, 1e-9)
        assertEquals(40.7, points[1].first, 1e-9)
        assertEquals(-120.95, points[1].second, 1e-9)
        assertEquals(43.252, points[2].first, 1e-9)
        assertEquals(-126.453, points[2].second, 1e-9)
    }

    @Test
    fun previousDeparture_onlyTheOneThatJustLeft() {
        val monday = LocalDate.of(2026, 10, 12)
        // From stop b the 07:00 trip leaves at 07:05 and the 08:00 one at 08:05
        assertEquals(425, schedule.previousDeparture("b", monday.atTime(7, 20))?.minutes)
        assertEquals(null, schedule.previousDeparture("b", monday.atTime(7, 50)))
        assertEquals(null, schedule.previousDeparture("b", monday.atTime(7, 5)))
        assertEquals(485, schedule.previousDeparture("b", monday.atTime(8, 30))?.minutes)
    }

    @Test
    fun nextArrivals_perStopOfTheNextBusToReachIt() {
        val monday = LocalDate.of(2026, 10, 12)
        val direction = line.directions.first()
        // 07:07: the 07:00 bus is between b (07:05) and c (07:10); the 08:00 one is next at a and b
        assertEquals(listOf(480, 485, 430), schedule.nextArrivals(line, direction, monday.atTime(7, 7)))
        assertEquals(listOf(null, null, null), schedule.nextArrivals(line, direction, monday.atTime(23, 0)))
    }

    @Test
    fun positionsOnRoute_betweenStopsByTime() {
        val monday = LocalDate.of(2026, 10, 12)
        val direction = line.directions.first()
        assertEquals(listOf(0.5), schedule.positionsOnRoute(line, direction, monday.atTime(7, 2, 30)))
        assertEquals(listOf(1.4), schedule.positionsOnRoute(line, direction, monday.atTime(7, 7)).map { Math.round(it * 10) / 10.0 })
        assertTrue(schedule.positionsOnRoute(line, direction, monday.atTime(7, 10)).isEmpty())
        assertTrue(schedule.positionsOnRoute(line, direction, monday.atTime(6, 59)).isEmpty())
    }
}
