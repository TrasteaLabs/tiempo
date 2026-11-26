package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** 018 P2 (H14, H15, H16): cuándo toca la notificación de la mañana y qué dice. Todo con `now` y la zona. */
class MorningTextTest {
    private val zone = TestData.zone
    private val minute = 450 // 7:30

    // --- nextMorning ---

    @Test fun `nextMorning antes de la hora es hoy y despues de la hora es manana`() {
        assertEquals(TestData.at(2026, 11, 16, 7, 30), nextMorning(TestData.at(2026, 11, 16, 6, 0), zone, minute))
        assertEquals(TestData.at(2026, 11, 17, 7, 30), nextMorning(TestData.at(2026, 11, 16, 8, 0), zone, minute))
    }

    @Test fun `nextMorning es estrictamente posterior, a la hora en punto ya es manana`() {
        val at = TestData.at(2026, 11, 16, 7, 30)
        assertEquals(TestData.at(2026, 11, 17, 7, 30), nextMorning(at, zone, minute))
        assertEquals(at, nextMorning(at - 1, zone, minute))
    }

    @Test fun `nextMorning respeta el cambio de hora de primavera y el de otono`() {
        // 29-mar-2026: a las 02:00 pasan a ser las 03:00 (el día dura 23 h).
        val saturday = TestData.at(2026, 3, 28, 8, 0)
        val spring = nextMorning(saturday, zone, minute)
        assertEquals(TestData.at(2026, 3, 29, 7, 30), spring)
        assertEquals(22 * 3600L + 1800, spring - saturday) // 22,5 h de reloj de pared: 23,5 h menos la hora que se salta

        // 25-oct-2026: a las 03:00 vuelve a ser las 02:00 (el día dura 25 h).
        val autumnSat = TestData.at(2026, 10, 24, 8, 0)
        val autumn = nextMorning(autumnSat, zone, minute)
        assertEquals(TestData.at(2026, 10, 25, 7, 30), autumn)
        assertEquals(24 * 3600L + 1800, autumn - autumnSat)
    }

    @Test fun `nextMorning con una hora que no existe ese dia cae en la primera que existe despues`() {
        // 02:30 no existe el 29-mar-2026 en Madrid: sale a las 03:30.
        val next = nextMorning(TestData.at(2026, 3, 29, 0, 30), zone, 150)
        val local = Instant.ofEpochSecond(next).atZone(zone)
        assertEquals(3, local.hour)
        assertEquals(30, local.minute)
        assertEquals(29, local.dayOfMonth)
    }

    @Test fun `nextMorning con otro huso usa la hora local de ese huso`() {
        val ny = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-11-16T05:00:00Z").epochSecond // 00:00 en Nueva York
        val next = Instant.ofEpochSecond(nextMorning(now, ny, minute)).atZone(ny)
        assertEquals(16, next.dayOfMonth)
        assertEquals(7, next.hour)
        assertEquals(30, next.minute)
    }

    // --- morningDue ---

    private val sevenThirty = TestData.at(2026, 11, 16, 7, 30)

    @Test fun `morningDue apagada nunca`() {
        assertFalse(morningDue(sevenThirty + 60, zone, false, minute, ""))
    }

    @Test fun `morningDue antes de la hora no`() {
        assertFalse(morningDue(sevenThirty - 1, zone, true, minute, ""))
    }

    @Test fun `morningDue desde la hora en punto y hasta dos horas despues`() {
        assertTrue(morningDue(sevenThirty, zone, true, minute, ""))
        assertTrue(morningDue(sevenThirty + MORNING_GRACE_S, zone, true, minute, ""))
    }

    @Test fun `morningDue pasadas las dos horas no`() {
        assertFalse(morningDue(sevenThirty + MORNING_GRACE_S + 1, zone, true, minute, ""))
    }

    @Test fun `morningDue ya avisado hoy no, aunque sea ayer si`() {
        assertFalse(morningDue(sevenThirty + 60, zone, true, minute, "2026-11-16"))
        assertTrue(morningDue(sevenThirty + 60, zone, true, minute, "2026-11-15"))
    }

    @Test fun `morningDue usa el dia local de la zona dada`() {
        // Las 08:00 de Madrid son las 02:00 en Nueva York: allí todavía no es la hora.
        val ny = ZoneId.of("America/New_York")
        val madridMorning = Instant.parse("2026-11-16T07:00:00Z").epochSecond // 08:00 en Madrid, 02:00 en Nueva York
        assertTrue(morningDue(madridMorning, zone, true, minute, ""))
        assertFalse(morningDue(madridMorning, ny, true, minute, ""))
    }

    @Test fun `morningDue en el dia del cambio de hora de primavera`() {
        val at = TestData.at(2026, 3, 29, 7, 30)
        assertTrue(morningDue(at, zone, true, minute, "2026-03-28"))
        assertFalse(morningDue(at - 1, zone, true, minute, "2026-03-28"))
    }

    // --- morningText ---

    private val strings = MorningStrings(
        title = { min, max -> "Hoy $min° a $max°" },
        warning = { level, phenomenon -> "Aviso $level por $phenomenon" },
        dataAt = { time -> "Datos de las $time" },
    )

