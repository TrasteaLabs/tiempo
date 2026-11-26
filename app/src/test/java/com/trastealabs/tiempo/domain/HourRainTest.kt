package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La fila de lluvia de las horas (019): la probabilidad solo desde el 20 %, y la frase si no llega ninguna. */
class HourRainTest {
    private fun hours(vararg probability: Int?, mm: (Int) -> Double = { 0.0 }) =
        probability.mapIndexed { i, p -> HourPoint(1_800_000_000L + i * 3600L, 12.0, p, mm(i), 0, true) }

    @Test fun `todo a 0 da la frase y ninguna etiqueta`() {
        val row = hourRainRow(hours(*Array(24) { 0 }))
        assertTrue(row.dry)
        assertEquals(List(24) { false }, row.shown)
    }

    @Test fun `alguna hora sobre el 20 lleva su etiqueta y las demas no`() {
        val row = hourRainRow(hours(0, 10, 45, 80, 5, 0))
        assertFalse(row.dry)
        assertEquals(listOf(false, false, true, true, false, false), row.shown)
    }

    @Test fun `justo 20 se ensena`() {
        val row = hourRainRow(hours(0, 20, 0))
        assertFalse(row.dry)
        assertEquals(listOf(false, true, false), row.shown)
    }

    @Test fun `19 no se ensena y, sin barras, va la frase`() {
        val row = hourRainRow(hours(19, 19, 0))
        assertTrue(row.dry)
        assertEquals(listOf(false, false, false), row.shown)
    }

    @Test fun `con 19 pero milimetros no dice sin lluvia`() {
        val row = hourRainRow(hours(19, 0, 0, mm = { if (it == 0) 0.3 else 0.0 }))
        assertFalse(row.dry)
        assertEquals(listOf(false, false, false), row.shown)
    }

    @Test fun `una llovizna por debajo de la barra no cuenta`() {
        assertTrue(hourRainRow(hours(0, 0, mm = { 0.04 })).dry)
    }

    @Test fun `sin dato de probabilidad cuenta como 0 y sin horas no hay frase que valga`() {
        assertTrue(hourRainRow(hours(null, null)).dry)
        assertEquals(HourRainRow(emptyList(), dry = true), hourRainRow(emptyList()))
    }
}
