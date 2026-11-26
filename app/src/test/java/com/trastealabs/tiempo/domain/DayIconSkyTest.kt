package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

class DayIconSkyTest {
    private val date = "2025-06-10"

    /** 24 horas del día; [codeAt] da el código WMO de cada hora local. */
    private fun day(codeAt: (Int) -> Int): List<HourPoint> = (0 until 24).map { h ->
        HourPoint(TestData.at(2025, 6, 10, h), 20.0, 0, 0.0, codeAt(h), h in 7..20)
    }

    private val clear = 0
    private val rain = 63
    private val storm = 95
    private val cloudy = 3

    @Test fun `lluvia solo a las 03 00 y despejado de 06 a 18 sale despejado (H4)`() {
        val hours = day { h -> if (h == 3) rain else clear }
        assertEquals(Sky.CLEAR, dayIconSky(date, hours, TestData.zone))
    }

    @Test fun `lluvia de 14 a 17 sale lluvia (H4)`() {
        val hours = day { h -> if (h in 14..17) rain else clear }
        assertEquals(Sky.RAIN, dayIconSky(date, hours, TestData.zone))
    }

    @Test fun `una sola hora de lluvia por el dia no cuenta`() {
        val hours = day { h -> if (h == 12) rain else clear }
        assertEquals(Sky.CLEAR, dayIconSky(date, hours, TestData.zone))
    }

    @Test fun `con lluvia y tormenta gana la mas grave`() {
        val hours = day { h -> when (h) { in 10..13 -> rain; 15 -> storm; else -> clear } }
        assertEquals(Sky.STORM, dayIconSky(date, hours, TestData.zone))
    }

    @Test fun `sin precipitacion sale el cielo mas frecuente entre las 06 y las 18`() {
        val hours = day { h -> if (h in 6..12) cloudy else clear }
        assertEquals(Sky.CLOUDY, dayIconSky(date, hours, TestData.zone))
    }

    @Test fun `las 18 00 ya no cuentan`() {
        val hours = day { h -> if (h in 18..19) rain else clear }
        assertEquals(Sky.CLEAR, dayIconSky(date, hours, TestData.zone))
    }

    @Test fun `solo cuentan las horas de ese dia`() {
        val next = (0 until 24).map { h -> HourPoint(TestData.at(2025, 6, 11, h), 20.0, 0, 0.0, rain, true) }
        val hours = day { clear } + next
        assertEquals(Sky.CLEAR, dayIconSky(date, hours, TestData.zone))
    }
}
