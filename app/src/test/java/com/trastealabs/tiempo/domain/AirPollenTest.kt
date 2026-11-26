package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.TestData.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** H4–H9: las reglas del aire y del polen, con datos inventados para Villa Ejemplo (Europe/Madrid). */
class AirPollenTest {
    private val zone = TestData.zone

    /**
     * Como la API: [count] instantes seguidos de 3600 s desde las 00:00 locales de [from] (el día del cambio de hora
     * no se corrige). [hour] da la hora de cada instante o null para no guardarla (hora vacía).
     */
    private fun hours(from: LocalDate, count: Int, hour: (i: Int, time: Long) -> AirHour?): List<AirHour> {
        val start = from.atStartOfDay(zone).toEpochSecond()
        return (0 until count).mapNotNull { i -> hour(i, start + i * 3600L) }
    }

    private fun snapshot(now: Long, hours: List<AirHour>, fetchedAt: Long = now): WeatherSnapshot =
        TestData.snapshot(now).copy(air = AirQuality(TestData.place.position, fetchedAt, hours))

    private fun h(
        aqi: Int? = 50, pm25: Int? = null, pm10i: Int? = null, no2: Int? = null, o3: Int? = null, so2: Int? = null,
        pm10: Double? = null, dust: Double? = null,
    ) = AirHour(0, aqi, pm25, pm10i, no2, o3, so2, pm10, dust)

    // ---------- H4 ----------

    @Test fun `H4 niveles del indice a los dos lados de cada borde`() {
        val expected = mapOf(
            0 to AirLevel.GOOD, 20 to AirLevel.GOOD, 21 to AirLevel.FAIR, 40 to AirLevel.FAIR,
            41 to AirLevel.MODERATE, 60 to AirLevel.MODERATE, 61 to AirLevel.POOR, 80 to AirLevel.POOR,
            81 to AirLevel.VERY_POOR, 100 to AirLevel.VERY_POOR, 101 to AirLevel.EXTREMELY_POOR, 134 to AirLevel.EXTREMELY_POOR,
        )
        expected.forEach { (aqi, level) -> assertEquals("índice $aqi", level, airLevel(aqi)) }
        assertEquals("Extremadamente desfavorable", airLevel(134).label)
    }

    // ---------- H5 ----------

    @Test fun `H5 gana cada contaminante`() {
        assertEquals(AirCause.PARTICLES, airCause(h(pm25 = 50, pm10i = 10, o3 = 30, no2 = 20, so2 = 5)))
        assertEquals(AirCause.PARTICLES, airCause(h(pm25 = 10, pm10i = 50, o3 = 30, no2 = 20, so2 = 5, pm10 = 40.0, dust = 2.0)))
        assertEquals(AirCause.OZONE, airCause(h(pm25 = 10, pm10i = 10, o3 = 50, no2 = 20, so2 = 5)))
        assertEquals(AirCause.NO2, airCause(h(pm25 = 10, pm10i = 10, o3 = 30, no2 = 50, so2 = 5)))
        assertEquals(AirCause.SO2, airCause(h(pm25 = 10, pm10i = 10, o3 = 30, no2 = 20, so2 = 50)))
    }

    @Test fun `H5 calima con el polvo justo a la mitad de las PM10 y particulas justo por debajo`() {
        assertEquals(AirCause.SAHARAN_DUST, airCause(h(pm10i = 50, o3 = 30, pm10 = 40.0, dust = 20.0)))
        assertEquals(AirCause.PARTICLES, airCause(h(pm10i = 50, o3 = 30, pm10 = 40.0, dust = 19.9)))
        // sin pm10 o sin polvo no hay calima
        assertEquals(AirCause.PARTICLES, airCause(h(pm10i = 50, o3 = 30, dust = 20.0)))
        assertEquals(AirCause.PARTICLES, airCause(h(pm10i = 50, o3 = 30, pm10 = 40.0)))
        // si gana el ozono, el polvo no cuenta
        assertEquals(AirCause.OZONE, airCause(h(pm10i = 30, o3 = 50, pm10 = 40.0, dust = 30.0)))
    }

    @Test fun `H5 empates particulas-ozono y ozono-NO2`() {
        assertEquals(AirCause.PARTICLES, airCause(h(pm25 = 45, o3 = 45, no2 = 10)))
        assertEquals(AirCause.OZONE, airCause(h(pm25 = 10, o3 = 45, no2 = 45)))
        assertEquals(AirCause.NO2, airCause(h(o3 = 10, no2 = 45, so2 = 45)))
    }

    @Test fun `H5 Buena va sin causa, y sin subindices o sin indice tampoco hay`() {
        assertNull(airCause(h(aqi = 20, o3 = 20)))
        assertNull(airCause(h(aqi = 50)))
        assertNull(airCause(h(aqi = null, o3 = 50)))
    }

