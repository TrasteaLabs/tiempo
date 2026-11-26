package com.trastealabs.tiempo.ui.scene

import com.trastealabs.tiempo.domain.SkyPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** H5: el texto blanco llega a 4,5:1 en cada foto, sobre el cristal de las tarjetas y en la cabecera con su velo. */
class PhotoLooksTest {
    @Test fun `las 14 fotos tienen su aspecto`() {
        assertEquals(SkyPhoto.entries.toSet(), PHOTO_LOOKS.keys)
    }

    @Test fun `contraste de las tarjetas y de la cabecera de al menos 4,5`() {
        for ((photo, look) in PHOTO_LOOKS) {
            assertTrue("$photo tarjetas ${look.cardContrast}", look.cardContrast >= 4.5f)
            assertTrue("$photo cabecera ${look.headerContrast}", look.headerContrast >= 4.5f)
        }
    }

    @Test fun `velos dentro de lo que pinta la maqueta`() {
        for ((photo, look) in PHOTO_LOOKS) {
            assertTrue("$photo glassTint ${look.glassTint}", look.glassTint == 0f || look.glassTint in 0.1f..0.8f)
            assertTrue("$photo headerVeil ${look.headerVeil}", look.headerVeil in 0f..0.7f)
        }
    }

    @Test fun `de noche el cristal es el claro o casi`() {
        assertEquals(0f, PHOTO_LOOKS.getValue(SkyPhoto.NIGHT_CLEAR).glassTint)
        for (photo in listOf(SkyPhoto.NIGHT_CLOUDS, SkyPhoto.NIGHT_RAIN)) {
            assertTrue(photo.name, PHOTO_LOOKS.getValue(photo).glassTint <= 0.1f)
        }
    }
}
