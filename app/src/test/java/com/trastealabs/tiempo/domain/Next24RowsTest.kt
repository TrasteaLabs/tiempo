package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las filas de las hojas de viento y humedad (H6 de la 004, acotadas el 2026-11-04): el viento en tramos de 3 horas y
 * la humedad por partes del día. Datos inventados (C5).
 */
class Next24RowsTest {
    private val zone = TestData.zone

    private fun hourly(
        start: Long = TestData.at(2026, 11, 2, 0, 0),
        count: Int = 96,
        wind: (Int) -> Double? = { 6.0 },
        gusts: (Int) -> Double? = { 12.0 },
        from: (Int) -> Int? = { 270 },
        humidity: (Int) -> Int? = { 60 },
        dew: (Int) -> Double? = { 8.0 },
    ): List<HourPoint> = (0 until count).map {
        HourPoint(
            start + it * 3600L, 10.0, 0, 0.0, 0, true,
            windKmh = wind(it), windGustsKmh = gusts(it), windDirectionDeg = from(it),
            humidityPct = humidity(it), dewPointC = dew(it),
        )
    }

    /** Índice en [hourly] (que empieza el 2-nov a las 00:00) de esa hora del 3-nov. */
    private fun nov3(hour: Int) = 24 + hour

    private fun winds(rows: List<SheetRow>) = rows.filterIsInstance<SheetRow.Wind>()
    private fun humidities(rows: List<SheetRow>) = rows.filterIsInstance<SheetRow.Humidity>()

    // ---------- viento, cada 3 horas ----------

    @Test fun `el viento va en 8 tramos de 3 horas en punto, el primero Ahora`() {
        val now = TestData.at(2026, 11, 3, 15, 20)
        val rows = winds(windRows(hourly(), now, zone))
        assertEquals(8, rows.size)
        assertEquals(listOf("Ahora", "18–21", "21–24", "00–03", "03–06", "06–09", "09–12", "12–15"), rows.map { it.label })
    }

    @Test fun `a media hora del tramo, Ahora cubre lo que queda de el`() {
        // A las 16:40 el tramo de ahora es 15–18 (15, 16 y 17); los datos de antes de las 16 no cuentan.
        val now = TestData.at(2026, 11, 3, 16, 40)
        val rows = winds(windRows(hourly(wind = { if (it == nov3(15)) 40.0 else 6.0 }), now, zone))
        assertEquals("Ahora", rows[0].label)
        assertEquals(6, rows[0].windKmh)
        assertEquals("18–21", rows[1].label)
    }

    @Test fun `cada tramo lleva el viento y la racha mas fuertes y la direccion de la hora mas fuerte`() {
        val now = TestData.at(2026, 11, 3, 17, 0)
        val h = hourly(
            wind = { when (it) { nov3(19) -> 18.4; nov3(20) -> 9.0; else -> 6.0 } },
            gusts = { when (it) { nov3(18) -> 33.6; else -> 12.0 } },
            from = { if (it == nov3(19)) 45 else 270 },
        )
        val tramo = winds(windRows(h, now, zone))[1]
        assertEquals("18–21", tramo.label)
        assertEquals(18, tramo.windKmh)
        assertEquals(34, tramo.gustKmh)
        assertEquals(45, tramo.fromDeg)
    }

    @Test fun `con viento flojo sale la cifra, nunca Calma`() {
        val now = TestData.at(2026, 11, 3, 3, 0)
        val tramo = winds(windRows(hourly(wind = { 2.0 }, gusts = { 7.0 }), now, zone))[0]
        assertEquals(2, tramo.windKmh)
        assertEquals(7, tramo.gustKmh)
    }

    @Test fun `sin dato de viento el tramo va vacio`() {
        val now = TestData.at(2026, 11, 3, 3, 0)
        val tramo = winds(windRows(hourly(wind = { null }, gusts = { null }, from = { null }), now, zone))[0]
        assertNull(tramo.windKmh)
        assertNull(tramo.gustKmh)
        assertNull(tramo.fromDeg)
    }

    @Test fun `antes del primer tramo del dia nuevo va la raya con su nombre`() {
        val now = TestData.at(2026, 11, 3, 15, 20)
        val rows = windRows(hourly(), now, zone)
        val breaks = rows.withIndex().filter { it.value is SheetRow.DayBreak }
        assertEquals(1, breaks.size)
        assertEquals("Miércoles, 4 de noviembre", (breaks.single().value as SheetRow.DayBreak).label)
        assertEquals("00–03", (rows[breaks.single().index + 1] as SheetRow.Wind).label)
    }

