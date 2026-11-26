package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindInfoTest {
    private fun conditions(kmh: Double?, deg: Int? = 225, gusts: Double? = 45.0) =
        CurrentConditions(15.0, 14.0, 3, true, windKmh = kmh, windGustsKmh = gusts, windDirectionDeg = deg)

    @Test fun `velocidad, direccion y rachas (H10)`() {
        assertEquals(WindInfo("12 km/h", "Suroeste · rachas 45"), windInfo(conditions(12.0)))
    }

    @Test fun `por debajo de 5 km por hora dice Calma`() {
        assertEquals(WindInfo("3 km/h", "Calma"), windInfo(conditions(3.0)))
    }

    @Test fun `sin rachas solo la direccion`() {
        assertEquals(WindInfo("12 km/h", "Suroeste"), windInfo(conditions(12.0, gusts = null)))
    }

    @Test fun `sin direccion solo las rachas`() {
        assertEquals(WindInfo("12 km/h", "Rachas 45"), windInfo(conditions(12.0, deg = null)))
    }

    @Test fun `sin direccion ni rachas solo la velocidad`() {
        assertEquals(WindInfo("12 km/h", null), windInfo(conditions(12.0, deg = null, gusts = null)))
    }

    @Test fun `sin velocidad no hay ficha (caso limite)`() {
        assertNull(windInfo(conditions(null)))
    }

    @Test fun `la velocidad se redondea como se ve`() {
        assertEquals("13 km/h", windInfo(conditions(12.6))!!.speed)
    }

    @Test fun `ocho rumbos con sus limites`() {
        assertEquals("Norte", compassName(0))
        assertEquals("Norte", compassName(22))
        assertEquals("Noreste", compassName(23))
        assertEquals("Este", compassName(90))
        assertEquals("Sureste", compassName(135))
        assertEquals("Sur", compassName(180))
        assertEquals("Suroeste", compassName(225))
        assertEquals("Oeste", compassName(270))
        assertEquals("Noroeste", compassName(337))
        assertEquals("Norte", compassName(338))
        assertEquals("Norte", compassName(359))
        assertEquals("Norte", compassName(360))
    }
}