    // ---------- H6 ----------

    private val may5 = LocalDate.of(2026, 5, 5)
    private val noon = at(2026, 5, 5, 12, 40)

    /** El índice de cada hora es 20 + su número desde el inicio; el ozono, el mismo (gana el ozono). */
    private fun airHours(count: Int = 120) = hours(may5, count) { i, t -> AirHour(t, aqi = 20 + i, ozoneIndex = 20 + i, pm25Index = 5) }

    @Test fun `H6 con el dato fresco, la hora de ahora`() {
        val air = airNow(snapshot(noon, airHours()), noon)!!
        assertEquals(32, air.aqi)
        assertEquals(AirLevel.FAIR, air.level)
        assertEquals(AirCause.OZONE, air.cause)
    }

    @Test fun `H6 guardado hace 6 h da la prevision de esta hora, no la de cuando se guardo`() {
        val air = airNow(snapshot(noon, airHours(), fetchedAt = noon - 6 * 3600), noon)!!
        assertEquals(32, air.aqi)
    }

    @Test fun `H6 la hora en punto ya es la nueva y un segundo antes la anterior`() {
        val s = snapshot(noon, airHours())
        assertEquals(33, airNow(s, at(2026, 5, 5, 13, 0))!!.aqi)
        assertEquals(32, airNow(s, at(2026, 5, 5, 13, 0) - 1)!!.aqi)
    }

    @Test fun `H6 caducado, si lo guardado no llega a esta hora no hay cifra`() {
        // Datos hasta las 11:00 (12 horas: de 00:00 a 11:00)
        assertNull(airNow(snapshot(noon, airHours(count = 12), fetchedAt = noon - 3 * 86400), noon))
    }

    @Test fun `H6 la hora de ahora sin indice no da cifra`() {
        val hours = hours(may5, 120) { i, t -> if (i == 12) AirHour(t, pm10 = 30.0) else AirHour(t, aqi = 30) }
        assertNull(airNow(snapshot(noon, hours), noon))
        // y si la hora no se guardó (vacía), tampoco
        val missing = hours(may5, 120) { i, t -> if (i == 12) null else AirHour(t, aqi = 30) }
        assertNull(airNow(snapshot(noon, missing), noon))
    }

    @Test fun `H6 sin aire guardado (datos de antes) no hay cifra`() {
        assertNull(airNow(TestData.snapshot(noon), noon))
        assertTrue(airDays(TestData.snapshot(noon), noon).isEmpty())
        assertTrue(pollenDays(TestData.snapshot(noon), noon).isEmpty())
        assertNull(pollenToday(TestData.snapshot(noon), noon))
    }

    // ---------- H7 ----------

    @Test fun `H7 niveles del olivo y las gramineas a los dos lados de cada borde`() {
        val olive = mapOf(0 to PollenLevel.NONE, 1 to PollenLevel.LOW, 50 to PollenLevel.LOW, 51 to PollenLevel.MODERATE,
            200 to PollenLevel.MODERATE, 201 to PollenLevel.HIGH)
        olive.forEach { (g, l) -> assertEquals("olivo $g", l, pollenLevel(PollenType.OLIVE, g)) }
        val grass = mapOf(0 to PollenLevel.NONE, 1 to PollenLevel.LOW, 25 to PollenLevel.LOW, 26 to PollenLevel.MODERATE,
            50 to PollenLevel.MODERATE, 51 to PollenLevel.HIGH)
        grass.forEach { (g, l) -> assertEquals("gramíneas $g", l, pollenLevel(PollenType.GRASS, g)) }
    }

    private fun pollenHours(olive: (day: Int) -> Double?, grass: (day: Int) -> Double? = { 0.0 }, count: Int = 98) =
        hours(may5, count) { i, t -> AirHour(t, aqi = 30, olivePollen = olive(i / 24), grassPollen = grass(i / 24)) }

    @Test fun `H7 la media del dia se redondea`() {
        val values = listOf(0.4, 0.5, 50.4, 50.5)
        val days = pollenDays(snapshot(noon, pollenHours(olive = { values.getOrNull(it) })), noon)
        assertEquals(listOf(PollenLevel.NONE, PollenLevel.LOW, PollenLevel.LOW, PollenLevel.MODERATE), days.map { it.olive })
    }

    @Test fun `H7 la media es de todas las horas del dia en hora local`() {
        // 12 horas a 0 y 12 a 101: media 50,5 → 51, moderado; con solo una mitad saldría nada o alto.
        val hours = hours(may5, 98) { i, t -> AirHour(t, aqi = 30, olivePollen = if (i % 24 < 12) 0.0 else 101.0, grassPollen = 0.0) }
        assertEquals(PollenLevel.MODERATE, pollenDays(snapshot(noon, hours), noon).first().olive)
    }

