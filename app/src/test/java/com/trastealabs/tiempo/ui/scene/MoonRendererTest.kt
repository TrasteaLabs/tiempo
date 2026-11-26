package com.trastealabs.tiempo.ui.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** H12: la luna de foto, con un mapa inventado (gris liso, o de dos colores por longitud), a 128 px. */
class MoonRendererTest {
    private val size = 128
    private val c = size / 2.0
    private val r = moonRadius(size).toDouble()

    private val gray = 0xFFB4B4B4.toInt()
    private val face = moonFace(IntArray(64 * 32) { gray }, 64, 32, size)

    private fun alpha(p: Int) = (p ushr 24) / 255.0

    /** Los píxeles del disco: (x, y, nx). */
    private fun disc(inside: Double = 1.0): List<Triple<Int, Int, Double>> =
        (0 until size).flatMap { y ->
            (0 until size).mapNotNull { x ->
                val nx = (x + 0.5 - c) / r
                val ny = (y + 0.5 - c) / r
                if (nx * nx + ny * ny < inside * inside) Triple(x, y, nx) else null
            }
        }

    @Test fun `fuera del disco, alfa 0`() {
        for (phase in listOf(0.0, 0.25, 0.5, 0.84)) {
            val lit = litMoon(face, phase, south = false)
            for (y in 0 until size) for (x in 0 until size) {
                val nx = (x + 0.5 - c) / r
                val ny = (y + 0.5 - c) / r
                if (nx * nx + ny * ny >= 1.01) assertEquals("$phase ($x,$y)", 0, lit[y * size + x] ushr 24)
            }
            // Las esquinas, siempre.
            assertEquals(0, lit[0] ushr 24)
        }
    }

    @Test fun `luna nueva, ningun pixel del disco pasa del 5 por ciento`() {
        for (phase in listOf(0.0, 1.0)) {
            val lit = litMoon(face, phase, south = false)
            val max = lit.maxOf { alpha(it) }
            assertTrue("$phase: $max", max <= 0.05)
        }
    }

    @Test fun `llena, el 97 por ciento del disco o mas por encima del 90 por ciento`() {
        val lit = litMoon(face, 0.5, south = false)
        val pixels = disc()
        val bright = pixels.count { (x, y) -> alpha(lit[y * size + x]) > 0.9 }
        assertTrue("$bright de ${pixels.size}", bright >= 0.97 * pixels.size)
    }

    @Test fun `cuarto creciente, la mitad derecha iluminada y la izquierda a oscuras`() {
        val lit = litMoon(face, 0.25, south = false)
        disc(inside = 0.95).filter { abs(it.third) > 0.1 }.forEach { (x, y, nx) ->
            val a = alpha(lit[y * size + x])
            if (nx > 0) assertTrue("derecha ($x,$y) $a", a > 0.5) else assertTrue("izquierda ($x,$y) $a", a < 0.06)
        }
    }

    @Test fun `cuarto menguante, al reves`() {
        val lit = litMoon(face, 0.75, south = false)
        disc(inside = 0.95).filter { abs(it.third) > 0.1 }.forEach { (x, y, nx) ->
            val a = alpha(lit[y * size + x])
            if (nx < 0) assertTrue("izquierda ($x,$y) $a", a > 0.5) else assertTrue("derecha ($x,$y) $a", a < 0.06)
        }
    }

    @Test fun `menguante del 23 por ciento, iluminado el 23 mas o menos 4 y a la izquierda`() {
        val lit = litMoon(face, 0.84, south = false)
        val pixels = disc()
        val bright = pixels.filter { (x, y) -> alpha(lit[y * size + x]) > 0.5 }
        val fraction = bright.size.toDouble() / pixels.size
        assertEquals(0.23, fraction, 0.04)
        assertTrue(bright.map { it.third }.average() < 0)
    }

    @Test fun `en el hemisferio sur, girada 180 grados`() {
        for (phase in listOf(0.15, 0.25, 0.84)) {
            val north = litMoon(face, phase, south = false)
            val south = litMoon(face, phase, south = true)
            val turned = IntArray(size * size) { i -> north[size * size - 1 - i] }
            assertArrayEquals("$phase", turned, south)
        }
        // El creciente, con la luz a la izquierda.
        val south = litMoon(face, 0.25, south = true)
        disc(inside = 0.95).filter { abs(it.third) > 0.1 }.forEach { (x, y, nx) ->
            val a = alpha(south[y * size + x])
            if (nx < 0) assertTrue(a > 0.5) else assertTrue(a < 0.06)
        }
    }

    @Test fun `el relieve tambien gira en el sur`() {
        // Con un mapa con detalle, la igualdad tiene que seguir siendo exacta.
        val map = IntArray(64 * 32) { i -> if ((i * 7) % 5 < 2) 0xFF505050.toInt() else 0xFFE0E0E0.toInt() }
        val detailed = moonFace(map, 64, 32, size)
        val north = litMoon(detailed, 0.3, south = false)
        val south = litMoon(detailed, 0.3, south = true)
        assertArrayEquals(IntArray(size * size) { i -> north[size * size - 1 - i] }, south)
    }

    @Test fun `moonFace, el centro del disco toma el color del mapa en longitud 0`() {
        // Mapa de dos colores: de -90 a 90 grados de longitud (el centro del mapa), rojo; el resto, azul.
        val w = 64
        val h = 32
        val red = 0xFFC83C28.toInt()
        val blue = 0xFF2840C8.toInt()
        val map = IntArray(w * h) { i -> if (i % w in w / 4 until 3 * w / 4) red else blue }
        val f = moonFace(map, w, h, size)
        assertEquals(red, f.argb[(size / 2) * size + size / 2])
        // Fuera del disco, transparente.
        assertEquals(0, f.argb[0])
    }

    @Test fun `repartida entre los nucleos sale igual`() {
        val map = IntArray(64 * 32) { i -> if ((i * 7) % 5 < 2) 0xFF505050.toInt() else 0xFFE0E0E0.toInt() }
        val one = moonFace(map, 64, 32, size)
        val many = moonFace(map, 64, 32, size, ParallelRows)
        assertArrayEquals(one.argb, many.argb)
        assertArrayEquals(litMoon(one, 0.84, south = true), litMoon(many, 0.84, south = true, rows = ParallelRows))
    }
}
