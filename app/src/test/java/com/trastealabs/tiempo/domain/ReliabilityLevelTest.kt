package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** H1: bordes de la tabla con escenarios inventados. */
class ReliabilityLevelTest {

    /** [n] escenarios: [wet] con 5 mm y el resto secos; la mitad con 20,0° y la otra mitad con 20,0° + [spread]. */
    private fun day(wet: Int, spread: Double = 0.0, n: Int = 100, base: Double = 20.0): DayReliability {
        val maxima = List(n) { if (it < n / 2) base else base + spread }
        val precip = List(n) { if (it < wet) 5.0 else 0.0 }
        return summarizeDay("2026-11-05", maxima, precip, emptyList())
    }

    @Test fun `90 por ciento y 2,0 grados es muy alta`() {
        assertEquals(ReliabilityLevel.VERY_HIGH, day(wet = 10, spread = 2.0).level())
        // la mayoría también puede ser la lluvia
        assertEquals(ReliabilityLevel.VERY_HIGH, day(wet = 90, spread = 2.0).level())
    }

    @Test fun `89 por ciento ya es alta`() {
        assertEquals(ReliabilityLevel.HIGH, day(wet = 11).level())
    }

    @Test fun `2,1 grados ya es alta`() {
        assertEquals(ReliabilityLevel.HIGH, day(wet = 0, spread = 2.1).level())
    }

    @Test fun `bordes de alta y media en la lluvia`() {
        assertEquals(ReliabilityLevel.HIGH, day(wet = 25).level())
        assertEquals(ReliabilityLevel.MEDIUM, day(wet = 26).level())
        assertEquals(ReliabilityLevel.MEDIUM, day(wet = 40).level())
        assertEquals(ReliabilityLevel.LOW, day(wet = 41).level())
    }

    @Test fun `bordes de alta y media en la maxima`() {
        assertEquals(ReliabilityLevel.HIGH, day(wet = 0, spread = 4.0).level())
        assertEquals(ReliabilityLevel.MEDIUM, day(wet = 0, spread = 4.1).level())
        assertEquals(ReliabilityLevel.MEDIUM, day(wet = 0, spread = 6.0).level())
        assertEquals(ReliabilityLevel.LOW, day(wet = 0, spread = 6.1).level())
    }

    @Test fun `manda el peor de los dos`() {
        assertEquals(ReliabilityLevel.MEDIUM, day(wet = 5, spread = 5.0).level())
        assertEquals(ReliabilityLevel.LOW, day(wet = 50, spread = 1.0).level())
        assertEquals(ReliabilityLevel.HIGH, day(wet = 20, spread = 0.5).level())
    }

    @Test fun `el 10 por ciento de cada extremo no cuenta`() {
        // 98 escenarios a 20° y dos disparatados: el rango recortado sigue siendo 20–20
        val maxima = List(98) { 20.0 } + listOf(35.0, 5.0)
        val d = summarizeDay("2026-11-05", maxima, List(100) { 0.0 }, emptyList())
        assertEquals(20.0, d.maxLowC, 0.0)
        assertEquals(20.0, d.maxHighC, 0.0)
        assertEquals(ReliabilityLevel.VERY_HIGH, d.level())
    }

    @Test fun `el recorte quita n entre 10 escenarios de cada lado`() {
        val maxima = (1..25).map { it.toDouble() }.shuffled(java.util.Random(7))
        val d = summarizeDay("2026-11-05", maxima, List(25) { 0.0 }, emptyList())
        // k = 25 / 10 = 2: se quedan del 3 al 23
        assertEquals(3.0, d.maxLowC, 0.0)
        assertEquals(23.0, d.maxHighC, 0.0)
    }

    @Test fun `0,9 mm es seco y 1,0 mm es lluvia`() {
        val n = 30
        assertEquals(0, summarizeDay("d", List(n) { 20.0 }, List(n) { 0.9 }, emptyList()).wetScenarios)
        assertEquals(n, summarizeDay("d", List(n) { 20.0 }, List(n) { 1.0 }, emptyList()).wetScenarios)
        assertEquals(n, summarizeDay("d", List(n) { 20.0 }, List(n) { 1.0 }, emptyList()).scenarios)
    }

    @Test fun `con 19 escenarios no hay nivel y con 20 si`() {
        assertNull(day(wet = 0, n = 19).level())
        assertEquals(ReliabilityLevel.VERY_HIGH, day(wet = 0, n = 20).level())
    }

    @Test fun `22,3 menos 20,3 cuenta como 2,0 grados`() {
        assertEquals(ReliabilityLevel.VERY_HIGH, day(wet = 0, spread = 2.0, base = 20.3).level())
        val maxima = List(50) { 20.3 } + List(50) { 22.3 }
        val d = summarizeDay("2026-11-05", maxima, List(100) { 0.0 }, emptyList())
        assertEquals(ReliabilityLevel.VERY_HIGH, d.level())
    }

    @Test fun `el resumen guarda la fecha y los modelos`() {
        val models = listOf(ModelDay(WeatherModel.ECMWF, 3, 21.0, 0.0))
        val d = summarizeDay("2026-11-05", List(30) { 20.0 }, List(30) { 0.0 }, models)
        assertEquals("2026-11-05", d.date)
        assertEquals(models, d.models)
    }
}
