package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 009, H12: las frases del Resumen salen de reglas fijas, con `now` y la zona del dato. */
class SummaryTest {
    private val zone = TestData.zone
    private val now = TestData.at(2025, 6, 10, 10, 20) // martes

    /**
     * Previsión inventada: horas seguidas (instantes UTC) desde dos días antes de [now] hasta 11 días después, secas, a
     * 15 °C, cubierto y rachas de 20; [hour] cambia las que quiera. Días desde hoy, secos, 20/10 °C; [day] los cambia.
     */
    private fun snap(
        now: Long = this.now,
        fetchedAt: Long = now,
        day: (index: Int, DayPoint) -> DayPoint = { _, d -> d },
        hour: (ZonedDateTime, HourPoint) -> HourPoint = { _, h -> h },
    ): WeatherSnapshot {
        val start = Instant.ofEpochSecond(now).atZone(zone).toLocalDate().minusDays(2).atStartOfDay(zone)
        val hourly = (0 until 13 * 24).map { i ->
            val t = start.plusHours(i.toLong())
            hour(t, HourPoint(t.toEpochSecond(), 15.0, 10, 0.0, 3, true, windKmh = 10.0, windGustsKmh = 20.0))
        }
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        val daily = (0 until 10).map { i -> day(i, DayPoint(today.plusDays(i.toLong()).toString(), 20.0, 10.0, 0, 0.0)) }
        return TestData.snapshot(now, fetchedAt = fetchedAt).copy(hourly = hourly, daily = daily)
    }

    private fun lines(s: WeatherSnapshot, at: Long = now) = summaryLines(s, at, zone)
    private fun texts(s: WeatherSnapshot, at: Long = now) = lines(s, at).map { it.text }

    private fun ZonedDateTime.isAt(date: LocalDate, hours: IntRange) = toLocalDate() == date && hour in hours
    private val today = LocalDate.of(2025, 6, 10)
    private val tomorrow = today.plusDays(1)

    // ---------- sin nada que contar ----------

    @Test fun `sin ninguna regla sale sin lluvia en los proximos 10 dias`() {
        assertEquals(listOf(SummaryLine(SummaryIcon.DRY, "Sin lluvia en los próximos 10 días")), lines(snap()))
    }

    @Test fun `sin ninguna hora desde ahora no hay frases (datos muy viejos)`() {
        val s = snap()
        assertEquals(emptyList<SummaryLine>(), lines(s, s.hourly.last().time + 7200))
    }

    // ---------- 1 · lluvia en 24 h ----------

    @Test fun `empieza a llover hoy con la cantidad del episodio`() {
        val s = snap { t, h -> if (t.isAt(today, 16..18)) h.copy(precipMm = 2.0, weatherCode = 61) else h }
        assertEquals(SummaryLine(SummaryIcon.RAIN, "Empieza a llover hoy a las 16:00 · 6 mm"), lines(s).first())
    }

    @Test fun `empieza esta noche y menos de 1 mm no lleva cifra`() {
        val s = snap { t, h -> if (t.isAt(today, 22..22)) h.copy(precipMm = 0.5, weatherCode = 51) else h }
        assertEquals("Empieza a llover esta noche a las 22:00", texts(s).first())
    }

    @Test fun `por debajo de 0,2 mm no cuenta como lluvia`() {
        val s = snap { t, h -> if (t.isAt(today, 16..18)) h.copy(precipMm = 0.1) else h }
        assertEquals(listOf("Sin lluvia en los próximos 10 días"), texts(s))
    }

    @Test fun `empieza manana`() {
        val s = snap { t, h -> if (t.isAt(tomorrow, 8..9)) h.copy(precipMm = 0.6) else h }
        assertEquals("Empieza a llover mañana a las 08:00 · 1 mm", texts(s).first())
    }

    @Test fun `llueve ahora y para hoy`() {
        val s = snap { t, h -> if (t.isAt(today, 9..12)) h.copy(precipMm = 1.0, weatherCode = 61) else h }
        // La cantidad, desde la hora en curso: 10, 11 y 12.
        assertEquals(SummaryLine(SummaryIcon.RAIN_STOP, "Para de llover a las 13:00 · 3 mm"), lines(s).first())
    }

