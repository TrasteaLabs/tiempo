package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** H12: el contador de hoy y del mes, con `now` y zona. */
class ApiUsageTest {
    private val zone = ZoneId.of("Europe/Madrid")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toEpochSecond()

    @Test fun `suma el peso de cada peticion`() {
        val now = at(2026, 11, 3, 10)
        val usage = ApiUsage().plus(22, now, zone).plus(244, now, zone).plus(18, now, zone)
        assertEquals(ApiUsage("2026-11-03", 284, "2026-11", 284), usage)
        assertEquals(28, usage.todayCalls(now, zone))
        assertEquals(28, usage.monthCalls(now, zone))
    }

    @Test fun `redondea a la llamada`() {
        val now = at(2026, 11, 3, 10)
        assertEquals(2, ApiUsage().plus(22, now, zone).todayCalls(now, zone))
        assertEquals(3, ApiUsage().plus(25, now, zone).todayCalls(now, zone))
    }

    @Test fun `a medianoche el de hoy vuelve a 0 y el del mes no`() {
        val late = at(2026, 11, 3, 23, 59)
        val midnight = at(2026, 11, 4, 0, 0)
        val usage = ApiUsage().plus(100, late, zone).plus(20, midnight, zone)
        assertEquals(2, usage.todayCalls(midnight, zone))
        assertEquals(12, usage.monthCalls(midnight, zone))
        assertEquals("2026-11-04", usage.day)
    }

    @Test fun `del 30 de noviembre al 1 de diciembre vuelven los dos`() {
        val nov = at(2026, 11, 30, 22)
        val dec = at(2026, 12, 1, 8)
        val usage = ApiUsage().plus(500, nov, zone).plus(30, dec, zone)
        assertEquals(3, usage.todayCalls(dec, zone))
        assertEquals(3, usage.monthCalls(dec, zone))
        assertEquals("2026-12", usage.month)
    }

    @Test fun `el dia del cambio de hora cuenta un solo dia`() {
        // 2026-10-25: a las 3:00 vuelven a ser las 2:00 (25 horas).
        val early = at(2026, 10, 25, 0, 30)
        val late = at(2026, 10, 25, 23, 30)
        val usage = ApiUsage().plus(10, early, zone).plus(10, late, zone)
        assertEquals(2, usage.todayCalls(late, zone))
        assertEquals("2026-10-25", usage.day)
    }

    @Test fun `hoy sale 0 al pintar pasada la medianoche sin peticiones nuevas`() {
        val usage = ApiUsage().plus(2140, at(2026, 11, 3, 20), zone)
        val nextDay = at(2026, 11, 4, 0, 5)
        assertEquals(0, usage.todayCalls(nextDay, zone))
        assertEquals(214, usage.monthCalls(nextDay, zone))
        assertEquals(0, usage.monthCalls(at(2026, 12, 1, 9), zone))
    }

    @Test fun `segundos hasta la medianoche, tambien el dia del cambio de hora`() {
        assertEquals(60, secondsToNextMidnight(at(2026, 11, 3, 23, 59), zone))
        assertEquals(24 * 3600, secondsToNextMidnight(at(2026, 11, 4, 0, 0), zone))
        // 2026-10-25 tiene 25 horas.
        assertEquals(25 * 3600, secondsToNextMidnight(at(2026, 10, 25, 0, 0), zone))
    }

    @Test fun `miles con punto`() {
        assertEquals("0", groupedThousands(0))
        assertEquals("214", groupedThousands(214))
        assertEquals("1.046", groupedThousands(1046))
        assertEquals("10.000", groupedThousands(10_000))
        assertEquals("300.000", groupedThousands(300_000))
        assertEquals("1.234.567", groupedThousands(1_234_567))
    }

    @Test fun `porcentaje entero desde el 1 y con un decimal por debajo`() {
        assertEquals("2 %", usagePercent(214, 10_000))
        assertEquals("0,3 %", usagePercent(1046, 300_000))
        assertEquals("0 %", usagePercent(0, 10_000))
        assertEquals("< 0,1 %", usagePercent(31, 300_000))
        assertEquals("1 %", usagePercent(97, 10_000))
        assertEquals("100 %", usagePercent(10_000, 10_000))
    }
}
