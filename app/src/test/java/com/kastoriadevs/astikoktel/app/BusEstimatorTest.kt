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
import com.kastoriadevs.astikoktel.app.domain.BusEstimator
import com.kastoriadevs.astikoktel.app.domain.Schedule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BusEstimatorTest {

    private val l = Localized("x", "x")

    /** Three stops due east along the equator, 0.01° (about 1.1 km) apart, no street path */
    private fun estimator(offsets: List<Int>): BusEstimator {
        val line = Line(
            id = "1", number = "1", name = l, color = "#000000", textColor = "#FFFFFF",
            directions = listOf(
                Direction(
                    id = "out", headsign = l,
                    stops = listOf("a", "b", "c").zip(offsets) { id, offset -> DirectionStop(id, offset) },
                ),
            ),
        )
        val data = TransitData(
            stops = listOf(Stop("a", l, 0.0, 0.0), Stop("b", l, 0.0, 0.01), Stop("c", l, 0.0, 0.02)),
            lines = listOf(line),
            timetables = listOf(Timetable("1", "out", ServiceDay.WEEKDAY, Season.WINTER, listOf("08:00"))),
            meta = Meta(
                placeholder = false, source = "test", updated = "2026-10-12",
                summer = SeasonRange("06-20", "09-10"), holidays = emptyList(),
            ),
        )
        return BusEstimator(data, Schedule(data))
    }

    // Monday 12 October 2026, a weekday
    private fun at(hour: Int, minute: Int, second: Int = 0) = LocalDateTime.of(2026, 10, 12, hour, minute, second)

    @Test
    fun noBusBeforeDepartureOrAfterTheLastStop() {
        val buses = estimator(listOf(0, 5, 10))
        assertTrue(buses.estimate(at(7, 59)).isEmpty())
        assertTrue(buses.estimate(at(8, 10)).isEmpty())
    }

    @Test
    fun movesAlongTheStretchAtTheTimetablePace() {
        val buses = estimator(listOf(0, 5, 10))
        // Halfway between a and b, heading east, next stop b
        val halfway = buses.estimate(at(8, 2, 30)).single()
        assertEquals(0.005, halfway.lng, 1e-6)
        assertEquals(90.0, halfway.bearing, 0.5)
        assertEquals("b", halfway.nextStopId)
        // A quarter of the way from b to c
        val later = buses.estimate(at(8, 6, 15)).single()
        assertEquals(0.0125, later.lng, 1e-6)
        assertEquals("c", later.nextStopId)
    }

    @Test
    fun stopsSharingAMinuteAreSpreadByDistance() {
        // a and b both at minute 0, c at minute 10: b sits halfway, so it's reached at 5 minutes
        val seconds = BusEstimator.smoothTimes(listOf(0, 0, 10), doubleArrayOf(0.0, 500.0, 1000.0))
        assertArrayEquals(doubleArrayOf(0.0, 300.0, 600.0), seconds, 1e-9)
        // So the bus moves steadily instead of jumping to b
        val buses = estimator(listOf(0, 0, 10))
        assertEquals(0.005, buses.estimate(at(8, 2, 30)).single().lng, 1e-6)
    }
}
