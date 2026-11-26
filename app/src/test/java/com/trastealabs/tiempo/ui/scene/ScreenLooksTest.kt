package com.trastealabs.tiempo.ui.scene

import com.trastealabs.tiempo.domain.SkyPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los números que mide `tools/build_looks.py` para Ajustes y Sitios (010). */
class ScreenLooksTest {
    @Test fun `hay uno por cada una de las 14 fotos`() {
        assertEquals(14, SkyPhoto.entries.size)
        assertEquals(SkyPhoto.entries.toSet(), SCREEN_LOOKS.keys)
    }

    @Test fun `las 14 fotos llevan un velo de al menos 0,20`() {
        for ((photo, look) in SCREEN_LOOKS) {
            assertTrue("$photo: velo ${look.sceneVeil}", look.sceneVeil >= 0.20f)
            assertTrue("$photo: velo ${look.sceneVeil} pasa de 0,92", look.sceneVeil <= 0.92f)
        }
    }

    @Test fun `el texto al 80 por ciento sobre la escena llega a 4,5 en las 14`() {
        for ((photo, look) in SCREEN_LOOKS) {
            assertTrue("$photo: contraste ${look.sceneContrast}", look.sceneContrast >= 4.5f)
        }
    }

    @Test fun `el tono del cristal es una fraccion`() {
        for ((photo, look) in SCREEN_LOOKS) {
            assertTrue("$photo: tono ${look.screenTint}", look.screenTint in 0f..0.9f)
        }
    }

    /** H6: el texto de las postales (peor de la de 112 dp y la de «Donde estoy») llega a 4,5:1 sobre cada foto. */
    @Test fun `el texto de las postales llega a 4,5 en las 14`() {
        for ((photo, look) in SCREEN_LOOKS) {
            assertTrue("$photo: contraste de la postal ${look.postalContrast}", look.postalContrast >= 4.5f)
            assertTrue("$photo: velo ${look.postalVeil}", look.postalVeil in 0f..0.92f)
            assertTrue("$photo: velo de Donde estoy ${look.herePostalVeil}", look.herePostalVeil in 0f..0.92f)
        }
    }

    /** `crop` es la tabla FRANJA de la maqueta (variante-a.html). */
    @Test fun `el centro de la franja es el de la tabla FRANJA de la maqueta`() {
        val franja = mapOf(
            SkyPhoto.CLEAR_COOL to .14f, SkyPhoto.CLEAR_MILD to .18f, SkyPhoto.CLEAR_HOT to .40f, SkyPhoto.SCORCHING to .46f,
            SkyPhoto.CLOUDS to .34f, SkyPhoto.FOG to .30f, SkyPhoto.RAIN to .20f, SkyPhoto.STORM to .26f,
            SkyPhoto.SNOW to .30f, SkyPhoto.GOLDEN_CLEAR to .36f, SkyPhoto.GOLDEN_CLOUDS to .30f,
            SkyPhoto.NIGHT_CLEAR to .30f, SkyPhoto.NIGHT_CLOUDS to .26f, SkyPhoto.NIGHT_RAIN to .30f,
        )
        assertEquals(franja.keys, SCREEN_LOOKS.keys)
        for ((photo, crop) in franja) assertEquals("$photo", crop, SCREEN_LOOKS.getValue(photo).crop, 0.005f)
    }
}
