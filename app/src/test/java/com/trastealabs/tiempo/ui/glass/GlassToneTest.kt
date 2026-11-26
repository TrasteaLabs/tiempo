package com.trastealabs.tiempo.ui.glass

import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.scene.PHOTO_LOOKS
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** H5 (009): un cristal por foto; el velado lleva sombra en el texto. */
class GlassToneTest {
    // ---------- 009: un cristal por foto (H5) ----------

    @Test fun `con la foto clara el cristal es el de siempre y sin sombra`() {
        for (photo in SkyPhoto.entries.filter { PHOTO_LOOKS.getValue(it).glassTint == 0f }) {
            assertSame(photo.name, GlassTone.CLEAR, GlassTone.forPhoto(photo))
            assertNull(photo.name, glassTextShadow(GlassTone.forPhoto(photo)))
        }
    }

    @Test fun `con velo, tinta de glassTint menos y mas 0,06 como glassVars`() {
        val ink = Color(16, 22, 46)
        for (photo in SkyPhoto.entries) {
            val tint = PHOTO_LOOKS.getValue(photo).glassTint
            if (tint == 0f) continue
            val tone = GlassTone.forPhoto(photo)
            assertEquals(photo.name, ink.copy(alpha = maxOf(0.04f, tint - 0.06f)), tone.top)
            assertEquals(photo.name, ink.copy(alpha = tint + 0.06f), tone.bottom)
            assertNotNull(photo.name, glassTextShadow(tone))
            assertSame("el mismo objeto cada vez", tone, GlassTone.forPhoto(photo))
        }
    }
}