    @Test fun `si ahora es de madrugada no hay raya delante`() {
        val rows = windRows(hourly(), TestData.at(2026, 11, 3, 0, 10), zone)
        assertTrue(rows.first() is SheetRow.Wind)
        assertTrue(rows.none { it is SheetRow.DayBreak })
    }

    @Test fun `con pocos datos salen los tramos que haya`() {
        // Los datos acaban el 3-nov a las 19:59: el tramo 18–21 sale con sus dos horas y ya no hay más.
        val now = TestData.at(2026, 11, 3, 15, 20)
        val rows = winds(windRows(hourly(count = nov3(20)), now, zone))
        assertEquals(listOf("Ahora", "18–21"), rows.map { it.label })
    }

    @Test fun `el dia que se atrasa la hora los tramos siguen en punto (25 horas)`() {
        // 25-oct-2026 en Europe/Madrid: las 02:00 salen dos veces y caen en el tramo 00–03.
        val start = TestData.at(2026, 10, 24, 0, 0)
        val rows = winds(windRows(hourly(start = start), TestData.at(2026, 10, 24, 22, 0), zone))
        assertEquals(listOf("Ahora", "00–03", "03–06", "06–09", "09–12", "12–15", "15–18", "18–21"), rows.map { it.label })
    }

    @Test fun `la escala llega a la racha mas fuerte de los tramos redondeada a la decena`() {
        fun scale(gust: Int?, wind: Int?) = windScaleKmh(listOf(SheetRow.Wind("Ahora", wind, gust, 0), SheetRow.Wind("18–21", 4, 8, 0)))
        assertEquals(30, scale(gust = 28, wind = 10))
        assertEquals(30, scale(gust = 30, wind = 10))
        assertEquals(40, scale(gust = 31, wind = 10))
        // Sin rachas, el viento; y como poco, 10.
        assertEquals(50, scale(gust = null, wind = 41))
        assertEquals(10, windScaleKmh(listOf(SheetRow.Wind("Ahora", 1, 2, 0))))
        assertEquals(10, windScaleKmh(emptyList()))
    }

    // ---------- humedad, por partes del día ----------

    @Test fun `la humedad va en 4 partes del dia empezando por la de ahora`() {
        val now = TestData.at(2026, 11, 3, 15, 20)
        val rows = humidityRows(hourly(), now, zone)
        assertEquals(listOf("Tarde", "Noche", "Madrugada", "Mañana"), humidities(rows).map { it.label })
        // La raya del día nuevo va delante de la madrugada.
        val breakAt = rows.indexOfFirst { it is SheetRow.DayBreak }
        assertEquals("Miércoles, 4 de noviembre", (rows[breakAt] as SheetRow.DayBreak).label)
        assertEquals("Madrugada", (rows[breakAt + 1] as SheetRow.Humidity).label)
    }

    @Test fun `cada parte lleva su minima, su maxima y el rocio mas alto`() {
        val now = TestData.at(2026, 11, 3, 6, 0)
        val h = hourly(
            humidity = { when (it) { nov3(7) -> 88; nov3(11) -> 41; else -> 60 } },
            dew = { if (it == nov3(9)) 11.6 else 8.0 },
        )
        val manana = humidities(humidityRows(h, now, zone))[0]
        assertEquals("Mañana", manana.label)
        assertEquals(41, manana.minPct)
        assertEquals(88, manana.maxPct)
        assertEquals(11.6, manana.dewC!!, 1e-9)
    }

    @Test fun `la parte de ahora solo cuenta desde la hora de ahora`() {
        // A las 10:30 la mañana va de 10 a 12: la humedad de las 7 no cuenta.
        val now = TestData.at(2026, 11, 3, 10, 30)
        val h = hourly(humidity = { if (it == nov3(7)) 95 else 50 })
        assertEquals(50, humidities(humidityRows(h, now, zone))[0].maxPct)
    }

    @Test fun `las partes del dia van de 0 a 6, de 6 a 12, de 12 a 18 y de 18 a 24`() {
        listOf(0 to "Madrugada", 5 to "Madrugada", 6 to "Mañana", 11 to "Mañana", 12 to "Tarde", 17 to "Tarde", 18 to "Noche", 23 to "Noche")
            .forEach { (hour, label) ->
                assertEquals("$hour h", label, humidities(humidityRows(hourly(), TestData.at(2026, 11, 3, hour, 0), zone))[0].label)
            }
    }

    @Test fun `una parte sin ninguna humedad no sale`() {
        val now = TestData.at(2026, 11, 3, 15, 0)
        val h = hourly(humidity = { if (it in nov3(18)..nov3(23)) null else 60 })
        assertEquals(listOf("Tarde", "Madrugada", "Mañana"), humidities(humidityRows(h, now, zone)).map { it.label })
    }
}
