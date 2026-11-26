package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.TestData.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** La postal de la pantalla Sitios (006). */
class PlaceCardsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val now = at(2026, 11, 4, 17, 20)
    private val ref = PlaceRef.Saved("p1")

    private fun stored(fetchedAt: Long = now - 600, zoneId: String = "Europe/Madrid") =
        StoredState(TestData.snapshot(now, currentC = 21.6, fetchedAt = fetchedAt).copy(zoneId = zoneId), false)

    @Test fun `con datos trae foto, cielo, temperatura y la maxima y minima de hoy`() {
        val st = stored()
        val card = placeCard(ref, "Villa Ejemplo", st, now, madrid, shown = false, noPermission = false)
        assertEquals(skyPhotoNow(st.snapshot!!, now), card.photo)
        assertEquals(skyOf(st.snapshot!!.current.weatherCode), card.sky)
        assertEquals(22, card.temp)
        assertEquals(18, card.max)   // daily[0] de TestData: 18 / 9
        assertEquals(9, card.min)
        assertNull(card.ageLabel)
        assertNull(card.localTime)
        assertFalse(card.isShown)
        assertFalse(card.noPermission)
    }

    @Test fun `sin datos no hay foto`() {
        val card = placeCard(ref, "Villa Nueva", StoredState(null, true), now, madrid, shown = true, noPermission = false)
        assertNull(card.photo)
        assertNull(card.temp)
        assertEquals("Villa Nueva", card.name)
        assertTrue(card.isShown)
    }

    @Test fun `donde estoy sin permiso`() {
        val card = placeCard(PlaceRef.Here, "Donde estoy", StoredState(null, false), now, madrid, shown = true, noPermission = true)
        assertTrue(card.noPermission)
        assertNull(card.photo)
    }

    @Test fun `sin permiso no hay foto aunque queden datos viejos`() {
        val card = placeCard(PlaceRef.Here, "Donde estoy", stored(), now, madrid, shown = true, noPermission = true)
        assertNull(card.photo)
    }

    /** H5: la foto de la postal es la de la principal (misma regla) de día, en la hora dorada, de noche y en los extremos. */
    @Test fun `la foto es la de la principal de dia, en la hora dorada, de noche, a menos 5 y a 44`() {
        val cases = listOf(
            "día" to at(2026, 11, 4, 12, 0),
            "hora dorada" to at(2026, 11, 4, 17, 20),
            "noche" to at(2026, 11, 4, 23, 30),
        )
        for ((name, t) in cases) {
            val st = StoredState(TestData.snapshot(t, currentC = 21.6, fetchedAt = t - 60), false)
            for (r in listOf(ref, PlaceRef.Here)) {
                assertEquals(name, skyPhotoNow(st.snapshot!!, t), placeCard(r, "X", st, t, madrid, false, false).photo)
            }
        }
        val noon = at(2026, 11, 4, 12, 0)
        for (c in listOf(-5.0, 44.0)) {
            val st = StoredState(TestData.snapshot(noon, currentC = c, fetchedAt = noon - 60), false)
            val card = placeCard(ref, "X", st, noon, madrid, false, false)
            assertEquals(skyPhotoNow(st.snapshot!!, noon), card.photo)
            assertNotNull("a $c °C hay foto", card.photo)
        }
    }

    @Test fun `con mas de 30 min dice de cuando es`() {
        val card = placeCard(ref, "X", stored(fetchedAt = at(2026, 11, 4, 15, 10)), now, madrid, false, false)
        assertEquals("Datos de las 15:10", card.ageLabel)
    }

    @Test fun `la maxima y minima son las de hoy en la zona del sitio`() {
        // 00:30 del 5 en Madrid = 23:30 del 4 en Canarias: allí aún es el día 4.
        val late = at(2026, 11, 5, 0, 30)
        val canary = StoredState(TestData.snapshot(now).copy(zoneId = "Atlantic/Canary"), false)
        val card = placeCard(ref, "X", canary, late, madrid, false, false)
        assertEquals(18, card.max)
        assertEquals("23:30", card.localTime)
    }

    @Test fun `la hora de alli solo con otro desfase`() {
        assertEquals("16:20", placeCard(ref, "X", stored(zoneId = "Atlantic/Canary"), now, madrid, false, false).localTime)
        assertNull(placeCard(ref, "X", stored(zoneId = "Europe/Paris"), now, madrid, false, false).localTime)
    }
}
