package com.trastealabs.tiempo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 021: «Licencias de terceros» cubre exactamente las librerías del catálogo de Gradle. */
class ThirdPartyLicensesTest {
    private fun catalog(): File = listOf(File("../gradle/libs.versions.toml"), File("gradle/libs.versions.toml")).first { it.isFile }

    /** Las claves de la sección `[libraries]` del catálogo. */
    private fun libraryKeys(toml: String): Set<String> {
        val keys = mutableSetOf<String>()
        var inLibraries = false
        for (raw in toml.lines()) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inLibraries = line == "[libraries]"
                continue
            }
            if (!inLibraries || line.isEmpty() || line.startsWith("#")) continue
            keys += line.substringBefore("=").trim()
        }
        return keys
    }

    @Test fun `cada libreria del catalogo tiene su licencia y no sobra ninguna`() {
        val inCatalog = libraryKeys(catalog().readText())
        assertTrue("el catálogo no se ha leído", inCatalog.size > 10)
        val listed = THIRD_PARTY.mapNotNull { it.catalogKey }.toSet()
        assertEquals("faltan en THIRD_PARTY", emptySet<String>(), inCatalog - listed)
        assertEquals("sobran en THIRD_PARTY", emptySet<String>(), listed - inCatalog)
    }

    @Test fun `el lector del catalogo caza una libreria nueva`() {
        val toml = "[versions]\nx = \"1\"\n[libraries]\nnueva-lib = { module = \"a:b\", version = \"1\" }\n[plugins]\np = { id = \"q\" }\n"
        assertEquals(setOf("nueva-lib"), libraryKeys(toml))
    }

    @Test fun `ninguna licencia ni nombre vacio`() {
        THIRD_PARTY.forEach {
            assertTrue(it.name.isNotBlank())
            assertTrue(it.license.isNotBlank())
        }
    }
}