    // ---------- H8 ----------

    @Test fun `H8 sin polen que llegue a bajo no hay ficha`() {
        assertNull(pollenToday(snapshot(noon, pollenHours(olive = { 0.0 })), noon))
        assertNull(pollenToday(snapshot(noon, pollenHours(olive = { 0.4 }, grass = { 0.3 })), noon))
    }

    @Test fun `H8 solo olivo`() {
        val today = pollenToday(snapshot(noon, pollenHours(olive = { 300.0 })), noon)!!
        assertEquals(listOf(PollenType.OLIVE to PollenLevel.HIGH), today.types)
        assertEquals("Polen hoy: olivo alto.", today.spoken())
    }

    @Test fun `H8 los dos, olivo primero`() {
        val today = pollenToday(snapshot(noon, pollenHours(olive = { 300.0 }, grass = { 30.0 })), noon)!!
        assertEquals(listOf(PollenType.OLIVE to PollenLevel.HIGH, PollenType.GRASS to PollenLevel.MODERATE), today.types)
        assertEquals("Polen hoy: olivo alto, gramíneas moderado.", today.spoken())
    }

    @Test fun `H8 a medianoche hoy pasa a ser el dia siguiente`() {
        val s = snapshot(noon, pollenHours(olive = { if (it == 0) 300.0 else 30.0 }))
        assertEquals(PollenLevel.HIGH, pollenToday(s, at(2026, 5, 5, 23, 59))!!.types.single().second)
        assertEquals(PollenLevel.LOW, pollenToday(s, at(2026, 5, 6, 0, 1))!!.types.single().second)
    }

    @Test fun `H8 si hoy no esta completo no hay ficha aunque manana si`() {
        // datos desde las 06:00 de hoy: le faltan sus primeras horas
        val hours = hours(may5, 98) { i, t -> if (i < 6) null else AirHour(t, aqi = 30, olivePollen = 300.0, grassPollen = 0.0) }
        val s = snapshot(noon, hours)
        assertNull(pollenToday(s, noon))
        assertEquals(LocalDate.of(2026, 5, 6).toString(), pollenDays(s, noon).first().date)
    }

    // ---------- H9 ----------

    @Test fun `H9 cuatro dias de aire con su peor hora y uno a medias que no sale`() {
        // El índice sube con la hora del día; el día i tiene su peor hora a las 23:00, 40 + 10·i.
        val full = hours(may5, 120) { i, t -> AirHour(t, aqi = 10 * (i / 24) + 17 + (i % 24)) }
        val days = airDays(snapshot(noon, full), noon)
        assertEquals(listOf("2026-05-05", "2026-05-06", "2026-05-07", "2026-05-08"), days.map { it.date })
        assertEquals(listOf(40, 50, 60, 70), days.map { it.worstAqi })
        assertEquals(listOf(AirLevel.FAIR, AirLevel.MODERATE, AirLevel.MODERATE, AirLevel.POOR), days.map { it.level })

        // El cuarto día solo hasta las 12:00: no sale.
        val half = hours(may5, 3 * 24 + 13) { i, t -> AirHour(t, aqi = 10 * (i / 24) + 17 + (i % 24)) }
        assertEquals(listOf("2026-05-05", "2026-05-06", "2026-05-07"), airDays(snapshot(noon, half), noon).map { it.date })
    }

    @Test fun `H9 cuatro dias de polen y uno a medias que no sale`() {
        val olive = listOf(300.0, 100.0, 10.0, 0.0)
        val grass = listOf(30.0, 60.0, 0.0, 10.0)
        val days = pollenDays(snapshot(noon, pollenHours(olive = { olive.getOrNull(it) }, grass = { grass.getOrNull(it) })), noon)
        assertEquals(4, days.size)
        assertEquals(listOf(PollenLevel.HIGH, PollenLevel.MODERATE, PollenLevel.LOW, PollenLevel.NONE), days.map { it.olive })
        assertEquals(listOf(PollenLevel.MODERATE, PollenLevel.HIGH, PollenLevel.NONE, PollenLevel.LOW), days.map { it.grass })

        // Como la API: el polen llega hasta la hora 97 y el índice sigue; el cuarto día del polen hasta las 12:00.
        val cut = hours(may5, 120) { i, t -> AirHour(t, aqi = 30, olivePollen = if (i < 3 * 24 + 13) 100.0 else null, grassPollen = if (i < 3 * 24 + 13) 0.0 else null) }
        val s = snapshot(noon, cut)
        assertEquals(3, pollenDays(s, noon).size)
        assertEquals(4, airDays(s, noon).size)
    }

