package com.kastoriadevs.astikoktel.app

import com.kastoriadevs.astikoktel.app.data.Announcement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AnnouncementTest {

    private fun announcement(published: String, validUntil: String? = null) = Announcement(
        id = 1,
        title = "ΑΝΑΚΟΙΝΩΣΗ",
        published = published,
        url = "https://astikoktelkastorias.gr/",
        validUntil = validUntil,
    )

    @Test
    fun datedAnnouncementIsActiveUntilItsLastDateInclusive() {
        val a = announcement("2026-07-27T06:00:00Z", validUntil = "2026-07-28")
        val now = Instant.parse("2026-07-28T20:00:00Z")
        assertTrue(a.isActive(LocalDate.parse("2026-07-28"), now))
        assertFalse(a.isActive(LocalDate.parse("2026-07-29"), now))
    }

    @Test
    fun undatedAnnouncementIsActiveForAWeekAfterPublishing() {
        val a = announcement("2026-02-18T10:00:00Z")
        assertTrue(a.isActive(LocalDate.parse("2026-02-24"), Instant.parse("2026-02-24T10:00:00Z")))
        assertFalse(a.isActive(LocalDate.parse("2026-02-26"), Instant.parse("2026-02-26T10:00:00Z")))
    }
}
