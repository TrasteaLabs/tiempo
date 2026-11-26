package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.domain.MoonPhaseName.FIRST_QUARTER
import com.trastealabs.tiempo.domain.MoonPhaseName.FULL
import com.trastealabs.tiempo.domain.MoonPhaseName.LAST_QUARTER
import com.trastealabs.tiempo.domain.MoonPhaseName.NEW
import com.trastealabs.tiempo.domain.MoonPhaseName.WANING_CRESCENT
import com.trastealabs.tiempo.domain.MoonPhaseName.WANING_GIBBOUS
import com.trastealabs.tiempo.domain.MoonPhaseName.WAXING_CRESCENT
import com.trastealabs.tiempo.domain.MoonPhaseName.WAXING_GIBBOUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.trastealabs.tiempo.TestData

/** H3: nombre y % iluminado de la fase que da Open-Meteo. */
class MoonTest {
    @Test fun `los ejemplos de la spec`() {
        assertEquals(NEW, moonPhaseName(0.005))
        assertEquals(0, moonIlluminatedPct(0.005))
        assertEquals(FIRST_QUARTER, moonPhaseName(0.25))
        assertEquals(FULL, moonPhaseName(0.5))
        assertEquals(100, moonIlluminatedPct(0.5))
        assertEquals(LAST_QUARTER, moonPhaseName(0.75))
        assertEquals(WANING_CRESCENT, moonPhaseName(0.84))
        assertEquals(23, moonIlluminatedPct(0.84))
        assertEquals("Menguante · 23 %", moonPhaseLabel(0.84))
        assertEquals("Luna llena · 100 %", moonPhaseLabel(0.5))
    }

    @Test fun `a 0,034 de cada fase principal es esa fase y a 0,035 la de entre medias`() {
        val cases = listOf(
            0.034 to NEW, 0.035 to WAXING_CRESCENT,
            0.216 to FIRST_QUARTER, 0.215 to WAXING_CRESCENT,
            0.284 to FIRST_QUARTER, 0.285 to WAXING_GIBBOUS,
            0.466 to FULL, 0.465 to WAXING_GIBBOUS,
            0.534 to FULL, 0.535 to WANING_GIBBOUS,
            0.716 to LAST_QUARTER, 0.715 to WANING_GIBBOUS,
            0.784 to LAST_QUARTER, 0.785 to WANING_CRESCENT,
            0.966 to NEW, 0.965 to WANING_CRESCENT,
            0.0 to NEW, 1.0 to NEW,
        )
        cases.forEach { (phase, name) -> assertEquals("fase $phase", name, moonPhaseName(phase)) }
    }

    @Test fun `los nombres son los de la spec`() {
        assertEquals(
            listOf("Luna nueva", "Creciente", "Cuarto creciente", "Gibosa creciente", "Luna llena", "Gibosa menguante", "Cuarto menguante", "Menguante"),
            MoonPhaseName.entries.map { it.label },
        )
    }

    @Test fun `cuarto creciente y cuarto menguante iluminan la mitad`() {
        assertEquals(50, moonIlluminatedPct(0.25))
        assertEquals(50, moonIlluminatedPct(0.75))
    }

    // 007, H12: la luna que se dibuja.
    @Test fun `moonPhaseToday con dato es la fase de hoy y sin dato llena`() {
        val now = TestData.at(2026, 11, 6, 23)
        val base = TestData.snapshot(now)
        val withPhase = base.copy(daily = base.daily.mapIndexed { i, d -> if (i == 0) d.copy(moonPhase = 0.84) else d })
        assertEquals(0.84, moonPhaseToday(withPhase, now), 1e-9)
        assertEquals(0.5, moonPhaseToday(base, now), 1e-9)
    }

    @Test fun `isSouth solo al sur del ecuador`() {
        val now = TestData.at(2026, 11, 6, 23)
        assertFalse(isSouth(TestData.snapshot(now)))
        val south = Place("Puerto Inventado", "AR", LatLon(-34.6, -58.4))
        assertTrue(isSouth(TestData.snapshot(now, place = south)))
    }
}
