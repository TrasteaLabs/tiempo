package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DateTimeLabelTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test fun `fecha y hora del dato en espanol`() {
        val t = ZonedDateTime.of(2026, 11, 3, 15, 2, 0, 0, madrid).toEpochSecond()
        assertEquals("Martes, 3 de noviembre · 15:02", dateTimeLabel(t, madrid))
    }

    @Test fun `la hora es la local de la zona`() {
        val t = ZonedDateTime.of(2025, 6, 1, 0, 5, 0, 0, madrid).toEpochSecond()
        assertEquals("Domingo, 1 de junio · 00:05", dateTimeLabel(t, madrid))
        assertEquals("Sábado, 31 de mayo · 22:05", dateTimeLabel(t, ZoneId.of("UTC")))
    }
}
