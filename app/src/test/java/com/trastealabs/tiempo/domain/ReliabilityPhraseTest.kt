package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.domain.WeatherModel.ECMWF
import com.trastealabs.tiempo.domain.WeatherModel.GEM
import com.trastealabs.tiempo.domain.WeatherModel.GFS
import com.trastealabs.tiempo.domain.WeatherModel.ICON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Datos inventados (C5). */
class ReliabilityPhraseTest {
    private fun m(model: WeatherModel, maxC: Double, mm: Double = 0.0, code: Int = 2) = ModelDay(model, code, maxC, mm)

    private fun offOf(models: List<ModelDay>) = modelVerdicts(models).filter { it.off }.map { it.day.model }

    @Test fun `lluvia contra la mayoria se aparta (H4)`() {
        val v = modelVerdicts(listOf(m(ECMWF, 20.0), m(ICON, 20.0), m(GFS, 20.0, mm = 2.8)))
        assertEquals(listOf(false, false, true), v.map { it.rainOff })
        assertEquals(listOf(GFS), v.filter { it.off }.map { it.day.model })
    }

    @Test fun `con empate en lluvia nadie se aparta por lluvia`() {
        val v = modelVerdicts(listOf(m(ECMWF, 20.0, mm = 3.0), m(ICON, 20.0), m(GFS, 20.0, mm = 1.0), m(GEM, 20.0)))
        assertTrue(v.none { it.rainOff })
    }

    @Test fun `0,9 mm es seco y 1,0 mm es lluvia`() {
        val v = modelVerdicts(listOf(m(ECMWF, 20.0, mm = 0.9), m(ICON, 20.0, mm = 0.0), m(GFS, 20.0, mm = 1.0)))
        assertEquals(listOf(false, false, true), v.map { it.rainOff })
    }

    @Test fun `maxima a 2,9 de la mediana no se aparta y a 3,0 si (mediana con numero par)`() {
        // Mediana de 20, 21, 22 y 24,4 (o 24,5): 21,5.
        assertTrue(offOf(listOf(m(ECMWF, 20.0), m(ICON, 21.0), m(GFS, 22.0), m(GEM, 24.4))).isEmpty())
        val v = modelVerdicts(listOf(m(ECMWF, 20.0), m(ICON, 21.0), m(GFS, 22.0), m(GEM, 24.5)))
        assertEquals(listOf(false, false, false, true), v.map { it.maxOff })
        assertFalse(v.last().rainOff)
    }

    @Test fun `nombres de cada modelo`() {
        assertEquals(
            listOf("Europeo", "Alemán", "Americano", "Francés", "Británico", "Canadiense"),
            WeatherModel.entries.map { it.country },
        )
        assertEquals(listOf("ECMWF", "ICON", "GFS", "Météo-France", "UKMO", "GEM"), WeatherModel.entries.map { it.acronym })
    }

    @Test fun `fecha larga de la hoja`() {
        assertEquals("Jueves, 5 de noviembre", longDayLabel("2026-11-05"))
    }
}