    @Test fun `llueve ahora y para tras la medianoche`() {
        val night = TestData.at(2025, 6, 10, 22, 30)
        val s = snap(night) { t, h ->
            if (t.isAt(today, 20..23) || t.isAt(tomorrow, 0..0)) h.copy(precipMm = 0.4, weatherCode = 61) else h
        }
        // Desde la hora en curso: 22, 23 y 00, 1,2 mm.
        assertEquals("Lluvia hasta mañana a las 01:00 · 1 mm", texts(s, night).first())
    }

    @Test fun `si para a las 00 00 dice a medianoche`() {
        val night = TestData.at(2025, 6, 10, 21, 10)
        val s = snap(night) { t, h -> if (t.isAt(today, 20..23)) h.copy(precipMm = 0.5, weatherCode = 61) else h }
        assertEquals("Para de llover a medianoche · 2 mm", texts(s, night).first())
    }

    @Test fun `lluvia todo el dia si no para en 24 h`() {
        val s = snap { _, h -> h.copy(precipMm = 1.0, weatherCode = 63) }
        assertEquals(SummaryLine(SummaryIcon.RAIN, "Lluvia todo el día · 24 mm"), lines(s).first())
    }

    @Test fun `con nieve dice nevar`() {
        val s = snap { t, h -> if (t.isAt(today, 18..19)) h.copy(precipMm = 0.8, weatherCode = 73) else h }
        assertEquals("Empieza a nevar hoy a las 18:00 · 2 mm", texts(s).first())
    }

    @Test fun `datos viejos - las horas pasadas no cuentan`() {
        // Llovió anoche y esta mañana a las 08:00; el dato es de ayer y ahora son las 10:20: nada que contar.
        val s = snap(fetchedAt = TestData.at(2025, 6, 9, 20)) { t, h ->
            if (t.isAt(today.minusDays(1), 21..23) || t.isAt(today, 8..8)) h.copy(precipMm = 3.0) else h
        }
        assertEquals(listOf("Sin lluvia en los próximos 10 días"), texts(s))
    }

    @Test fun `la cantidad es la de las 24 h aunque siga lloviendo despues`() {
        // Llueve desde mañana a las 08:00 hasta pasado mañana: en la ventana (hasta las 09:59 de mañana), 08 y 09.
        val s = snap { t, h -> if (t.isAt(tomorrow, 8..23) || t.isAt(tomorrow.plusDays(1), 0..12)) h.copy(precipMm = 1.0) else h }
        assertEquals("Empieza a llover mañana a las 08:00 · 2 mm", texts(s).first())
    }

    @Test fun `con la prevision acabando pronto no dice todo el dia ni 10 dias`() {
        val end = TestData.at(2025, 6, 10, 20)
        val wet = snap { _, h -> h.copy(precipMm = 1.0) }.let { it.copy(hourly = it.hourly.filter { h -> h.time < end }) }
        assertEquals("Lluvia hasta el final de la previsión · 10 mm", texts(wet).first())
        // Datos viejos: quedan tres días de previsión desde hoy.
        val dry = snap().let { it.copy(daily = it.daily.take(3)) }
        assertEquals(listOf("Sin lluvia en los próximos 3 días"), texts(dry))
    }

    // ---------- 2–5 ----------

    @Test fun `rachas desde 50 km h con la hora de la mayor`() {
        val s = snap { t, h ->
            when {
                t.isAt(today, 15..15) -> h.copy(windGustsKmh = 62.0)
                t.isAt(today, 16..16) -> h.copy(windGustsKmh = 55.0)
                else -> h
            }
        }
        assertEquals(SummaryLine(SummaryIcon.GUST, "Rachas de 62 km/h hoy hacia las 15:00"), lines(s).first())
        val calm = snap { t, h -> if (t.isAt(today, 15..15)) h.copy(windGustsKmh = 49.0) else h }
        assertEquals(listOf("Sin lluvia en los próximos 10 días"), texts(calm))
    }

    @Test fun `helada justo en 0 grados`() {
        val s = snap { t, h -> if (t.isAt(tomorrow, 6..6)) h.copy(temperatureC = 0.0) else h }
        assertEquals(SummaryLine(SummaryIcon.FROST, "Helada esta noche: 0°"), lines(s).first())
        val above = snap { t, h -> if (t.isAt(tomorrow, 6..6)) h.copy(temperatureC = 0.6) else h }
        assertEquals(listOf("Sin lluvia en los próximos 10 días"), texts(above))
        val below = snap { t, h -> if (t.isAt(tomorrow, 5..6)) h.copy(temperatureC = -2.3) else h }
        assertEquals("Helada esta noche: -2°", texts(below).first())
    }

