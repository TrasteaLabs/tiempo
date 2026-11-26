package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData.at
import com.trastealabs.tiempo.TestData.zone
import org.junit.Assert.assertEquals
import org.junit.Test

/** La franja sin conexión dice el día si el dato no es de hoy (diseño del 006). */
class OfflineLabelTest {
    private val now = at(2026, 11, 4, 17, 20)   // miércoles

    @Test fun `de hoy solo la hora, como antes`() {
        assertEquals("Sin conexión · datos de las 09:12", offlineLabel(at(2026, 11, 4, 9, 12), zone, now))
        assertEquals("Sin conexión · datos de las 00:00", offlineLabel(at(2026, 11, 4, 0, 0), zone, now))
    }

    @Test fun `de otro dia tambien el dia`() {
        assertEquals("Sin conexión · datos de ayer, 23:59", offlineLabel(at(2026, 11, 3, 23, 59), zone, now))
        assertEquals("Sin conexión · datos del lunes, 09:12", offlineLabel(at(2026, 11, 2, 9, 12), zone, now))
        assertEquals("Sin conexión · datos del 27 de octubre, 09:12", offlineLabel(at(2026, 10, 27, 9, 12), zone, now))
    }
}
