package com.trastealabs.tiempo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** H12: la cifra que viaja, reglas puras. Cuenta desde la última temperatura vista hasta la de ahora en 700 ms. */
class TravelingNumberTest {
    @Test fun `de 12 a 18 son 6 pasos dentro de 700 ms, uno por grado`() {
        val steps = travelSteps(12, 18, motion = true)
        assertEquals(6, steps.size)
        assertEquals(listOf(13, 14, 15, 16, 17, 18), steps.map { it.value })
        assertTrue(steps.all { it.atMs in 0..TRAVEL_MS && it.alpha == 1f })
        assertEquals(steps.sortedBy { it.atMs }, steps)
    }

    @Test fun `un solo grado cambia pronto, no al final de la entrada`() {
        val steps = travelSteps(12, 13, motion = true)
        assertEquals(1, steps.size)
        assertEquals(13, steps.single().value)
        assertTrue("cambia a ${steps.single().atMs} ms", steps.single().atMs in 1..TRAVEL_MS / 2)
    }

    @Test fun `la cuenta frena al acercarse al final`() {
        val steps = travelSteps(0, 20, motion = true)
        val gaps = steps.map { it.atMs }.zipWithNext { a, b -> b - a }
        assertTrue("el último salto tarda más que el primero: $gaps", gaps.last() > gaps.first())
    }

    @Test fun `de menos 5 a 30 son como mucho 40 pasos y acaba en 30`() {
        val steps = travelSteps(-5, 30, motion = true)
        assertTrue(steps.size in 1..TRAVEL_MAX_STEPS)
        assertEquals(30, steps.last().value)
        assertTrue(steps.map { it.value }.zipWithNext().all { (a, b) -> b > a })
        assertTrue(steps.all { it.atMs in 0..TRAVEL_MS })
    }

    @Test fun `de 30 a menos 5 baja`() {
        val steps = travelSteps(30, -5, motion = true)
        assertEquals(-5, steps.last().value)
        assertTrue(steps.first().value < 30)
        assertTrue(steps.map { it.value }.zipWithNext().all { (a, b) -> b < a })
    }

    @Test fun `un salto enorme se queda en 40 pasos y acaba justo en el valor final`() {
        val steps = travelSteps(-20, 40, motion = true)
        assertEquals(TRAVEL_MAX_STEPS, steps.size)
        assertEquals(40, steps.last().value)
        assertTrue(steps.map { it.value }.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun `igual es un paso quieto`() {
        assertEquals(listOf(TravelStep(0, 14, 1f)), travelSteps(14, 14, motion = true))
    }

    @Test fun `sin anterior entra con un fundido en el valor final`() {
        val steps = travelSteps(null, 21, motion = true)
        assertTrue(steps.size in 2..TRAVEL_MAX_STEPS)
        assertTrue(steps.all { it.value == 21 })
        assertEquals(0f, steps.first().alpha, 0f)
        assertEquals(1f, steps.last().alpha, 0f)
        assertEquals(TRAVEL_MS, steps.last().atMs)
        assertTrue(steps.map { it.alpha }.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun `sin animaciones es un paso con el valor final, venga de donde venga`() {
        val still = listOf(TravelStep(0, 18, 1f))
        assertEquals(still, travelSteps(12, 18, motion = false))
        assertEquals(still, travelSteps(null, 18, motion = false))
        assertEquals(still, travelSteps(18, 18, motion = false))
    }

    @Test fun `en cada instante se ve el paso en vigor y antes del primero, la cifra de partida`() {
        val steps = travelSteps(12, 18, motion = true)
        assertTrue(steps.first().atMs > 0)
        assertEquals(12, travelAt(12, steps, 0).value)
        assertEquals(18, travelAt(12, steps, TRAVEL_MS).value)
        assertEquals(18, travelAt(12, steps, 5_000).value)
        val mid = travelAt(12, steps, 200).value
        assertTrue("a los 200 ms va de camino: $mid", mid in 13..17)
    }

    @Test fun `el fundido sube su transparencia entre los pasos y la cifra no cambia`() {
        val steps = travelSteps(null, 21, motion = true)
        assertEquals(0f, travelAt(null, steps, 0).alpha, 0f)
        val half = travelAt(null, steps, TRAVEL_MS / 2)
        assertEquals(21, half.value)
        assertTrue(half.alpha in 0.05f..0.99f)
        assertEquals(1f, travelAt(null, steps, TRAVEL_MS).alpha, 0f)
    }

    @Test fun `la cuenta cabe en la entrada`() {
        assertEquals(700L, TRAVEL_MS)
        assertEquals(40, TRAVEL_MAX_STEPS)
        assertTrue(TRAVEL_MS <= INTRO_MS)
    }
}
