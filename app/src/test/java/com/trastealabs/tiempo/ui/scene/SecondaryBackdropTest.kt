package com.trastealabs.tiempo.ui.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.glass.GlassTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** La escena desenfocada de Ajustes y Sitios (010): foto desenfocada y velada, con su propio cristal, quieta. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecondaryBackdropTest {
    @get:Rule val rule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun luma(p: Int): Float = 0.2126f * ((p shr 16) and 0xFF) + 0.7152f * ((p shr 8) and 0xFF) + 0.0722f * (p and 0xFF)

    private fun photo(p: SkyPhoto): Bitmap {
        val options = BitmapFactory.Options().apply { inSampleSize = 8; inScaled = false }
        return BitmapFactory.decodeResource(context.resources, PhotoCache.resOf(p), options)
    }

    private fun roughness(b: Bitmap): Float {
        var sum = 0f
        var n = 0
        for (y in 0 until b.height step 3) for (x in 0 until b.width - 1 step 3) {
            sum += abs(luma(b.getPixel(x, y)) - luma(b.getPixel(x + 1, y)))
            n++
        }
        return sum / n
    }

    @Test fun `la escena es de 135 por 300 y esta desenfocada, con dos pixeles vecinos casi iguales`() {
        val sharp = photo(SkyPhoto.CLOUDS)
        val blurred = blurredScene(context, SkyPhoto.CLOUDS, veil = 0f)
        assertEquals(135, blurred.width)
        assertEquals(300, blurred.height)
        assertTrue("desenfocada ${roughness(blurred)} frente a nítida ${roughness(sharp)}", roughness(blurred) < roughness(sharp) * 0.6f)
        assertTrue("vecinos casi iguales: ${roughness(blurred)}", roughness(blurred) < 3f)
    }

    @Test fun `el velo la deja mas oscura que la foto`() {
        val plain = blurredScene(context, SkyPhoto.CLEAR_MILD, veil = 0f)
        val veiled = blurredScene(context, SkyPhoto.CLEAR_MILD, veil = SCREEN_LOOKS.getValue(SkyPhoto.CLEAR_MILD).sceneVeil)
        fun mean(b: Bitmap): Float {
            var s = 0f
            for (y in 0 until b.height step 5) for (x in 0 until b.width step 5) s += luma(b.getPixel(x, y))
            return s / ((b.height / 5) * (b.width / 5))
        }
        assertTrue("con velo ${mean(veiled)} frente a sin velo ${mean(plain)}", mean(veiled) < mean(plain) * 0.7f)
    }

    @Test fun `da su propio Backdrop y su propio tono de cristal, y el contenido los ve`() {
        var backdrop: Backdrop? = null
        var tone: GlassTone? = null
        rule.setContent {
            SecondaryBackdrop(SkyPhoto.NIGHT_CLOUDS) {
                backdrop = LocalBackdrop.current
                tone = LocalGlassTone.current
                Box {}
            }
        }
        rule.waitForIdle()
        assertNotNull(backdrop)
        assertNotNull(tone)
        assertNotSame(GlassTone.CLEAR, tone)
        rule.onNodeWithTag("secondary-backdrop").assertExists()
    }

    @Test fun `el cristal de una foto clara es mas velado que el de una oscura`() {
        val clear = screenTone(SCREEN_LOOKS.getValue(SkyPhoto.CLOUDS).screenTint)
        val night = screenTone(SCREEN_LOOKS.getValue(SkyPhoto.NIGHT_CLEAR).screenTint)
        assertTrue(clear.bottom.alpha > night.bottom.alpha)
    }

    @Test fun `esta quieta, sin reloj ni capa de luz`() {
        rule.setContent { SecondaryBackdrop(SkyPhoto.FOG) { Box {} } }
        rule.waitForIdle()
        rule.onNodeWithTag("sky-moving").assertDoesNotExist()
        val a = blurredScene(context, SkyPhoto.FOG, 0.5f)
        val b = blurredScene(context, SkyPhoto.FOG, 0.5f)
        for (y in 0 until a.height step 17) for (x in 0 until a.width step 13) assertEquals(a.getPixel(x, y), b.getPixel(x, y))
    }

    @Test fun `la segunda vez que se pide la misma escena no se vuelve a calcular`() {
        SecondaryScenes.clear()
        val before = SecondaryScenes.computed
        val a = SecondaryScenes.get(context, SkyPhoto.CLOUDS, 0.3f)
        assertEquals(before + 1, SecondaryScenes.computed)
        val b = SecondaryScenes.get(context, SkyPhoto.CLOUDS, 0.3f)
        assertEquals("la segunda vez no recalcula", before + 1, SecondaryScenes.computed)
        assertSame(a, b)
        SecondaryScenes.get(context, SkyPhoto.FOG, 0.3f)
        assertEquals("otra foto sí se calcula", before + 2, SecondaryScenes.computed)
    }

    @Test fun `abrir la pantalla secundaria dos veces solo calcula la escena una`() {
        SecondaryScenes.clear()
        val before = SecondaryScenes.computed
        var open by mutableStateOf(true)
        rule.setContent { if (open) SecondaryBackdrop(SkyPhoto.SNOW) { Box {} } }
        rule.waitForIdle()
        open = false
        rule.waitForIdle()
        open = true
        rule.waitForIdle()
        assertEquals(before + 1, SecondaryScenes.computed)
    }
}
