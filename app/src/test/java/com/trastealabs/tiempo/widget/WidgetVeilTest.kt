package com.trastealabs.tiempo.widget

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El velo del widget (010, P3): el `solve()` de la maqueta (Robolectric solo por `RectF`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetVeilTest {
    private val d = 2f
    private val w = 100
    private val h = 60
    private val texts = listOf(TextSample(RectF(20f, 10f, 80f, 40f), 0.92f))
    private val layers = VEIL_LAYERS.getValue(WidgetLayout.LARGE)

    private fun solid(color: Int) = IntArray(w * h) { color }

    @Test fun `foto negra, velo 0`() {
        val s = solveVeil(solid(0xFF000000.toInt()), w, h, d, texts, layers)
        assertEquals(0f, s.k, 0f)
        assertTrue(s.minContrast > 10f)
    }

    @Test fun `foto blanca, el velo que da el contraste buscado`() {
        val s = solveVeil(solid(0xFFFFFFFF.toInt()), w, h, d, texts, layers)
        assertTrue("k=${s.k}", s.k > 0.3f && s.k <= 1f)
        assertEquals(VEIL_TARGET, s.minContrast, 0.15f)
        assertTrue(s.minContrast >= 4.5f)
    }

    @Test fun `sin textos o sin capas no hay velo`() {
        assertEquals(0f, solveVeil(solid(-1), w, h, d, emptyList(), layers).k, 0f)
        assertEquals(0f, solveVeil(solid(-1), w, h, d, texts, emptyList()).k, 0f)
    }

    @Test fun `las capas de cada tamano son las de la maqueta`() {
        assertEquals(listOf(VeilLayer(VeilSide.TOP, 86f, 10f, 0.8f), VeilLayer(VeilSide.BOTTOM, 56f, 30f)), VEIL_LAYERS[WidgetLayout.LARGE])
        assertEquals(listOf(VeilLayer(VeilSide.ALL, 0f, 0f)), VEIL_LAYERS[WidgetLayout.MEDIUM])
        assertEquals(listOf(VeilLayer(VeilSide.ALL, 0f, 0f)), VEIL_LAYERS[WidgetLayout.SMALL])
    }

    @Test fun `018 P3 la frase del 4x2 (de 30 a 86 dp) cae entera en el tramo lleno de la capa de arriba, sin rampa`() {
        val top = VEIL_LAYERS.getValue(WidgetLayout.LARGE).first { it.side == VeilSide.TOP }
        assertEquals(86f, top.fullDp, 0f)
        val only = listOf(top)
        val reference = layerAlpha(only, 100f, 30f, 328f, 146f, 1f)
        var y = 30f
        while (y <= 86f) {
            assertEquals("y=$y", reference, layerAlpha(only, 100f, y, 328f, 146f, 1f), 1e-6f)
            y += 1f
        }
    }

    @Test fun `layerAlpha es pleno en el tramo lleno, se desvanece y llega a 0`() {
        val top = listOf(VeilLayer(VeilSide.TOP, 86f, 10f, 0.8f))
        assertEquals(0.8f, layerAlpha(top, 50f, 40f, 328f, 146f, 1f), 1e-5f)
        assertEquals(0.4f, layerAlpha(top, 50f, 91f, 328f, 146f, 1f), 1e-5f)
        assertEquals(0f, layerAlpha(top, 50f, 100f, 328f, 146f, 1f), 1e-5f)
        // Dos capas se componen: 1 − (1 − a)(1 − b).
        val both = listOf(VeilLayer(VeilSide.TOP, 100f, 0f, 1f), VeilLayer(VeilSide.BOTTOM, 100f, 0f, 1f))
        assertEquals(1f - (1f - 0.5f) * (1f - 0.5f), layerAlpha(both, 10f, 50f, 100f, 100f, 0.5f), 1e-5f)
    }

    @Test fun `el borde del velo no tiene escalon, ni en el 4x2 ni en el 4x1 (017 H4b)`() {
        // La linea que se veia era la pendiente que salta de 0 a a/fade justo donde acaba el tramo lleno y donde acaba el fundido.
        for ((layout, wDp, hDp) in listOf(Triple(WidgetLayout.LARGE, 328f, 146f), Triple(WidgetLayout.MEDIUM, 313f, 82f))) {
            val ls = VEIL_LAYERS.getValue(layout)
            val horizontal = layout == WidgetLayout.LARGE
            val len = if (horizontal) hDp else wDp
            var prev = layerAlpha(ls, if (horizontal) 10f else 0f, if (horizontal) 0f else 10f, wDp, hDp, 1f)
            var prevSlope = 0f
            var maxJump = 0f
            var maxSlope = 0f
            var pos = 1f
            while (pos <= len) {
                val a = if (horizontal) layerAlpha(ls, 164f, pos, wDp, hDp, 1f) else layerAlpha(ls, pos, 41f, wDp, hDp, 1f)
                val slope = a - prev
                maxJump = maxOf(maxJump, kotlin.math.abs(slope - prevSlope))
                maxSlope = maxOf(maxSlope, kotlin.math.abs(slope))
                prev = a
                prevSlope = slope
                pos += 1f
            }
            // Con la rampa lineal de 14 dp el salto de pendiente era de ~0,066 por dp; ahora, menos de la mitad.
            assertTrue("$layout salto de pendiente $maxJump", maxJump < 0.03f)
            assertTrue("$layout pendiente $maxSlope", maxSlope < 0.06f)
        }
    }

    @Test fun `las paradas del degradado siguen la misma rampa que layerAlpha y no hay dos iguales`() {
        val (stops, colors) = veilStops(0.4f, 0.8f)
        assertEquals(0f, stops.first(), 0f)
        assertEquals(1f, stops.last(), 1e-6f)
        assertEquals(0, android.graphics.Color.alpha(colors.last()))
        assertEquals(204, android.graphics.Color.alpha(colors.first()))
        for (i in 1 until stops.size) assertTrue(stops[i] > stops[i - 1])
        for (i in 1 until colors.size) assertTrue(android.graphics.Color.alpha(colors[i]) <= android.graphics.Color.alpha(colors[i - 1]))
        // En el medio de la rampa, la mitad.
        val mid = stops.indexOfFirst { it >= 0.7f - 1e-4f }
        assertEquals(102f, android.graphics.Color.alpha(colors[mid]).toFloat(), 2f)
    }

    @Test fun `el alfa no pasa de 0,92`() {
        assertEquals(VEIL_MAX, layerAlpha(listOf(VeilLayer(VeilSide.ALL, 0f, 0f)), 0f, 0f, 10f, 10f, 1f), 1e-5f)
    }

    @Test fun `mide el percentil 1 y no la media`() {
        // Casi toda la foto es negra, pero una tira blanca del 5 % del texto: la media no pediría velo; el percentil 1, sí.
        val px = solid(0xFF000000.toInt())
        for (y in 10 until 40) for (x in 20 until 23) px[y * w + x] = 0xFFFFFFFF.toInt()
        val s = solveVeil(px, w, h, d, texts, layers)
        assertTrue("k=${s.k}", s.k > 0.2f)
        assertTrue(s.minContrast >= 4.5f)
    }

    @Test fun `la pildora de sin datos sube su alfa sobre una foto clara y se queda en 0,42 sobre una oscura`() {
        val rect = RectF(20f, 10f, 80f, 40f)
        assertEquals(0.42f, solvePill(solid(0xFF000000.toInt()), w, h, d, rect, 0.42f).k, 0f)
        val bright = solvePill(solid(0xFFFFFFFF.toInt()), w, h, d, rect, 0.42f)
        assertTrue(bright.k > 0.42f && bright.k <= VEIL_MAX)
        assertTrue(bright.minContrast >= 4.5f)
    }
}
