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
import com.kastoriadevs.astikoktel.app.domain.Destination
import com.kastoriadevs.astikoktel.app.domain.JourneyPlanner
import com.kastoriadevs.astikoktel.app.domain.JourneyStatus
import com.kastoriadevs.astikoktel.app.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** "Where do you want to go?": which stop, which bus, and when there's none, on a small made-up network */
class JourneyPlannerTest {

    private val l = Localized("x", "x")
    private val university = "16114"

    // "home" is where the user stands; "corner" is ~450 m east; the university ~2.2 km east
    private val home = Stop("home", l, 40.5, 21.20)
    private val corner = Stop("corner", l, 40.5, 21.2053)
    private val uni = Stop(university, l, 40.5, 21.226)

    private fun line(id: String, vararg stops: String) = Line(
        id = id, number = id, name = l, color = "#000000", textColor = "#FFFFFF",
        directions = listOf(
            Direction(id = "out", headsign = l, stops = stops.mapIndexed { i, s -> DirectionStop(s, i * 5) }),
        ),
    )

    private fun data(vararg lines: Line) = TransitData(
        stops = listOf(home, corner, uni),
        lines = lines.toList(),
        timetables = lines.map { Timetable(it.id, "out", ServiceDay.WEEKDAY, Season.WINTER, listOf("08:00", "09:00")) },
        meta = Meta(false, "test", "2026-10-12", SeasonRange("06-20", "09-10"), emptyList()),
    )

    // A Monday
    private val now = LocalDateTime.of(2026, 10, 12, 7, 50)

    private fun plan(d: TransitData, at: Stop = home, time: LocalDateTime = now) =
        JourneyPlanner.plan(d, Schedule(d), Destination.UNIVERSITY, at.lat, at.lng, time)!!

    @Test
    fun boardsAtTheNearestStopAndShowsTheNextBus() {
        val p = plan(data(line("1", "home", "corner", university)))
        assertEquals(JourneyStatus.Ready, p.status)
        assertNull(p.usefulStop)
        val option = p.options.single()
        assertEquals("home", option.ride.board.stop.id)
        assertEquals(8 * 60, option.minutes)
        assertEquals(10, option.minutesUntil)
        assertEquals(9 * 60, option.later)
    }

    @Test
    fun aStopAfterTheDestinationIsTheWrongWay() {
        val p = plan(data(line("1", university, "home")))
        assertEquals(JourneyStatus.NoRoute, p.status)
    }

    @Test
    fun pointsToAFurtherStopWhenTheNearestHasNoBusThere() {
        val p = plan(data(line("1", "corner", university), line("2", university, "home")))
        assertEquals("corner", p.usefulStop?.stop?.id)
        assertEquals("home", p.nearestStop.stop.id)
        // The bus leaves "corner" at 08:00; 7 minutes' walk, so it can still be caught
        assertEquals(8 * 60, p.options.single().minutes)
    }

    @Test
    fun skipsBusesYouCantWalkToInTime() {
        val p = plan(data(line("1", "corner", university)), time = LocalDateTime.of(2026, 10, 12, 7, 55))
        // 7 minutes' walk to "corner", the 08:00 leaves in 5: the 09:00 is the next one
        assertEquals(9 * 60, p.options.single().minutes)
    }

    @Test
    fun noMoreBusesLateInTheDay() {
        val p = plan(data(line("1", "home", university)), time = LocalDateTime.of(2026, 10, 12, 22, 0))
        assertEquals(JourneyStatus.NoMoreBuses, p.status)
    }

    @Test
    fun tooFarFromKastoria() {
        val p = plan(data(line("1", "home", university)), at = Stop("far", l, 37.4, -122.1))
        assertEquals(JourneyStatus.TooFar, p.status)
    }

    @Test
    fun ignoresLinesWithoutATimetable() {
        // Line 1 passes "home" but has no timetable; line 2 from "corner" does run
        val timed = line("2", "corner", university)
        val d = data(timed).let { TransitData(it.stops, listOf(line("1", "home", university), timed), it.timetables, it.meta) }
        val p = plan(d)
        assertEquals(JourneyStatus.Ready, p.status)
        assertEquals("2", p.options.single().ride.line.id)
        assertEquals("corner", p.usefulStop?.stop?.id)
    }

    @Test
    fun alreadyThereNextToADestinationStop() {
        val p = plan(data(line("1", "home", university)), at = uni)
        assertEquals(JourneyStatus.AlreadyThere, p.status)
    }
}
