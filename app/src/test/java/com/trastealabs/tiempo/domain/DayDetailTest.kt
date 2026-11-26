package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayDetailTest {
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    /** Horas seguidas (instantes UTC) desde las 00:00 local del día anterior a [date] hasta dos días después. */
    private fun hoursAround(date: String): List<HourPoint> {
        val start = LocalDate.parse(date).minusDays(1).atStartOfDay(zone).toEpochSecond()
        return (0 until 72).map { HourPoint(start + it * 3600L, 10.0, 0, 0.0, 0, true) }
    }

    private fun localHours(hours: List<HourPoint>) =
        hours.map { "%02d".format(Instant.ofEpochSecond(it.time).atZone(zone).hour) }

    @Test fun `un dia normal tiene 24 horas de 00 a 23`() {
        val hours = hoursOfDay(hoursAround("2026-11-04"), "2026-11-04", zone)
        assertEquals(24, hours.size)
        assertEquals((0..23).map { "%02d".format(it) }, localHours(hours))
        assertNull(dstShift(hours, zone))
    }

    @Test fun `al atrasar la hora el dia tiene 25 y las 02 salen dos veces`() {
        val hours = hoursOfDay(hoursAround("2026-10-25"), "2026-10-25", zone)
        assertEquals(25, hours.size)
        assertEquals(listOf("00", "01", "02", "02", "03"), localHours(hours).take(5))
        assertEquals(hours.size, hours.map { it.time }.toSet().size)
        assertEquals(DstShift(index = 3, backward = true, before = "02", after = "02"), dstShift(hours, zone))
    }

    @Test fun `al adelantar la hora el dia tiene 23 y de las 01 se pasa a las 03`() {
        val hours = hoursOfDay(hoursAround("2027-03-28"), "2027-03-28", zone)
        assertEquals(23, hours.size)
        assertEquals(listOf("00", "01", "03", "04"), localHours(hours).take(4))
        assertEquals(DstShift(index = 2, backward = false, before = "01", after = "03"), dstShift(hours, zone))
    }

    @Test fun `con horas que faltan salen las que hay`() {
        val all = hoursAround("2026-11-04")
        val holes = all.filterIndexed { i, _ -> i != 24 + 5 && i != 24 + 6 }
        val hours = hoursOfDay(holes, "2026-11-04", zone)
        assertEquals(22, hours.size)
        assertTrue("05" !in localHours(hours))
        assertNull(dstShift(hours, zone))
    }

    @Test fun `la columna Ahora es la ultima que ha empezado y solo dentro del dia`() {
        val hours = hoursOfDay(hoursAround("2026-11-04"), "2026-11-04", zone)
        assertEquals(15, nowIndex(hours, TestData.at(2026, 11, 4, 15, 2)))
        assertEquals(0, nowIndex(hours, TestData.at(2026, 11, 4, 0, 0)))
        assertEquals(23, nowIndex(hours, TestData.at(2026, 11, 4, 23, 59)))
        assertNull(nowIndex(hours, TestData.at(2026, 11, 3, 23, 59)))
        assertNull(nowIndex(hours, TestData.at(2026, 11, 5, 0, 0)))
        assertNull(nowIndex(emptyList(), TestData.at(2026, 11, 4, 15, 0)))
    }

    private fun windDay(speeds: List<Double?>, dirs: List<Int?> = speeds.map { 225 }, gusts: List<Double?> = speeds.map { it?.times(2) }): List<HourPoint> {
        val start = LocalDate.parse("2026-11-04").atStartOfDay(zone).toEpochSecond()
        return speeds.indices.map {
            HourPoint(start + it * 3600L, 10.0, 0, 0.0, 0, true, windKmh = speeds[it], windGustsKmh = gusts[it], windDirectionDeg = dirs[it])
        }
    }

    @Test fun `el viento del dia es el mas fuerte con su direccion, su hora y la racha mas fuerte`() {
        val speeds = (0..23).map { if (it == 17) 22.2 else 8.0 }
        val gusts = (0..23).map { if (it == 18) 41.4 else 15.0 }
        val wind = dayWind(windDay(speeds, gusts = gusts), zone)!!
        assertEquals(false, wind.calm)
        assertEquals("22 km/h", wind.speed)
        assertEquals(45, wind.towardsDeg)            // viene del suroeste, sopla hacia el noreste
        assertEquals("Suroeste · 17:00", wind.detail)
        assertEquals("Rachas de 41", wind.gusts)
    }

    @Test fun `si empatan, la primera hora`() {
        val speeds = (0..23).map { if (it == 9 || it == 14) 20.0 else 5.0 }
        assertEquals("Suroeste · 09:00", dayWind(windDay(speeds), zone)!!.detail)
    }

    @Test fun `calma con 4,4 y no con 4,6`() {
        val calm = dayWind(windDay((0..23).map { 4.4 }), zone)!!
        assertEquals(true, calm.calm)
        assertEquals("Calma", calm.speed)
        assertNull(calm.towardsDeg)
        assertEquals("No pasa de 5 km/h en todo el día", calm.detail)
        assertNull(calm.gusts)

        val breeze = dayWind(windDay((0..23).map { 4.6 }), zone)!!
        assertEquals(false, breeze.calm)
        assertEquals("5 km/h", breeze.speed)
    }

    @Test fun `sin direccion ni rachas, solo la hora`() {
        val speeds = (0..23).map { if (it == 17) 22.0 else 8.0 }
        val wind = dayWind(windDay(speeds, dirs = speeds.map { null }, gusts = speeds.map { null }), zone)!!
        assertNull(wind.towardsDeg)
        assertEquals("17:00", wind.detail)
        assertNull(wind.gusts)
    }

    @Test fun `sin datos de viento no hay tarjeta`() {
        assertNull(dayWind(windDay((0..23).map { null }), zone))
        assertNull(dayWind(emptyList(), zone))
    }

    @Test fun `la humedad del dia va de la minima a la maxima`() {
        val start = LocalDate.parse("2026-11-04").atStartOfDay(zone).toEpochSecond()
        fun h(i: Int, pct: Int?) = HourPoint(start + i * 3600L, 10.0, 0, 0.0, 0, true, humidityPct = pct)
        assertEquals(DayHumidity(55, 93), dayHumidity(listOf(h(0, 93), h(1, 70), h(2, 55), h(3, null))))
        assertEquals("55–93 %", DayHumidity(55, 93).label)
        assertEquals("70 %", DayHumidity(70, 70).label)
        assertNull(dayHumidity(listOf(h(0, null))))
    }

    @Test fun `hoy, manana y dentro de N dias`() {
        assertEquals("Hoy", relativeDayLabel("2026-11-04", "2026-11-04"))
        assertEquals("Mañana", relativeDayLabel("2026-11-05", "2026-11-04"))
        assertEquals("Dentro de 2 días", relativeDayLabel("2026-11-06", "2026-11-04"))
        assertEquals("Dentro de 9 días", relativeDayLabel("2026-11-13", "2026-11-04"))
    }

    // ---------- P3 · Sol y luna del día (H5.6) ----------

    private fun at(d: Int, h: Int, m: Int) = TestData.at(2026, 11, d, h, m)

    private fun sunDay(rise: Long? = at(5, 7, 58), set: Long? = at(5, 18, 21), light: Double? = 37_380.0) =
        DayPoint("2026-11-05", 20.0, 10.0, 0, 0.0, sunrise = rise, sunset = set, daylightSeconds = light)

    @Test fun `el sol del dia con horas, luz y su tramo en la linea`() {
        val sun = daySun(sunDay(), zone)!!
        assertEquals("07:58 – 18:21", sun.range)
        assertEquals("10 h 23 min de luz", sun.daylight)
        assertEquals((7 * 60 + 58) / 1440f, sun.rise, 1e-6f)
        assertEquals((18 * 60 + 21) / 1440f, sun.set, 1e-6f)
    }

    @Test fun `sin salida o puesta del sol, o en noche y dia polar, no hay sol`() {
        assertNull(daySun(sunDay(rise = null), zone))
        assertNull(daySun(sunDay(set = null), zone))
        assertNull(daySun(sunDay(light = 0.0), zone))
        assertNull(daySun(sunDay(light = 86_400.0), zone))
    }

    @Test fun `clockFraction es la hora local del dia en tanto por uno`() {
        assertEquals(0f, clockFraction(at(5, 0, 0), zone), 1e-6f)
        assertEquals(0.5f, clockFraction(at(5, 12, 0), zone), 1e-6f)
        assertEquals((23 * 60 + 59) / 1440f, clockFraction(at(5, 23, 59), zone), 1e-6f)
    }

    private fun moonDay(rise: Long?, set: Long?, phase: Double? = 0.84) =
        DayPoint("2026-11-05", 20.0, 10.0, 0, 0.0, moonrise = rise, moonset = set, moonPhase = phase)

    private fun f(h: Int, m: Int) = (h * 60 + m) / 1440f

    private fun assertBands(expected: List<Pair<Float, Float>>, moon: DayMoon) {
        assertEquals(expected.size, moon.bands.size)
        expected.zip(moon.bands).forEach { (e, b) ->
            assertEquals(e.first, b.start, 1e-6f)
            assertEquals(e.second, b.endInclusive, 1e-6f)
        }
    }

    @Test fun `luna que sale antes de ponerse, un solo tramo`() {
        val moon = dayMoon(moonDay(at(5, 1, 44), at(5, 15, 12)), zone)!!
        assertEquals("Sale 01:44 · se pone 15:12", moon.events)
        assertEquals("Menguante · 23 %", moon.label)
        assertEquals(0.84, moon.phase, 1e-9)
        assertBands(listOf(f(1, 44) to f(15, 12)), moon)
    }

    @Test fun `luna que se pone antes de salir, dos tramos`() {
        val moon = dayMoon(moonDay(at(5, 19, 52), at(5, 10, 29)), zone)!!
        assertEquals("Se pone 10:29 · sale 19:52", moon.events)
        assertBands(listOf(0f to f(10, 29), f(19, 52) to 1f), moon)
    }

    @Test fun `luna que no sale ese dia (caso limite)`() {
        val moon = dayMoon(moonDay(null, at(5, 18, 39)), zone)!!
        assertEquals("No sale este día · se pone 18:39", moon.events)
        assertBands(listOf(0f to f(18, 39)), moon)
    }

    @Test fun `luna que no se pone ese dia (caso limite)`() {
        val moon = dayMoon(moonDay(at(5, 13, 40), null), zone)!!
        assertEquals("Sale 13:40 · no se pone este día", moon.events)
        assertBands(listOf(f(13, 40) to 1f), moon)
    }

    @Test fun `luna que ni sale ni se pone`() {
        val moon = dayMoon(moonDay(null, null), zone)!!
        assertEquals("No sale ni se pone este día", moon.events)
        assertTrue(moon.bands.isEmpty())
    }

    @Test fun `sin fase son datos de antes y no hay luna`() {
        assertNull(dayMoon(moonDay(at(5, 1, 44), at(5, 15, 12), phase = null), zone))
    }
}
