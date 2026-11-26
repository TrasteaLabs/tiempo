package com.trastealabs.tiempo.ui

import androidx.compose.ui.Modifier
import com.trastealabs.tiempo.domain.DayMoment
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.SkyEffects
import com.trastealabs.tiempo.domain.effectsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** La lluvia contra el cristal de las tarjetas (009, P2: H8b), quieta desde el 012 P6: solo gotas fijas. */
class RainOnGlassTest {
    // Una tarjeta ancha de la principal en el OPPO (dp).
    private val w = 380f
    private val h = 300f

    @Test fun `sin lluvia no dibuja nada`() {
        for (sky in listOf(Sky.CLEAR, Sky.CLOUDY, Sky.FOG, Sky.SNOW)) {
            for (moment in DayMoment.entries) {
                assertSame(Modifier, Modifier.rainOnGlass(effectsOf(sky, moment), 1))
            }
        }
        assertSame(Modifier, Modifier.rainOnGlass(SkyEffects.NONE, 1))
        assertNotSame(Modifier, Modifier.rainOnGlass(effectsOf(Sky.RAIN, DayMoment.DAY), 1))
    }

    @Test fun `en tormenta mas gotas que con lluvia, y una tarjeta pequena lleva menos`() {
        assertEquals(16, WetGlass(w, h, storm = false, seed = 4).maxBeads)
        assertEquals(26, WetGlass(w, h, storm = true, seed = 4).maxBeads)
        assertTrue(WetGlass(170f, 90f, storm = true, seed = 4).maxBeads < 8)
    }

    @Test fun `las gotas fijas caben en la tarjeta y son siempre las mismas (012 P6)`() {
        for (seed in 1..6) {
            val glass = WetGlass(w, h, storm = seed % 2 == 0, seed = seed)
            val beads = glass.beads()
            assertTrue("seed $seed sin gotas", beads.isNotEmpty())
            assertTrue(beads.size <= glass.maxBeads)
            for (b in beads) {
                assertTrue(b.x in 6f..w - 6f && b.y in 6f..h - 4f)
                assertTrue(b.alpha in 0f..1f)
                assertTrue(b.radius in 0.8f..3.2f)
            }
            // Mismo dibujo en dos instantes: no hay reloj del que depender, ni gotas que escurren ni salpicaduras.
            assertEquals(beads, glass.beads())
            assertEquals(beads, WetGlass(w, h, storm = seed % 2 == 0, seed = seed).beads())
        }
    }

    @Test fun `cada tarjeta tiene sus gotas`() {
        assertTrue(WetGlass(w, h, storm = false, seed = 1).beads() != WetGlass(w, h, storm = false, seed = 2).beads())
    }
}