    private val now = TestData.at(2026, 11, 16, 7, 31)
    private val snapshot = TestData.snapshot(now) // hoy: mínima 9°, máxima 18°

    private fun report(today: String?) = AiReport(
        generatedAt = now - 60,
        provider = AiProvider.CLAUDE,
        basis = ReportBasis("2026-11-16", 18, 9, false),
        today = today,
    )

    private fun warning(level: WarningLevel, phenomenon: String, onset: Long, expires: Long, id: String = "w") = Warning(
        id, level, phenomenon, "Zona inventada", onset, expires, null, "Texto inventado", null, onset - 3600,
    )

    @Test fun `con parte de la IA el texto es el parte y withAi es true`() {
        val text = morningText(snapshot, report("Mañana fresca; sol y 18° hacia las 16:00."), listOf("Regla"), now, zone, strings)!!
        assertEquals("Hoy 9° a 18°", text.title)
        assertEquals("Mañana fresca; sol y 18° hacia las 16:00.", text.body)
        assertTrue(text.withAi)
    }

    @Test fun `sin parte el texto son las frases de reglas con su punto y withAi es false`() {
        val rules = listOf("Nubes por la mañana y sol desde las 13:00", "Sin lluvia")
        val text = morningText(snapshot, null, rules, now, zone, strings)!!
        assertEquals("Nubes por la mañana y sol desde las 13:00. Sin lluvia.", text.body)
        assertFalse(text.withAi)
    }

    @Test fun `un parte sin la frase de hoy cae a las reglas`() {
        val text = morningText(snapshot, report(null), listOf("Sin lluvia"), now, zone, strings)!!
        assertEquals("Sin lluvia.", text.body)
        assertFalse(text.withAi)
    }

    @Test fun `con un aviso en vigor hoy el titulo lleva su nivel y su fenomeno`() {
        val w = warning(WarningLevel.YELLOW, "viento", now - 3600, now + 6 * 3600)
        val text = morningText(snapshot.copy(warnings = listOf(w)), null, listOf("Sin lluvia"), now, zone, strings)!!
        assertEquals("Hoy 9° a 18° · Aviso amarillo por viento", text.title)
    }

    @Test fun `con varios avisos sale el mas grave`() {
        val yellow = warning(WarningLevel.YELLOW, "viento", now - 3600, now + 3600, "a")
        val orange = warning(WarningLevel.ORANGE, "lluvia", now + 3600, now + 7200, "b")
        val text = morningText(snapshot.copy(warnings = listOf(yellow, orange)), null, listOf("x"), now, zone, strings)!!
        assertEquals("Hoy 9° a 18° · Aviso naranja por lluvia", text.title)
    }

    @Test fun `un aviso de manana, uno caducado o ninguno no salen en el titulo`() {
        val tomorrow = warning(WarningLevel.RED, "nieve", TestData.at(2026, 11, 17, 3, 0), TestData.at(2026, 11, 17, 12, 0), "a")
        val expired = warning(WarningLevel.RED, "calor", now - 7200, now - 60, "b")
        val text = morningText(snapshot.copy(warnings = listOf(tomorrow, expired)), null, listOf("x"), now, zone, strings)!!
        assertEquals("Hoy 9° a 18°", text.title)
    }

    @Test fun `con el dato de mas de 3 h una linea mas con su hora, y con 3 h justas no`() {
        val old = snapshot.copy(fetchedAt = TestData.at(2026, 11, 16, 3, 10))
        val text = morningText(old, null, listOf("Sin lluvia"), now, zone, strings)!!
        assertEquals("Sin lluvia.\nDatos de las 03:10", text.body)

        val limit = snapshot.copy(fetchedAt = now - MORNING_STALE_S)
        assertEquals("Sin lluvia.", morningText(limit, null, listOf("Sin lluvia"), now, zone, strings)!!.body)
    }

    @Test fun `null si lo guardado no cubre hoy`() {
        val yesterdayOnly = snapshot.copy(daily = snapshot.daily.filter { it.date != "2026-11-16" })
        assertNull(morningText(yesterdayOnly, report("Hola"), listOf("x"), now, zone, strings))
    }

    @Test fun `null si no hay parte ni reglas`() {
        assertNull(morningText(snapshot, null, emptyList(), now, zone, strings))
        assertNull(morningText(snapshot, report(" "), listOf(" "), now, zone, strings))
    }

    @Test fun `hoy sale de now y de la zona del dato, tambien pasada la medianoche`() {
        // 00:20 del 17: «hoy» ya es el 17 y la previsión (que empieza el 16) lo cubre; a las 23:50 del 16 era el 16.
        val late = TestData.at(2026, 11, 16, 23, 50)
        val after = TestData.at(2026, 11, 17, 0, 20)
        assertNotNull(morningText(snapshot, null, listOf("x"), late, zone, strings))
        val onlyToday = snapshot.copy(daily = snapshot.daily.filter { it.date == "2026-11-16" })
        assertNull(morningText(onlyToday, null, listOf("x"), after, zone, strings))
    }
}
