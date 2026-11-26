package com.trastealabs.tiempo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** H15 y H10: las reglas de la entrada al abrir, por milisegundo. Solo sube el astro; la foto y los paneles, quietos. */
class IntroTest {
    @Test fun `el velo 1 al empezar y 0 desde los 420 ms`() {
        assertEquals(1f, introVeil(0), 1e-6f)
        assertTrue(introVeil(200) in 0.01f..0.99f)
        assertEquals(0f, introVeil(420), 1e-6f)
        assertEquals(0f, introVeil(900), 1e-6f)
        // Siempre baja.
        (0L..420L step 10).zipWithNext().forEach { (a, b) -> assertTrue(introVeil(b) <= introVeil(a)) }
    }

    @Test fun `el astro sube 36 dp y escala 92 al empezar y llega a 0 y 1 a los 800 ms`() {
        val (rise0, scale0) = introAstro(0)
        assertEquals(36f, rise0, 1e-4f)
        assertEquals(0.92f, scale0, 1e-4f)
        val (rise1, scale1) = introAstro(800)
        assertEquals(0f, rise1, 1e-4f)
        assertEquals(1f, scale1, 1e-4f)
        assertEquals(introAstro(800), introAstro(2000))
        // A mitad de camino se mueve.
        assertTrue(introAstro(300).first in 0.1f..35.9f)
        // Siempre hacia su sitio.
        (0L..800L step 10).zipWithNext().forEach { (a, b) -> assertTrue(introAstro(b).first <= introAstro(a).first) }
    }

    @Test fun `todo termina en INTRO_MS, que es 800 ms (H10, H15)`() {
        assertEquals(800L, INTRO_MS)
        assertEquals(0f, introVeil(INTRO_MS), 0f)
        assertEquals(0f to 1f, introAstro(INTRO_MS))
    }

    @Test fun `la foto y los paneles ya no se mueven al abrir (H10)`() {
        val source = java.io.File("src/main/java/com/trastealabs/tiempo/ui/Intro.kt").readText()
        for (gone in listOf("introPhoto", "introPanel", "INTRO_LAST_PANEL", "PANEL_", "PHOTO_")) {
            assertFalse("Intro.kt aún tiene $gone", source.contains(gone))
        }
    }

    @Test fun `la cifra que viaja cabe en la entrada y no la alarga (H12)`() {
        assertTrue(TRAVEL_MS <= INTRO_MS)
        val source = java.io.File("src/main/java/com/trastealabs/tiempo/ui/TravelingNumber.kt").readText()
        // Sin bucle propio: la cuenta sigue el reloj de la entrada.
        assertFalse(source.contains("withFrameMillis"))
        assertFalse(source.contains("LaunchedEffect"))
    }

    @Test fun `la puerta se gasta una vez`() {
        val gate = IntroGate()
        assertTrue(gate.pending)
        assertTrue(gate.take())
        assertFalse(gate.pending)
        assertFalse(gate.take())
    }
}
