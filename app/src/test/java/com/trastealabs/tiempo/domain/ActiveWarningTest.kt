package com.trastealabs.tiempo.domain

import com.trastealabs.tiempo.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveWarningTest {
    private val now = TestData.at(2025, 6, 10, 10, 0)
    private val hour = 3600L

    private fun warning(id: String, level: WarningLevel, onset: Long, expires: Long) =
        Warning(id, level, "tormentas", "Llanura de Ejemplo", onset, expires, null, "", null, now - hour)

    @Test fun `un aviso vigente sale`() {
        val w = warning("a", WarningLevel.YELLOW, now - hour, now + hour)
        assertEquals(w, activeWarning(listOf(w), now))
    }

    @Test fun `uno que empieza dentro de 23 horas sale`() {
        val w = warning("a", WarningLevel.YELLOW, now + 23 * hour, now + 30 * hour)
        assertEquals(w, activeWarning(listOf(w), now))
    }

    @Test fun `uno que empieza dentro de 25 horas no sale`() =
        assertNull(activeWarning(listOf(warning("a", WarningLevel.RED, now + 25 * hour, now + 30 * hour)), now))

    @Test fun `uno caducado no sale`() =
        assertNull(activeWarning(listOf(warning("a", WarningLevel.RED, now - 5 * hour, now - hour)), now))

    @Test fun `sin avisos no sale nada`() = assertNull(activeWarning(emptyList(), now))

    @Test fun `con amarillo y naranja a la vez gana el naranja (H14)`() {
        val yellow = warning("a", WarningLevel.YELLOW, now - hour, now + hour)
        val orange = warning("n", WarningLevel.ORANGE, now + 2 * hour, now + 6 * hour)
        assertEquals(orange, activeWarning(listOf(yellow, orange), now))
    }

    @Test fun `a igual nivel gana el que empieza antes`() {
        val later = warning("b", WarningLevel.ORANGE, now + 5 * hour, now + 8 * hour)
        val sooner = warning("a", WarningLevel.ORANGE, now + 2 * hour, now + 4 * hour)
        assertEquals(sooner, activeWarning(listOf(later, sooner), now))
    }
}
