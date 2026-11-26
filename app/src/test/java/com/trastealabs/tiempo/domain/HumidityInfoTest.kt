package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HumidityInfoTest {
    private val fog = 45
    private val cloudy = 3

    private fun conditions(pct: Int?, code: Int = cloudy, dew: Double? = 10.2, visibility: Double? = null) =
        CurrentConditions(15.0, 14.0, code, true, humidityPct = pct, dewPointC = dew, visibilityM = visibility)

    @Test fun `porcentaje y punto de rocio (H11)`() {
        assertEquals(HumidityInfo("68 %", "Rocío 10°"), humidityInfo(conditions(68)))
    }

    @Test fun `rocio bajo cero`() {
        assertEquals("Rocío -2°", humidityInfo(conditions(88, dew = -2.3))!!.detail)
    }

    @Test fun `con niebla, la visibilidad en metros de cien en cien`() {
        assertEquals(HumidityInfo("96 %", "Visibilidad 300 m"), humidityInfo(conditions(96, fog, visibility = 320.0)))
    }

    @Test fun `con niebla y mas de un kilometro, en kilometros`() {
        assertEquals("Visibilidad 2 km", humidityInfo(conditions(96, fog, visibility = 2400.0))!!.detail)
        assertEquals("Visibilidad 1 km", humidityInfo(conditions(96, fog, visibility = 960.0))!!.detail)
    }

    @Test fun `con niebla muy densa no dice 0 m`() {
        assertEquals("Visibilidad < 100 m", humidityInfo(conditions(100, fog, visibility = 40.0))!!.detail)
        assertEquals("Visibilidad 100 m", humidityInfo(conditions(100, fog, visibility = 60.0))!!.detail)
    }

    @Test fun `niebla sin visibilidad ensena el rocio (caso limite)`() {
        assertEquals("Rocío 10°", humidityInfo(conditions(96, fog, visibility = null))!!.detail)
    }

    @Test fun `sin niebla la visibilidad no sale`() {
        assertEquals("Rocío 10°", humidityInfo(conditions(70, cloudy, visibility = 320.0))!!.detail)
    }

    @Test fun `sin rocio solo el porcentaje`() {
        assertEquals(HumidityInfo("68 %", null), humidityInfo(conditions(68, dew = null)))
    }

    @Test fun `sin humedad no hay ficha (caso limite)`() {
        assertNull(humidityInfo(conditions(null)))
    }
}