    @Test fun `el frio de manana a mediodia no es helada de esta noche`() {
        val s = snap { t, h -> if (t.isAt(tomorrow, 10..10)) h.copy(temperatureC = -1.0) else h }
        assertEquals(listOf("Sin lluvia en los próximos 10 días"), texts(s))
    }

    @Test fun `niebla en las proximas 24 h`() {
        val s = snap { t, h -> if (t.isAt(tomorrow, 7..8)) h.copy(weatherCode = 45) else h }
        assertEquals(SummaryLine(SummaryIcon.FOG, "Niebla mañana hacia las 07:00"), lines(s).first())
        val now = snap { t, h -> if (t.isAt(today, 6..11)) h.copy(weatherCode = 48) else h }
        assertEquals("Niebla hasta las 12:00", texts(now).first())
    }

    @Test fun `maxima de manana con 2,9 y 3 grados`() {
        // 20,0 → 22,9: en pantalla 20° y 23°, 3° más; 20,0 → 22,4: 20° y 22°, no sale.
        val up = snap(day = { i, d -> if (i == 1) d.copy(maxC = 22.9) else d })
        assertEquals(SummaryLine(SummaryIcon.TEMP_UP, "Mañana, máxima de 23°: 3° más que hoy"), lines(up).first())
        val small = snap(day = { i, d -> if (i == 1) d.copy(maxC = 22.4) else d })
        assertEquals(listOf("Sin lluvia en los próximos 10 días"), texts(small))
        val down = snap(day = { i, d -> if (i == 1) d.copy(maxC = 16.0) else d })
        assertEquals(SummaryLine(SummaryIcon.TEMP_DOWN, "Mañana, máxima de 16°: 4° menos que hoy"), lines(down).first())
    }

    // ---------- 6 · lluvia más adelante ----------

    @Test fun `lluvia en los 10 dias - manana, el jueves y el dia 9`() {
        assertEquals("Lluvia el jueves", texts(snap(day = { i, d -> if (i == 2) d.copy(precipSumMm = 3.0) else d })).first())
        assertEquals("Lluvia el jueves 19", texts(snap(day = { i, d -> if (i == 9) d.copy(precipSumMm = 3.0) else d })).first())
        val late = snap(day = { i, d -> if (i == 1) d.copy(precipSumMm = 2.0) else d }) { t, h ->
            if (t.isAt(tomorrow, 20..21)) h.copy(precipMm = 1.0) else h
        }
        assertEquals(SummaryLine(SummaryIcon.RAIN, "Lluvia mañana"), lines(late).first())
        // Menos de 1 mm en el día no cuenta; y lo que llovió hoy ya no es «más adelante».
        val little = snap(day = { i, d -> if (i == 0) d.copy(precipSumMm = 5.0) else if (i == 3) d.copy(precipSumMm = 0.8) else d })
        assertEquals(listOf("Sin lluvia en los próximos 10 días"), texts(little))
    }

    @Test fun `con lluvia en 24 h no se repite la de mas adelante`() {
        val s = snap(day = { i, d -> if (i == 4) d.copy(precipSumMm = 3.0) else d }) { t, h ->
            if (t.isAt(today, 16..16)) h.copy(precipMm = 0.4) else h
        }
        assertEquals(listOf("Empieza a llover hoy a las 16:00"), texts(s))
    }

    // ---------- orden y tope ----------

    @Test fun `por orden de importancia y como mucho tres`() {
        val s = snap(day = { i, d -> if (i == 1) d.copy(maxC = 26.0) else d }) { t, h ->
            when {
                t.isAt(today, 18..18) -> h.copy(precipMm = 0.5, windGustsKmh = 70.0)
                t.isAt(tomorrow, 4..4) -> h.copy(temperatureC = -1.0, weatherCode = 45)
                else -> h
            }
        }
        val all = lines(s)
        assertEquals(listOf(SummaryIcon.RAIN, SummaryIcon.GUST, SummaryIcon.FROST), all.map { it.icon })
    }

