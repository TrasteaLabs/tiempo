package com.trastealabs.tiempo.ui.scene

import android.content.Context
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.domain.DayMoment
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.SkyEffects
import com.trastealabs.tiempo.domain.effectsOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La capa de luz de la principal (009, P2: H4, H6; quieta desde el 012 P6, H9): un solo instante fijo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightLayerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // 200 × 400 dp a 1 px/dp: pequeño, para que vaya rápido.
    private val w = 200
    private val h = 400
    private val clouds by lazy { PhotoCache(context).cloudLayer(h) }

    private fun layer(effects: SkyEffects) = LightLayer(effects, w.toFloat(), h.toFloat(), 1f, clouds)

    /** Lo que pinta [layer] sobre transparente, [times] veces seguidas; devuelve la última. */
    private fun render(layer: LightLayer, times: Int = 1, scroll: Float = 0f, appear: Float = 1f, enter: Float = 1f): IntArray {
        var pixels = IntArray(0)
        repeat(times) {
            val image = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
                layer.draw(this, scroll, appear, enter)
            }
            pixels = IntArray(w * h).also { image.readPixels(it) }
        }
        return pixels
    }

    private fun IntArray.painted() = count { (it ushr 24) > 2 }

    private val allEffects = Sky.entries.flatMap { sky -> DayMoment.entries.map { effectsOf(sky, it) } }.distinct()

    @Test fun `cada efecto pinta algo y NONE no pinta nada`() {
        for (effects in allEffects) {
            assertTrue("$effects no pinta", render(layer(effects)).painted() > 50)
        }
        assertEquals(0, render(layer(SkyEffects.NONE)).painted())
    }

    @Test fun `siempre el mismo dibujo, pinte cuando pinte (un solo instante fijo)`() {
        for (effects in allEffects) {
            val once = layer(effects)
            val a = render(once, times = 1)
            val b = render(once, times = 4)
            assertArrayEquals("$effects", a, b)
            // Y otra capa igual da lo mismo: no hay reloj ni estado que avance.
            assertArrayEquals("$effects", a, render(layer(effects)))
        }
    }

    @Test fun `appear funde, a cero no se ve nada y a medias no es lo mismo que entero`() {
        for (effects in allEffects) {
            val l = layer(effects)
            assertEquals("$effects", 0, render(l, appear = 0f).painted())
            val half = render(l, appear = 0.5f)
            assertFalse("$effects a medias es igual que entero", half.contentEquals(render(l)))
        }
    }

    @Test fun `el destello del sol entra desde arriba con enter`() {
        val sun = layer(effectsOf(Sky.CLEAR, DayMoment.DAY))
        val entering = render(sun, enter = 0f)
        val inPlace = render(sun, enter = 1f)
        assertTrue(entering.painted() < inPlace.painted() / 4)
        assertTrue(inPlace.painted() > w * h / 4)
    }

    @Test fun `las estrellas suben al desplazar (paralaje)`() {
        val stars = layer(effectsOf(Sky.CLEAR, DayMoment.NIGHT))
        assertFalse(render(stars).contentEquals(render(stars, scroll = 200f)))
    }

    @Test fun `ya no hay nada que dependa del tiempo`() {
        val source = java.io.File("src/main/java/com/trastealabs/tiempo/ui/scene/LightLayer.kt").readText()
        for (gone in listOf("flashAt", "enterAt", "breathAt", "seconds: Double", "still: Boolean")) {
            assertFalse("LightLayer.kt aún tiene $gone", source.contains(gone))
        }
    }
}
