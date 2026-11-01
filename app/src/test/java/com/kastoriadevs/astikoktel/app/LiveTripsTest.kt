package com.kastoriadevs.astikoktel.app

import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.DirectionStop
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.LiveBus
import com.kastoriadevs.astikoktel.app.data.Localized
import com.kastoriadevs.astikoktel.app.data.Meta
import com.kastoriadevs.astikoktel.app.data.Season
import com.kastoriadevs.astikoktel.app.data.SeasonRange
import com.kastoriadevs.astikoktel.app.data.ServiceDay
import com.kastoriadevs.astikoktel.app.data.Stop
import com.kastoriadevs.astikoktel.app.data.Timetable
import com.kastoriadevs.astikoktel.app.data.TransitData
import com.kastoriadevs.astikoktel.app.domain.LiveTrip
import com.kastoriadevs.astikoktel.app.domain.Schedule
import com.kastoriadevs.astikoktel.app.domain.liveTrips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** The times at a stop from where the bus really is: later or earlier, and gone once it's passed */
class LiveTripsTest {

    private val l = Localized("x", "x")
    // Four stops 1 km apart going north, five minutes apart by the timetable
    private fun lat(km: Int) = 40.50 + km * 0.009
    private val stops = listOf("a", "b", "c", "d").mapIndexed { i, id -> Stop(id, l, lat(i), 21.26) }
    private val line = Line(
        id = "01", number = "1", name = l, color = "#000000", textColor = "#FFFFFF",
        directions = listOf(Direction("out", l, stops.mapIndexed { i, s -> DirectionStop(s.id, i * 5) })),
    )
    private val data = TransitData(
        stops = stops,
        lines = listOf(line),
        timetables = listOf(Timetable("01", "out", ServiceDay.WEEKDAY, Season.WINTER, listOf("08:00", "08:30"))),
        meta = Meta(placeholder = false, source = "test", updated = "2026-10-12", summer = SeasonRange("06-20", "09-10"), holidays = emptyList()),
    )
    private val schedule = Schedule(data)

    /** A bus [fraction] of the way from stop [from] to the next, seen at [time] on Monday 12 October (UTC) */
    private fun bus(from: Int, fraction: Double, time: String, lng: Double = 21.26) = LiveBus(
        vehicle = "12", line = "01", direction = "out",
        lat = lat(from) + fraction * 0.009, lng = lng, t = "2026-10-12T${time}Z",
    )

    private fun trips(vararg buses: LiveBus) = liveTrips(data, schedule, buses.toList(), ZoneOffset.UTC)

    @Test
    fun aBusFiveMinutesLateIsTheEightOClockFiveMinutesLate() {
        // Halfway from b to c is 7.5 minutes out; the 08:00 should be there at 08:07:30
        assertEquals(listOf(LiveTrip("01", "out", tripStart = 480, delay = 5, passedIndex = 1)), trips(bus(1, 0.5, "08:12:30")))
    }

    @Test
    fun aBusEarlyIsAheadOfTheTimetable() {
        assertEquals(-2, trips(bus(1, 0.5, "08:05:30")).single().delay)
    }

    @Test
    fun theNextStopsMoveByTheDelayAndThePassedOneGoes() {
        val live = trips(bus(1, 0.5, "08:12:30"))
        val now = LocalDateTime.of(2026, 10, 12, 8, 6)
        // Two of today's, so tomorrow's first ones don't come into it
        val atC = schedule.nextDepartures("c", now, 2, live)
        assertEquals(listOf(8 * 60 + 15 to true, 8 * 60 + 40 to false), atC.map { it.minutes to it.live })
        // b is behind the bus: its 08:05 isn't coming any more, the 08:35 is
        assertEquals(listOf(8 * 60 + 35), schedule.nextDepartures("b", now, 1, live).map { it.minutes })
    }

    @Test
    fun aLateBusStillShowsAfterItsTimetableTime() {
        val live = trips(bus(1, 0.5, "08:12:30"))
        // By the timetable the 08:00 left c at 08:10, but it's five minutes late
        val atC = schedule.nextDepartures("c", LocalDateTime.of(2026, 10, 12, 8, 13), 5, live)
        assertEquals(8 * 60 + 15, atC.first().minutes)
        assertTrue(atC.first().live)
        assertEquals("without the live bus the timetable says it's gone", 8 * 60 + 40, schedule.nextDepartures("c", LocalDateTime.of(2026, 10, 12, 8, 13), 5).first().minutes)
    }

    @Test
    fun noTimesFromABusAtItsFirstStopOffItsRouteOrFittingNoTrip() {
        assertTrue("waiting at the first stop says nothing about when it leaves", trips(bus(0, 0.0, "07:58:00")).isEmpty())
        assertTrue("a kilometre off the route", trips(bus(1, 0.5, "08:12:30", lng = 21.272)).isEmpty())
        assertTrue("an hour late is more likely another bus", trips(bus(1, 0.5, "09:50:00")).isEmpty())
    }

    @Test
    fun withNoLiveBusNothingChanges() {
        val now = LocalDateTime.of(2026, 10, 12, 8, 6)
        val deps = schedule.nextDepartures("c", now, 2)
        assertEquals(listOf(8 * 60 + 10, 8 * 60 + 40), deps.map { it.minutes })
        assertFalse(deps.any { it.live })
    }
}
