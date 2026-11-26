package com.trastealabs.tiempo.ui.scene

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.domain.SkyPhoto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El trozo de la foto que se ve en una postal, en la hoja de borrar y (P3) en el widget. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoBandTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `el trozo tiene la proporcion del hueco`() {
        for (photo in SkyPhoto.entries) {
            val r = photoCrop(photo, 1188, 336)
            assertEquals(0, r.left)
            assertEquals(PhotoCache.PHOTO_WIDTH, r.right)
            assertEquals("$photo", 1188f / 336f, r.width().toFloat() / r.height(), 0.02f)
        }
        val tall = photoCrop(SkyPhoto.CLOUDS, 540, 1200)
        assertEquals(540f / 1200f, tall.width().toFloat() / tall.height(), 0.01f)
    }

    @Test fun `cae dentro de la foto con el centro arriba del todo y abajo del todo`() {
        for (photo in SkyPhoto.entries) {
            val r = photoCrop(photo, 1188, 444)
            assertTrue("$photo: $r", r.top >= 0 && r.bottom <= PhotoCache.PHOTO_HEIGHT)
        }
        // Centro en el borde de arriba (0) y en el de abajo (1): la franja se pega al borde, sin salirse.
        val top = bandRect(0f, 1188, 444)
        assertEquals(0, top.top)
        assertEquals(top.height(), top.bottom)
        val bottom = bandRect(PhotoCache.PHOTO_HEIGHT.toFloat(), 1188, 444)
        assertEquals(PhotoCache.PHOTO_HEIGHT, bottom.bottom)
        assertEquals(top.height(), bottom.height())
        // CLEAR_COOL centra en 0,14 (336 px): con un hueco alto no cabe centrada y se pega arriba.
        assertEquals(0, photoCrop(SkyPhoto.CLEAR_COOL, 1188, 800).top)
        // Un hueco tan alto como la foto entera no deja sitio para moverse.
        val whole = photoCrop(SkyPhoto.SCORCHING, 1080, PhotoCache.PHOTO_HEIGHT)
        assertEquals(0, whole.top)
        assertEquals(PhotoCache.PHOTO_HEIGHT, whole.bottom)
    }

    @Test fun `usa el centro crop de ScreenLooks`() {
        // 336 px de alto a 1188 de ancho: 403 px de foto; el centro de CLEAR_HOT (0,40) cae en 960 px.
        val r = photoCrop(SkyPhoto.CLEAR_HOT, 1188, 336)
        val center = (r.top + r.bottom) / 2f
        assertEquals(SCREEN_LOOKS.getValue(SkyPhoto.CLEAR_HOT).crop * PhotoCache.PHOTO_HEIGHT, center, 2f)
        // Y otra foto con otro centro da otra franja.
        val other = photoCrop(SkyPhoto.CLEAR_MILD, 1188, 336)
        assertTrue(other.top < r.top)
    }

    @Test fun `decodeBand devuelve el tamano pedido entre sample`() {
        val r = photoCrop(SkyPhoto.CLOUDS, 1188, 336)
        for (sample in listOf(1, 2)) {
            val band = decodeBand(context, SkyPhoto.CLOUDS, 1188, 336, sample)
            // El decodificador redondea hacia arriba con un alto impar.
            assertEquals(r.width() / sample.toFloat(), band.width.toFloat(), 1f)
            assertEquals(r.height() / sample.toFloat(), band.height.toFloat(), 1f)
        }
    }

    @Test fun `PhotoBands da la misma imagen la segunda vez y se acuerda de pocas`() = runBlocking {
        val bands = PhotoBands(context, capacity = 2)
        val a = bands.get(SkyPhoto.CLOUDS, 1188, 336)
        assertSame(a, bands.get(SkyPhoto.CLOUDS, 1188, 336))
        bands.get(SkyPhoto.FOG, 1188, 336)
        bands.get(SkyPhoto.RAIN, 1188, 336)
        assertEquals(2, bands.count)
    }
}
