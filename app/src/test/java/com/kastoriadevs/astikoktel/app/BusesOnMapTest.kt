package com.kastoriadevs.astikoktel.app

import com.kastoriadevs.astikoktel.app.data.Direction
import com.kastoriadevs.astikoktel.app.data.DirectionStop
import com.kastoriadevs.astikoktel.app.data.Line
import com.kastoriadevs.astikoktel.app.data.LiveBus
import com.kastoriadevs.astikoktel.app.data.Localized
import com.kastoriadevs.astikoktel.app.data.Meta
import com.kastoriadevs.astikoktel.app.data.SeasonRange
import com.kastoriadevs.astikoktel.app.data.TransitData
import com.kastoriadevs.astikoktel.app.domain.EstimatedBus
import com.kastoriadevs.astikoktel.app.domain.busesOnMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The map shows real buses where they're sending, the timetable's estimates elsewhere, never both */
class BusesOnMapTest {

    private val l = Localized("x", "x")
    private fun line(id: String) = Line(
        id = id, number = id, name = l, color = "#000000", textColor = "#FFFFFF",
        directions = listOf(
            Direction("out", l, listOf(DirectionStop("a", 0), DirectionStop("b", 5))),
            Direction("in", l, listOf(DirectionStop("b", 0), DirectionStop("a", 5))),
        ),
    )
    private val data = TransitData(
        stops = emptyList(),
        lines = listOf(line("01"), line("04")),
        timetables = emptyList(),
        meta = Meta(placeholder = false, source = "test", updated = "2026-10-31", summer = SeasonRange("06-20", "09-10"), holidays = emptyList()),
    )

    private fun estimated(lineId: String, lat: Double) = data.line(lineId)!!.let {
        EstimatedBus(it, it.directions[0], departure = 480, lat = lat, lng = 21.0, bearing = 0.0, nextStopId = "b")
    }

    private fun live(lineId: String, direction: String = "out", lat: Double = 40.9) =
        LiveBus(vehicle = "12", line = lineId, direction = direction, lat = lat, lng = 21.2, t = "2026-10-31T08:15:27Z")

    @Test
    fun aLineSendingShowsOnlyItsRealBuses() {
        val shown = busesOnMap(data, listOf(estimated("01", 40.1), estimated("04", 40.4), estimated("04", 40.5)), listOf(live("04")))
        assertEquals(listOf("04" to true, "01" to false), shown.map { it.line.id to it.live })
        assertEquals(40.9, shown.first { it.live }.lat, 0.0)
    }

    @Test
    fun theRealBusKeepsItsDirection() {
        val shown = busesOnMap(data, emptyList(), listOf(live("01", direction = "in")))
        assertEquals("in", shown.single().direction.id)
    }

    @Test
    fun nothingSendingMeansTheTimetableAsBefore() {
        val shown = busesOnMap(data, listOf(estimated("01", 40.1)), emptyList())
        assertEquals(1, shown.size)
        assertTrue(shown.none { it.live })
    }

    @Test
    fun anUnknownLineOrDirectionIsLeftOut() {
        val shown = busesOnMap(data, listOf(estimated("01", 40.1)), listOf(live("99"), live("01", direction = "loop")))
        assertEquals(listOf("01" to false), shown.map { it.line.id to it.live })
    }
}
