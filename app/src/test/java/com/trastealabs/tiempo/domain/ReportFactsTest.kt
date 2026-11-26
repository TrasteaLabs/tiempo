package com.trastealabs.tiempo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Previsión inventada para los tests del parte (016): horas seguidas, secas, a 15 °C y cubierto; 10 días de 20/10 °C. */
internal fun reportSnap(
    now: Long,
    zone: ZoneId = ZoneId.of("Europe/Madrid"),
    hoursAhead: Int = 11 * 24,
    days: Int = 10,
    place: Place = Place("Villa Ejemplo", "ES", LatLon(40.0, -3.0)),
    warnings: List<Warning> = emptyList(),
    day: (index: Int, DayPoint) -> DayPoint = { _, d -> d },
    hour: (ZonedDateTime, HourPoint) -> HourPoint = { _, h -> h },
): WeatherSnapshot {
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val start = today.minusDays(2).atStartOfDay(zone)
    val end = today.atStartOfDay(zone).plusHours(hoursAhead.toLong())
    val hourly = generateSequence(start.toInstant()) { it.plusSeconds(3600) }
        .takeWhile { it.isBefore(end.toInstant()) }
        .map { i ->
            val t = i.atZone(zone)
            hour(t, HourPoint(i.epochSecond, 15.0, 10, 0.0, 3, true, windKmh = 10.0, windGustsKmh = 20.0))
        }.toList()
    val daily = (0 until days).map { i -> day(i, DayPoint(today.plusDays(i.toLong()).toString(), 20.0, 10.0, 0, 0.0)) }
    return WeatherSnapshot(
        place = place,
        zoneId = zone.id,
        fetchedAt = now,
        current = CurrentConditions(15.0, 14.0, 3, true),
        hourly = hourly,
        daily = daily,
        warnings = warnings,
    )
}

internal fun madridAt(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
    ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of("Europe/Madrid")).toEpochSecond()

class ReportFactsTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = madridAt(2026, 11, 12, 10, 20) // jueves
    private val today = LocalDate.of(2026, 11, 12)

    private fun facts(
        s: WeatherSnapshot = reportSnap(now),
        at: Long = now,
        z: ZoneId = zone,
    ): ReportFacts = reportFacts(s, at, z)!!

    private fun ZonedDateTime.onDay(offset: Long, hours: IntRange) =
        toLocalDate() == today.plusDays(offset) && hour in hours

    private fun wet(mm: Double = 1.0, code: Int = 61) = { t: ZonedDateTime, h: HourPoint -> h.copy(precipMm = mm, weatherCode = code) }

    private fun warning(
        id: String = "w1",
        level: WarningLevel = WarningLevel.ORANGE,
        onset: Long = now - 3600,
        expires: Long = now + 6 * 3600,
        area: String = "Campiña de Prueba",
    ) = Warning(id, level, "Tormentas", area, onset, expires, "40%-70%", "Acumulación de precipitación en una hora: 20 mm", "Precaución", onset)

    // ---------- día a día ----------

    @Test fun `un dia seco no cuenta lluvia, tormenta, racha, helada ni niebla`() {
        val d = facts().today
        assertEquals("2026-11-12", d.date)
        assertEquals("hoy", d.label)
        assertEquals(10, d.minC)
        assertEquals(20, d.maxC)
        assertEquals(0, d.rainMm)
        assertTrue(d.rain.isEmpty())
        assertFalse(d.rainAllDay)
        assertTrue(d.storm.isEmpty())
        assertNull(d.gustKmh)
        assertNull(d.gustAt)
        assertNull(d.frostC)
        assertNull(d.fog)
        assertFalse(d.snow)
    }

    @Test fun `diez dias con su etiqueta y hoy en el primero`() {
        val f = facts()
        assertEquals(10, f.days.size)
        assertEquals(f.today, f.days.first())
        assertEquals(listOf("hoy", "mañana", "sábado 14", "domingo 15"), f.days.take(4).map { it.label })
    }

    @Test fun `lluvia solo por la tarde da un tramo con sus horas y su cantidad`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 16..19)) h.copy(precipMm = 1.0) else h })
        val d = facts(s).today
        assertEquals(listOf(Span("16:00", "20:00", 4)), d.rain)
        assertEquals(4, d.rainMm)
        assertFalse(d.rainAllDay)
    }

    @Test fun `lluvia todo el dia solo si llueve el dia entero`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 0..23)) h.copy(precipMm = 0.5) else h })
        val early = madridAt(2026, 11, 12, 0, 20)
        val whole = facts(s, early).today
        assertTrue(whole.rainAllDay)
        assertEquals(12, whole.rainMm)
        // A las 10:20 ya han pasado 10 h: lo que queda llueve, pero no es «todo el día».
        val d = facts(s).today
        assertFalse(d.rainAllDay)
        assertEquals(listOf(Span("10:00", "00:00", 7)), d.rain) // 14 h desde las 10:00 (la de las 10 sigue en curso)
        assertEquals(7, d.rainMm)
    }

    @Test fun `lluvia a ratos son dos tramos`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 12..13) || t.onDay(0, 17..18)) h.copy(precipMm = 1.0) else h })
        val d = facts(s).today
        assertEquals(listOf(Span("12:00", "14:00", 2), Span("17:00", "19:00", 2)), d.rain)
        assertFalse(d.rainAllDay)
    }

    @Test fun `la lluvia que ya paso hoy no cuenta`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 3..5)) h.copy(precipMm = 2.0) else h })
        val f = facts(s)
        assertTrue(f.today.rain.isEmpty())
        assertEquals(0, f.today.rainMm)
        assertFalse(f.basis.wetToday)
    }

    @Test fun `menos de 0,2 mm no es lluvia y 0,2 si`() {
        val dry = reportSnap(now, hour = { t, h -> if (t.onDay(0, 15..15)) h.copy(precipMm = 0.19) else h })
        assertTrue(facts(dry).today.rain.isEmpty())
        val wet = reportSnap(now, hour = { t, h -> if (t.onDay(0, 15..15)) h.copy(precipMm = 0.2) else h })
        assertEquals(1, facts(wet).today.rain.size)
    }

    @Test fun `nieve se marca aparte`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 14..15)) h.copy(precipMm = 1.0, weatherCode = 71) else h })
        val d = facts(s).today
        assertTrue(d.snow)
        assertEquals(1, d.rain.size)
        assertFalse(facts(reportSnap(now, hour = { t, h -> if (t.onDay(0, 14..15)) h.copy(precipMm = 1.0) else h })).today.snow)
    }

    @Test fun `tormenta con su hora`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 18..18)) h.copy(weatherCode = 95, precipMm = 3.0) else h })
        assertEquals(listOf("18:00"), facts(s).today.storm)
    }

    @Test fun `racha de 49 no cuenta y de 50 si, con la primera hora`() {
        val s49 = reportSnap(now, hour = { t, h -> if (t.onDay(0, 15..15)) h.copy(windGustsKmh = 49.0) else h })
        assertNull(facts(s49).today.gustKmh)
        val s50 = reportSnap(now, hour = { t, h -> if (t.onDay(0, 15..16)) h.copy(windGustsKmh = 50.0) else h })
        val d = facts(s50).today
        assertEquals(50, d.gustKmh)
        assertEquals("15:00", d.gustAt)
    }

    @Test fun `helada de 0 grados cuenta y de 1 no`() {
        val zero = facts(reportSnap(now, day = { i, d -> if (i == 0) d.copy(minC = 0.0) else d })).today
        assertEquals(0, zero.frostC)
        val one = facts(reportSnap(now, day = { i, d -> if (i == 0) d.copy(minC = 1.0) else d })).today
        assertNull(one.frostC)
        val minus = facts(reportSnap(now, day = { i, d -> if (i == 0) d.copy(minC = -2.4) else d })).today
        assertEquals(-2, minus.frostC)
    }

    @Test fun `niebla con su tramo`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 11..12)) h.copy(weatherCode = 45) else h })
        assertEquals(Span("11:00", "13:00"), facts(s).today.fog)
    }

    @Test fun `minima y maxima con su hora y el empate se queda con la primera`() {
        val s = reportSnap(now, hour = { t, h ->
            when {
                t.onDay(0, 5..6) -> h.copy(temperatureC = 8.0)
                t.onDay(0, 16..17) -> h.copy(temperatureC = 22.0)
                else -> h
            }
        })
        val d = facts(s).today
        assertEquals("05:00", d.minAt)
        assertEquals("16:00", d.maxAt)
    }

    @Test fun `el dia 10 sin horas no tiene hora de minima ni de maxima`() {
        val s = reportSnap(now, hoursAhead = 9 * 24) // las horas acaban con el día 9 de 10
        val last = facts(s).days.last()
        assertEquals("2026-11-21", last.date)
        assertNull(last.minAt)
        assertNull(last.maxAt)
        assertEquals(20, last.maxC)
        assertTrue(last.rain.isEmpty())
        assertFalse(last.rainAllDay)
    }

    @Test fun `sin horas desde ahora no hay hechos`() {
        val s = reportSnap(now)
        assertNull(reportFacts(s.copy(hourly = s.hourly.filter { it.time + 3600 <= now }), now, zone))
        assertNull(reportFacts(s.copy(hourly = emptyList()), now, zone))
    }

    @Test fun `con menos de 10 dias se cuentan los que hay`() {
        assertEquals(4, facts(reportSnap(now, days = 4)).days.size)
    }

    @Test fun `los dias anteriores a hoy no entran`() {
        val s = reportSnap(now)
        val old = s.daily.first().copy(date = "2026-11-10")
        assertEquals(10, facts(s.copy(daily = listOf(old) + s.daily)).days.size)
    }

    // ---------- medianoche, cambio de hora, otros husos ----------

    @Test fun `a las 23 59 hoy sigue siendo hoy y a las 00 00 ya es mañana`() {
        val late = facts(at = madridAt(2026, 11, 12, 23, 59), s = reportSnap(madridAt(2026, 11, 12, 23, 59)))
        assertEquals("2026-11-12", late.today.date)
        assertEquals("2026-11-12", late.basis.date)
        val midnight = madridAt(2026, 11, 13, 0, 0)
        val next = facts(at = midnight, s = reportSnap(now))
        assertEquals("2026-11-13", next.today.date)
        assertEquals("hoy", next.today.label)
        assertEquals("mañana", next.days[1].label)
    }

    @Test fun `el dia del cambio de hora tiene 25 horas y se cuenta desde la medianoche real`() {
        val at = madridAt(2026, 10, 25, 1, 30)
        // La 03:00 hora de invierno es la hora 4 desde las 00:00 (hay dos «02:00»).
        val s = reportSnap(at, hour = { t, h -> if (t.hour == 3 && t.toLocalDate() == LocalDate.of(2026, 10, 25)) h.copy(precipMm = 1.0) else h })
        val f = facts(s, at)
        assertEquals("2026-10-25", f.today.date)
        assertEquals(listOf(Span("03:00", "04:00", 1)), f.today.rain)
        assertEquals(4, f.basis.rainStartHour)
    }

    @Test fun `un sitio en otro huso da sus horas locales`() {
        val sydney = ZoneId.of("Australia/Sydney")
        val at = ZonedDateTime.of(2026, 11, 12, 10, 20, 0, 0, sydney).toEpochSecond()
        val s = reportSnap(at, zone = sydney, hour = { t, h -> if (t.hour in 15..16 && t.toLocalDate() == LocalDate.of(2026, 11, 12)) h.copy(precipMm = 1.0) else h })
        val f = reportFacts(s, at, sydney)!!
        assertEquals("2026-11-12", f.today.date)
        assertEquals(listOf(Span("15:00", "17:00", 2)), f.today.rain)
        // El mismo instante en Madrid todavía es el 12 pero otra hora: no se mezcla.
        assertEquals("2026-11-12", reportFacts(reportSnap(at), at, zone)!!.today.date)
    }

    @Test fun `un sitio al oeste en la noche de Madrid sigue en el dia de ayer`() {
        val mexico = ZoneId.of("America/Mexico_City")
        val at = ZonedDateTime.of(2026, 11, 12, 23, 30, 0, 0, mexico).toEpochSecond() // ya es el 13 en Madrid
        val f = reportFacts(reportSnap(at, zone = mexico), at, mexico)!!
        assertEquals("2026-11-12", f.today.date)
    }

    // ---------- cifras de pantalla ----------

    @Test fun `las cifras son las de pantalla`() {
        val s = reportSnap(now, day = { i, d -> if (i == 0) d.copy(maxC = 19.6, minC = 5.5) else d })
        val d = facts(s).today
        assertEquals(shownDegrees(19.6), d.maxC)
        assertEquals(20, d.maxC)
        assertEquals(6, d.minC)
        assertEquals(20, facts(s).basis.maxC)
        assertEquals(6, facts(s).basis.minC)
    }

    // ---------- avisos ----------

    @Test fun `los avisos van con clave a1, a2 en el orden de activeWarnings`() {
        val yellow = warning("amarillo-id", WarningLevel.YELLOW, onset = now - 3600)
        val orange = warning("naranja-id", WarningLevel.ORANGE, onset = now + 3600, expires = now + 4 * 3600)
        val gone = warning("pasado-id", WarningLevel.RED, onset = now - 9 * 3600, expires = now - 3600)
        val far = warning("lejano-id", WarningLevel.RED, onset = now + 30 * 3600, expires = now + 40 * 3600)
        val f = facts(reportSnap(now, warnings = listOf(yellow, gone, orange, far)))
        assertEquals(listOf("a1", "a2"), f.warnings.map { it.key })
        assertEquals(listOf("naranja-id", "amarillo-id"), f.warnings.map { it.id })
        assertEquals("naranja", f.warnings[0].level)
        assertEquals("Tormentas", f.warnings[0].phenomenon)
        assertEquals("hoy 11:20", f.warnings[0].from)
        assertEquals("hoy 14:20", f.warnings[0].to)
        assertEquals("40%-70%", f.warnings[0].probability)
        assertEquals(listOf("amarillo-id", "naranja-id"), f.basis.warningIds) // ordenados
    }

    @Test fun `un aviso que acaba mañana lo dice`() {
        val w = warning(onset = now - 3600, expires = madridAt(2026, 11, 13, 8, 0))
        assertEquals("mañana 08:00", facts(reportSnap(now, warnings = listOf(w))).warnings[0].to)
    }

    // ---------- huella ----------

    @Test fun `la huella es coherente con los hechos`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 16..18)) h.copy(precipMm = 1.0) else h })
        val f = facts(s)
        assertEquals("2026-11-12", f.basis.date)
        assertEquals(f.today.maxC, f.basis.maxC)
        assertEquals(f.today.minC, f.basis.minC)
        assertTrue(f.basis.wetToday)
        assertEquals(16, f.basis.rainStartHour)
        assertTrue(f.warnings.isEmpty() && f.basis.warningIds.isEmpty())
    }

    @Test fun `la lluvia de manana cuenta como inicio de lluvia a partir de la hora 24`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(1, 9..9)) h.copy(precipMm = 1.0) else h })
        val b = facts(s).basis
        assertFalse(b.wetToday)
        assertEquals(33, b.rainStartHour)
    }

    @Test fun `una lluvia en curso conserva su inicio real mientras sigue`() {
        val s = reportSnap(now, hour = { t, h -> if (t.onDay(0, 8..13)) h.copy(precipMm = 1.0) else h })
        val early = facts(s, madridAt(2026, 11, 12, 8, 10)).basis.rainStartHour
        val late = facts(s, madridAt(2026, 11, 12, 12, 10)).basis.rainStartHour
        assertEquals(8, early)
        assertEquals(8, late)
    }

    @Test fun `sin lluvia en 48 horas no hay inicio`() {
        assertNull(facts().basis.rainStartHour)
        assertNotNull(facts().basis)
    }
}