    // ---------- medianoche y cambio de hora ----------

    @Test fun `a medianoche, esta noche y manana dependen de now`() {
        val s = snap { t, h -> if (t.isAt(tomorrow, 1..1)) h.copy(precipMm = 0.5) else h }
        assertEquals("Empieza a llover esta noche a las 01:00", texts(s, TestData.at(2025, 6, 10, 23, 59)).first())
        assertEquals("Empieza a llover esta noche a las 01:00", texts(s, TestData.at(2025, 6, 11, 0, 1)).first())
        assertEquals("Empieza a llover mañana a las 01:00", texts(s, TestData.at(2025, 6, 10, 10, 0)).first())
    }

    @Test fun `cambio de hora de marzo - la hora que se salta no existe`() {
        val at = TestData.at(2025, 3, 29, 23, 10) // la noche del último domingo de marzo
        val s = snap(at) { t, h -> if (t.isAt(LocalDate.of(2025, 3, 30), 3..4)) h.copy(precipMm = 0.6) else h }
        assertEquals("Empieza a llover esta noche a las 03:00 · 1 mm", texts(s, at).first())
    }

    @Test fun `cambio de hora de octubre - la hora repetida cuenta dos veces`() {
        val at = TestData.at(2025, 10, 26, 0, 30)
        val day = LocalDate.of(2025, 10, 26)
        // De 00:00 a 02:59 locales: cuatro horas de verdad (las 02:00 salen dos veces); para a las 03:00.
        val s = snap(at) { t, h -> if (t.isAt(day, 0..2)) h.copy(precipMm = 1.0) else h }
        assertEquals(4, s.hourly.count { it.precipMm > 0 })
        assertEquals("Para de llover a las 03:00 · 4 mm", texts(s, at).first())
    }

    // ---------- Resumen del día (P4) ----------

    @Test fun `resumen de hoy cuenta desde ahora`() {
        val s = snap { t, h -> if (t.isAt(today, 8..8) || t.isAt(today, 16..18)) h.copy(precipMm = 2.0) else h }
        assertEquals("Lluvia de 16:00 a 19:00 · 6 mm", daySummary(s, today.toString(), now, zone))
    }

    @Test fun `resumen con lluvia a ratos`() {
        val date = today.plusDays(2)
        val s = snap { t, h -> if (t.isAt(date, 6..6) || t.isAt(date, 21..21)) h.copy(precipMm = 1.5) else h }
        assertEquals("Lluvia a ratos de 06:00 a 22:00 · 3 mm", daySummary(s, date.toString(), now, zone))
    }

    @Test fun `resumen de manana con lluvia todo el dia`() {
        val s = snap { t, h -> if (t.toLocalDate() == tomorrow) h.copy(precipMm = 1.0, weatherCode = 63) else h }
        assertEquals("Lluvia todo el día · 24 mm", daySummary(s, tomorrow.toString(), now, zone))
    }

    @Test fun `resumen de un dia seco dice la maxima y su hora`() {
        val date = today.plusDays(3)
        val s = snap { t, h -> if (t.isAt(date, 16..16)) h.copy(temperatureC = 25.2) else h }
        assertEquals("Sin lluvia; máxima de 25° hacia las 16:00", daySummary(s, date.toString(), now, zone))
    }

    @Test fun `resumen de un dia sin horas usa el dia`() {
        val s = snap().let { it.copy(hourly = it.hourly.filter { h -> Instant.ofEpochSecond(h.time).atZone(zone).toLocalDate() < today.plusDays(5) }) }
        val text = daySummary(s, today.plusDays(7).toString(), now, zone)
        assertEquals("Máxima de 20° y mínima de 10°", text)
        assertTrue(daySummary(s, "2030-01-01", now, zone).isEmpty())
    }

    // ---------- la hora de los avisos guardados ----------

    @Test fun `hora de los avisos guardados`() {
        assertEquals("Avisos guardados · datos de las 08:15", savedWarningsLabel(TestData.at(2025, 6, 10, 8, 15), now, zone))
        assertEquals("Avisos guardados · datos de ayer, 21:40", savedWarningsLabel(TestData.at(2025, 6, 9, 21, 40), now, zone))
    }
}
