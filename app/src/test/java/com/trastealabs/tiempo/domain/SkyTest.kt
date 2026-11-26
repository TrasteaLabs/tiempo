package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SkyTest {
    @Test fun `codigos WMO representativos`() {
        mapOf(
            0 to Sky.CLEAR,
            1 to Sky.CLEAR,
            2 to Sky.PARTLY_CLOUDY,
            3 to Sky.CLOUDY,
            45 to Sky.FOG,
            48 to Sky.FOG,
            51 to Sky.DRIZZLE,
            57 to Sky.DRIZZLE,
            61 to Sky.RAIN,
            67 to Sky.RAIN,
            80 to Sky.RAIN,
            82 to Sky.RAIN,
            71 to Sky.SNOW,
            77 to Sky.SNOW,
            86 to Sky.SNOW,
            95 to Sky.STORM,
            99 to Sky.STORM,
        ).forEach { (code, sky) -> assertEquals("WMO $code", sky, skyOf(code)) }
    }

    @Test fun `un codigo desconocido sale nublado`() {
        assertEquals(Sky.CLOUDY, skyOf(42))
    }
}
