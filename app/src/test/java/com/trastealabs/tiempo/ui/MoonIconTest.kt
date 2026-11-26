package com.trastealabs.tiempo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * H12: la luna pequeña (ficha y página del día), en espejo en el hemisferio sur. Se pinta el dibujo de `MoonIcon`
 * ([drawMoon]) en un lienzo de 96 px: `captureToImage` no llega a capturar en Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoonIconTest {
    private val px = 96

    /** Brillo medio de la mitad derecha menos el de la izquierda (0–3 por canal sumado). */
    private fun rightMinusLeft(south: Boolean, phase: Double = 0.2): Float {
        val image = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
            scale(px / 24f, pivot = Offset.Zero) { drawMoon(phase, south) }
        }
        val map = image.toPixelMap()
        fun side(from: Int, to: Int) = (from until to).flatMap { x ->
            (px / 4 until px * 3 / 4).map { y -> map[x, y].let { (it.red + it.green + it.blue) * it.alpha } }
        }.average().toFloat()
        return side(px / 2 + 2, px * 9 / 10) - side(px / 10, px / 2 - 2)
    }

    @Test fun `creciente en el norte, la mitad derecha mas clara`() {
        assertTrue(rightMinusLeft(south = false) > 0.3f)
    }

    @Test fun `creciente en el sur, la izquierda mas clara`() {
        assertTrue(rightMinusLeft(south = true) < -0.3f)
    }

    @Test fun `menguante en el sur, la derecha mas clara`() {
        assertTrue(rightMinusLeft(south = true, phase = 0.8) > 0.3f)
    }
}