    @Test fun `H9 una hora vacia en medio no quita el dia ni cuenta para la peor hora`() {
        val hours = hours(may5, 120) { i, t ->
            when (i) {
                10 -> null                                    // hora entera vacía
                11 -> AirHour(t, pm10 = 500.0, olivePollen = 100.0, grassPollen = 0.0)   // sin índice
                else -> AirHour(t, aqi = 30, olivePollen = 100.0, grassPollen = 0.0)
            }
        }
        val s = snapshot(noon, hours)
        val air = airDays(s, noon)
        assertEquals(4, air.size)
        assertEquals(30, air.first().worstAqi)
        assertEquals(4, pollenDays(s, noon).size)
    }

    @Test fun `H9 datos guardados hace dias, solo los dias que aun cubren`() {
        // Pedido el 3-may: llega hasta el 7-may a las 23:00. El 5 de mayo, hoy, el 6 y el 7.
        val hours = hours(LocalDate.of(2026, 5, 3), 120) { _, t -> AirHour(t, aqi = 30) }
        val days = airDays(snapshot(noon, hours, fetchedAt = at(2026, 5, 3, 1, 0)), noon)
        assertEquals(listOf("2026-05-05", "2026-05-06", "2026-05-07"), days.map { it.date })
    }

    // ---------- Fuera de Europa ----------

    @Test fun `fuera de Europa sin polen no hay dias de polen y el aire si sale`() {
        val hours = hours(may5, 120) { _, t -> AirHour(t, aqi = 45, no2Index = 45) }
        val s = snapshot(noon, hours)
        assertTrue(pollenDays(s, noon).isEmpty())
        assertNull(pollenToday(s, noon))
        assertEquals(4, airDays(s, noon).size)
        assertEquals(AirCause.NO2, airNow(s, noon)!!.cause)
    }

    // ---------- Cambio de hora ----------

    @Test fun `25 de octubre de 2026, dia de 25 horas, y el ultimo dia cortado no sale`() {
        val oct25 = LocalDate.of(2026, 10, 25)
        val now = at(2026, 10, 25, 12, 0)
        // 96 instantes seguidos desde las 00:00: el 25 tiene 25 horas y al 28 le falta su última hora.
        val hours = hours(oct25, 96) { i, t ->
            // La peor hora del 25 es la última de sus 25 (la de las 23:00, instante 24).
            AirHour(t, aqi = if (i == 24) 77 else 30, olivePollen = 50.0, grassPollen = 0.0)
        }
        val s = snapshot(now, hours)
        assertEquals(25, s.air!!.hours.count { java.time.Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate() == oct25 })
        val air = airDays(s, now)
        assertEquals(listOf("2026-10-25", "2026-10-26", "2026-10-27"), air.map { it.date })
        assertEquals(77, air.first().worstAqi)
        // media de 25 horas a 50: 50, bajo (dividir entre 24 daría 52, moderado)
        val pollen = pollenDays(s, now)
        assertEquals(3, pollen.size)
        assertEquals(PollenLevel.LOW, pollen.first().olive)
    }

    @Test fun `28 de marzo de 2027, dia de 23 horas`() {
        val mar28 = LocalDate.of(2027, 3, 28)
        val now = at(2027, 3, 28, 12, 0)
        val hours = hours(mar28, 96) { i, t -> AirHour(t, aqi = if (i == 22) 66 else 30, olivePollen = 52.0, grassPollen = 0.0) }
        val s = snapshot(now, hours)
        val air = airDays(s, now)
        assertEquals(listOf("2027-03-28", "2027-03-29", "2027-03-30", "2027-03-31"), air.map { it.date })
        assertEquals(66, air.first().worstAqi)   // la 23:00, su última hora
        // media de 23 horas a 52: 52, moderado (dividir entre 24 daría 50, bajo)
        assertEquals(PollenLevel.MODERATE, pollenDays(s, now).first().olive)
    }

    // ---------- TalkBack ----------

    @Test fun `frases de TalkBack`() {
        assertEquals("Calidad del aire: razonablemente buena, índice 35. Por el ozono.", AirNow(35, AirLevel.FAIR, AirCause.OZONE).spoken())
        assertEquals("Calidad del aire: buena, índice 16.", AirNow(16, AirLevel.GOOD, null).spoken())
        assertEquals(
            "Calidad del aire: extremadamente desfavorable, índice 134. Por polvo del Sáhara.",
            AirNow(134, AirLevel.EXTREMELY_POOR, AirCause.SAHARAN_DUST).spoken(),
        )
        assertEquals("Polen hoy: gramíneas bajo.", PollenToday(listOf(PollenType.GRASS to PollenLevel.LOW)).spoken())
        assertNotNull(airNow(snapshot(noon, airHours()), noon)?.spoken())
    }
}
