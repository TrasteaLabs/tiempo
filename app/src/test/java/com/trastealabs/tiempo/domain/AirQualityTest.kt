package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** H1 y H3: cuándo se pide el aire y qué se guarda. */
class AirQualityTest {
    private val here = LatLon(40.0, -3.0)
    private val near = LatLon(40.03, -3.0)      // ~3,3 km
    private val far = LatLon(40.1, -3.0)        // ~11 km
    private val now = at(2026, 11, 4, 12, 0)

    private fun air(position: LatLon = here, fetchedAt: Long = now) = AirQuality(position, fetchedAt, emptyList())

    @Test fun `sin aire guardado toca pedirlo`() {
        assertTrue(airDue(null, here, now))
    }

    @Test fun `de otro sitio toca, del mismo sitio cercano no`() {
        assertTrue(airDue(air(far), here, now))
        assertFalse(airDue(air(near), here, now))
    }

    @Test fun `con 2 h 59 min no toca y con 3 h si`() {
        assertFalse(airDue(air(fetchedAt = now - (2 * 3600 + 59 * 60)), here, now))
        assertTrue(airDue(air(fetchedAt = now - 3 * 3600), here, now))
    }

    @Test fun `con la edad negativa (reloj atrasado) toca`() {
        assertTrue(airDue(air(fetchedAt = now + 60), here, now))
    }

    @Test fun `keptAir se queda el nuevo si llego`() {
        val fresh = air(fetchedAt = now)
        assertEquals(fresh, keptAir(fresh, air(fetchedAt = now - 9000), here))
        assertEquals(fresh, keptAir(fresh, null, here))
    }

    @Test fun `keptAir sin nuevo se queda el anterior del mismo sitio`() {
        val previous = air(near, now - 9000)
        assertEquals(previous, keptAir(null, previous, here))
    }

    @Test fun `keptAir sin nuevo y el anterior de otro sitio, nada`() {
        assertNull(keptAir(null, air(far), here))
        assertNull(keptAir(null, null, here))
    }
}
