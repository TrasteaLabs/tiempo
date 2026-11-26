package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Test

/** 009, H12: los avisos del Resumen, uno por franja, el más grave arriba. */
class ActiveWarningsTest {
    private val now = TestData.at(2025, 6, 10, 10, 0)

    private fun w(id: String, level: WarningLevel, onsetH: Int, expiresH: Int, day: Int = 10) = Warning(
        id, level, "lluvias", "Llanura de Ejemplo", TestData.at(2025, 6, day, onsetH), TestData.at(2025, 6, day, expiresH),
        null, "", null, TestData.at(2025, 6, 10, 9),
    )

    private fun ids(list: List<Warning>) = activeWarnings(list, now).map { it.id }

    @Test fun `rojo antes que naranja antes que amarillo`() {
        assertEquals(listOf("r", "o", "y"), ids(listOf(w("y", WarningLevel.YELLOW, 8, 20), w("r", WarningLevel.RED, 12, 20), w("o", WarningLevel.ORANGE, 9, 20))))
    }

    @Test fun `a igual nivel, el que empieza antes`() {
        assertEquals(listOf("a", "b"), ids(listOf(w("b", WarningLevel.ORANGE, 16, 20), w("a", WarningLevel.ORANGE, 11, 20))))
    }

    @Test fun `los caducados fuera`() {
        assertEquals(listOf("v"), ids(listOf(w("c", WarningLevel.RED, 6, 9), w("v", WarningLevel.YELLOW, 9, 12), w("e", WarningLevel.RED, 6, 10))))
    }

    @Test fun `los que empiezan en mas de 24 h fuera`() {
        val inDay = w("d", WarningLevel.YELLOW, 10, 12, day = 11)   // justo a las 24 h: entra
        val later = w("l", WarningLevel.RED, 11, 14, day = 11)
        assertEquals(listOf("d"), ids(listOf(later, inDay)))
    }

    @Test fun `sin avisos, lista vacia`() {
        assertEquals(emptyList<String>(), ids(emptyList()))
    }

    @Test fun `el aviso del widget es el primero de la lista`() {
        val list = listOf(w("y", WarningLevel.YELLOW, 8, 20), w("o", WarningLevel.ORANGE, 9, 20))
        assertEquals(activeWarnings(list, now).first(), activeWarning(list, now))
    }
}
