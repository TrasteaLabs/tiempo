package com.trastealabs.tiempo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** H2 (006): la búsqueda sin tildes ni mayúsculas, por inicio de nombre o de palabra, y la negrita. */
class TextMatchTest {
    @Test fun `normalize quita tildes y mayusculas sin cambiar la longitud`() {
        for ((original, expected) in listOf(
            "Córdoba" to "cordoba",
            "A Coruña" to "a coruna",
            "Güéjar Sierra" to "guejar sierra",
            "L'Hospitalet de Llobregat" to "l'hospitalet de llobregat",
        )) {
            assertEquals(expected, normalize(original))
            assertEquals(original.length, normalize(original).length)
        }
    }

    @Test fun `coincide al inicio del nombre`() {
        assertTrue(matches(normalize("Córdoba"), normalizeQuery("cor")))
    }

    @Test fun `coincide al inicio de cada palabra tras espacio, guion, apostrofo, barra y parentesis`() {
        val q = normalizeQuery("bar")
        for (name in listOf("La Barca", "Sant-Barto", "L'Barrio", "Ojo/Barra", "Pueblo (Barrio)")) {
            assertTrue(name, matches(normalize(name), q))
        }
    }

    @Test fun `a mitad de palabra no coincide`() {
        assertFalse(matches(normalize("Escorial"), normalizeQuery("cor")))
        assertFalse(matches(normalize("Sevilla"), normalizeQuery("villa")))
    }

    @Test fun `da igual escribir con tildes, mayusculas o espacios en los extremos`() {
        val key = normalize("Córdoba")
        assertTrue(matches(key, normalizeQuery("CÓRDOBA")))
        assertTrue(matches(key, normalizeQuery("cordoba")))
        assertTrue(matches(key, normalizeQuery("  Cordoba ")))
        assertTrue(matches(normalize("A Coruña"), normalizeQuery("coruna")))
    }

    @Test fun `la negrita marca el tramo que coincide en el nombre original`() {
        val name = "Villanueva de Córdoba"
        val bold = boldRanges(name, "cordoba")
        assertEquals(listOf(14..20), bold)
        assertEquals("Córdoba", name.substring(bold.single()))
        assertEquals(listOf(0..3), boldRanges(name, "VILL"))
        assertEquals(emptyList<IntRange>(), boldRanges(name, "nueva"))
    }
}
