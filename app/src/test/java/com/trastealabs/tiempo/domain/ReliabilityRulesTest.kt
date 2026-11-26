package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** H8 (ritmo) y H6 (validez), con `now` y zona inventados. */
class ReliabilityRulesTest {
    private val zone: ZoneId = TestData.zone
    private val here = LatLon(40.0, -3.0)

    /** Grados de latitud que son [km] kilómetros (R = 6371 km). */
    private fun north(km: Double) = LatLon(here.lat + km / 111.19492664455873, here.lon)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toEpochSecond()

    private fun stored(fetchedAt: Long, position: LatLon = here, failedAt: Long? = null) =
        StoredReliability(Reliability(position, zone.id, fetchedAt, emptyList()), failedAt)

    @Test fun `de dia toca con 3 horas o mas`() {
        val last = stored(at(2026, 11, 4, 7, 25))
        assertTrue(reliabilityDue(last, here, at(2026, 11, 4, 10, 30), zone))
        assertFalse(reliabilityDue(last, here, at(2026, 11, 4, 10, 0), zone))
    }

    @Test fun `de madrugada solo toca con 6 horas o mas`() {
        val last = stored(at(2026, 11, 3, 23, 30))
        assertFalse(reliabilityDue(last, here, at(2026, 11, 4, 5, 0), zone))
        assertTrue(reliabilityDue(last, here, at(2026, 11, 4, 6, 0), zone))
    }

    @Test fun `a las 07 00 ya vale el ritmo de dia`() {
        val last = stored(at(2026, 11, 4, 4, 0))
        assertFalse(reliabilityDue(last, here, at(2026, 11, 4, 6, 59), zone))
        assertTrue(reliabilityDue(last, here, at(2026, 11, 4, 7, 0), zone))
    }

    @Test fun `el dia del cambio de hora cuenta las horas reales`() {
        // Sábado 24 a las 23:30 (CEST) → domingo 25 a las 05:00 (CET): 6 h 30 min reales, aunque el reloj diga 5 h 30
        val last = stored(at(2026, 10, 24, 23, 30))
        val now = at(2026, 10, 25, 5, 0)
        assertEquals(6 * 3600L + 1800, now - last.reliability!!.fetchedAt)
        assertTrue(reliabilityDue(last, here, now, zone))
    }

    @Test fun `si nunca se ha pedido toca`() {
        assertTrue(reliabilityDue(StoredReliability(), here, at(2026, 11, 4, 3, 0), zone))
    }

    @Test fun `en otro sitio a mas de 5 km toca aunque sea reciente`() {
        val last = stored(at(2026, 11, 4, 10, 0), position = north(5.1))
        assertTrue(reliabilityDue(last, here, at(2026, 11, 4, 10, 5), zone))
        val near = stored(at(2026, 11, 4, 10, 0), position = north(4.9))
        assertFalse(reliabilityDue(near, here, at(2026, 11, 4, 10, 5), zone))
    }

    @Test fun `tras un fallo no se reintenta hasta pasada 1 hora`() {
        val now = at(2026, 11, 4, 12, 0)
        val old = stored(at(2026, 11, 4, 7, 0), failedAt = now - 40 * 60)
        assertFalse(reliabilityDue(old, here, now, zone))
        assertTrue(reliabilityDue(old.copy(lastFailedAt = now - 61 * 60), here, now, zone))
        // también sin ninguna guardada
        assertFalse(reliabilityDue(StoredReliability(lastFailedAt = now - 40 * 60), here, now, zone))
        assertTrue(reliabilityDue(StoredReliability(lastFailedAt = now - 61 * 60), here, now, zone))
    }

    @Test fun `la distancia entre dos puntos es la del circulo maximo`() {
        assertEquals(5.1, distanceKm(here, north(5.1)), 0.001)
        // Madrid–Barcelona, unos 505 km
        assertEquals(505.0, distanceKm(LatLon(40.4168, -3.7038), LatLon(41.3874, 2.1686)), 5.0)
    }

    @Test fun `sin fiabilidad guardada no hay valida`() {
        val now = at(2026, 11, 4, 12, 0)
        assertNull(validReliability(null, snapshotAt(now), now))
    }

    @Test fun `con mas de 12 horas no vale`() {
        val now = at(2026, 11, 4, 12, 0)
        val r = stored(now - 12 * 3600 - 60).reliability
        assertNull(validReliability(r, snapshotAt(now), now))
    }

    @Test fun `de otro sitio a mas de 5 km no vale`() {
        val now = at(2026, 11, 4, 12, 0)
        val r = stored(now - 3600, position = north(5.1)).reliability
        assertNull(validReliability(r, snapshotAt(now), now))
    }

    @Test fun `de 11 horas y a 4,9 km vale la misma`() {
        val now = at(2026, 11, 4, 12, 0)
        val r = stored(now - 11 * 3600, position = north(4.9)).reliability
        assertSame(r, validReliability(r, snapshotAt(now), now))
    }

    private fun snapshotAt(now: Long) = TestData.snapshot(now)
}
