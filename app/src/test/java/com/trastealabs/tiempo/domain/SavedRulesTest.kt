package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.TestData.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** H11 (más de 30 min), la edad del dato de las postales y «hora de allí» (006). */
class SavedRulesTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = at(2026, 11, 4, 17, 20)   // miércoles

    private fun stored(age: Long) = StoredState(TestData.snapshot(now, fetchedAt = now - age), false)

    @Test fun `savedRefreshDue con 30 min justos no pide y con uno mas si`() {
        assertTrue(savedRefreshDue(StoredState(null, false), now))
        assertFalse(savedRefreshDue(stored(10 * 60), now))
        assertFalse(savedRefreshDue(stored(30 * 60), now))
        assertTrue(savedRefreshDue(stored(30 * 60 + 1), now))
        assertTrue(savedRefreshDue(stored(3600), now))
        assertTrue(savedRefreshDue(stored(-60), now))
    }

    @Test fun `edad del dato hoy ayer dias de la semana y fecha`() {
        assertNull(dataAgeLabel(now - 30 * 60, now, madrid))
        assertEquals("Datos de las 16:49", dataAgeLabel(now - 31 * 60, now, madrid))
        assertEquals("Datos de ayer", dataAgeLabel(at(2026, 11, 3, 23, 50), now, madrid))
        assertEquals("Datos del lunes", dataAgeLabel(at(2026, 11, 2, 9, 12), now, madrid))
        assertEquals("Datos del jueves", dataAgeLabel(at(2026, 10, 29, 9, 12), now, madrid))   // hace 6 días
        assertEquals("Datos del 28 de octubre", dataAgeLabel(at(2026, 10, 28, 9, 12), now, madrid))   // hace 7
    }

    @Test fun `el dia del dato es el del sitio, no el del movil`() {
        val ba = ZoneId.of("America/Argentina/Buenos_Aires")
        // 22:30 del 3 en Buenos Aires = 02:30 del 4 en Madrid.
        val fetched = ZonedDateTime.of(2026, 11, 3, 22, 30, 0, 0, ba).toEpochSecond()
        val beforeMidnight = ZonedDateTime.of(2026, 11, 3, 23, 59, 0, 0, ba).toEpochSecond()
        val afterMidnight = ZonedDateTime.of(2026, 11, 4, 0, 1, 0, 0, ba).toEpochSecond()
        assertEquals("Datos de las 22:30", dataAgeLabel(fetched, beforeMidnight, ba))
        assertEquals("Datos de ayer", dataAgeLabel(fetched, afterMidnight, ba))
        // Con la zona de Madrid sería «de hoy»: por eso va la del sitio.
        assertEquals("Datos de las 02:30", dataAgeLabel(fetched, afterMidnight, madrid))
    }

    @Test fun `otro desfase que el movil`() {
        assertFalse(otherOffset(now, madrid, madrid))
        assertTrue(otherOffset(now, ZoneId.of("Atlantic/Canary"), madrid))
        assertTrue(otherOffset(now, ZoneId.of("Europe/Lisbon"), madrid))
        assertFalse(otherOffset(now, ZoneId.of("Europe/Paris"), madrid))
    }

    @Test fun `otro desfase el dia del cambio de hora`() {
        // 25-oct-2026, 03:00 de Madrid vuelve a las 02:00; Londres cambia a la vez (siempre 1 h menos).
        val london = ZoneId.of("Europe/London")
        val change = ZonedDateTime.of(2026, 10, 25, 1, 0, 0, 0, ZoneId.of("UTC")).toEpochSecond()
        assertTrue(otherOffset(change - 60, london, madrid))
        assertTrue(otherOffset(change + 60, london, madrid))
        // Nueva York cambia una semana después: ese día desfase de 5 h y no de 6, pero distinto en los dos casos.
        assertTrue(otherOffset(change + 60, ZoneId.of("America/New_York"), madrid))
        // Santiago de Chile y Buenos Aires: iguales en noviembre (UTC−3), distintos en julio (Santiago, UTC−4).
        val santiago = ZoneId.of("America/Santiago")
        val ba = ZoneId.of("America/Argentina/Buenos_Aires")
        assertFalse(otherOffset(now, santiago, ba))
        assertTrue(otherOffset(at(2026, 7, 1, 12, 0), santiago, ba))
    }
}
