package com.trastealabs.tiempo.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** H6: la barra de rango de los 10 días se colorea según la temperatura. */
class TempColorsTest {
    @Test fun `los cuatro puntos de la escala`() {
        assertEquals(0xFF8CCBFF.toInt(), tempColor(2.0))
        assertEquals(0xFFA8E6CF.toInt(), tempColor(14.0))
        assertEquals(0xFFFFD27A.toInt(), tempColor(22.0))
        assertEquals(0xFFFF8A5B.toInt(), tempColor(32.0))
    }

    @Test fun `entre dos puntos se interpola`() {
        val c = tempColor(8.0)
        fun near(expected: Int, actual: Int) = assertTrue("$expected ≠ $actual", abs(expected - actual) <= 1)
        near((0x8C + 0xA8) / 2, (c shr 16) and 0xFF)
        near((0xCB + 0xE6) / 2, (c shr 8) and 0xFF)
        near((0xFF + 0xCF) / 2, c and 0xFF)
        assertEquals(255, (c ushr 24) and 0xFF)
    }

    @Test fun `fuera de la escala, el color del extremo`() {
        assertEquals(0xFF8CCBFF.toInt(), tempColor(-5.0))
        assertEquals(0xFFFF8A5B.toInt(), tempColor(40.0))
    }
}
