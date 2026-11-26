package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

class WarningBannerTextTest {
    private val zone = TestData.zone
    private val now = TestData.at(2025, 6, 10, 10, 0)

    private fun warning(level: WarningLevel, phenomenon: String, onset: Long, expires: Long) =
        Warning("x", level, phenomenon, "Llanura de Ejemplo", onset, expires, "40%-70%", "", null, TestData.at(2025, 6, 10, 9, 30))

    @Test fun `aviso de hoy`() {
        val w = warning(WarningLevel.ORANGE, "tormentas", TestData.at(2025, 6, 10, 16), TestData.at(2025, 6, 10, 22))
        assertEquals("Aviso naranja · tormentas", warningTitle(w))
        assertEquals("hoy 16:00–22:00", warningWhenText(w, now, zone))
    }

    @Test fun `si empieza manana dice manana`() {
        val w = warning(WarningLevel.YELLOW, "lluvias", TestData.at(2025, 6, 11, 6), TestData.at(2025, 6, 11, 12))
        assertEquals("Aviso amarillo · lluvias", warningTitle(w))
        assertEquals("mañana 06:00–12:00", warningWhenText(w, now, zone))
    }

    @Test fun `si acaba otro dia lo dice`() {
        val w = warning(WarningLevel.RED, "nevadas", TestData.at(2025, 6, 10, 22), TestData.at(2025, 6, 11, 8))
        assertEquals("Aviso rojo · nevadas", warningTitle(w))
        assertEquals("hoy 22:00–mañana 08:00", warningWhenText(w, now, zone))
    }

    @Test fun `hoy y manana dependen del dia local de now`() {
        val w = warning(WarningLevel.YELLOW, "lluvias", TestData.at(2025, 6, 11, 0, 30), TestData.at(2025, 6, 11, 6))
        assertEquals("Aviso amarillo · lluvias", warningTitle(w))
        assertEquals("mañana 00:30–06:00", warningWhenText(w, TestData.at(2025, 6, 10, 23, 59), zone))
        assertEquals("hoy 00:30–06:00", warningWhenText(w, TestData.at(2025, 6, 11, 0, 1), zone))
    }

    @Test fun `la hoja da la vigencia y la fuente con fecha`() {
        val w = warning(WarningLevel.ORANGE, "tormentas", TestData.at(2025, 6, 10, 16), TestData.at(2025, 6, 10, 22))
        assertEquals("martes 10 jun, de 16:00 a 22:00", warningPeriodText(w, zone))
        assertEquals("Fuente: AEMET · emitido hoy a las 09:30", warningSourceText(w, now, zone))
        assertEquals("Fuente: AEMET · emitido el 10 jun a las 09:30", warningSourceText(w, TestData.at(2025, 6, 12, 9), zone))

        val night = warning(WarningLevel.ORANGE, "tormentas", TestData.at(2025, 6, 10, 22), TestData.at(2025, 6, 11, 8))
        assertEquals("del martes 10 jun a las 22:00 al miércoles 11 jun a las 08:00", warningPeriodText(night, zone))
    }
}
