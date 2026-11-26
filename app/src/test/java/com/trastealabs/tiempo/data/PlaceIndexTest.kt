package com.trastealabs.tiempo.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** H1 (006): la lista nueva con regiones, habitantes y nombres en español. */
class PlaceIndexTest {
    private val tsv = listOf(
        "R\tES\tCórdoba",
        "R\tAR\tCórdoba",
        "R\tES\t",
        "Córdoba\t0\t325708\t37.89\t-4.77",
        "Córdoba\t1\t2106734\t-31.41\t-64.19",
        "Sin Provincia\t2\t0\t40.00\t-3.00",
        "London\t3\t8961989\t51.51\t-0.13\tLondres",
        "R\tGB\tEngland",
    ).joinToString("\n", postfix = "\n")

    @Test fun `lee regiones, habitantes, posicion y nombre en espanol de un TSV`() = runTest {
        // La línea de región después de los lugares también vale: el índice es el orden de las líneas R.
        val index = PlaceIndex { tsv.byteInputStream() }
        assertFalse(index.isLoaded)
        index.load()
        assertTrue(index.isLoaded)
        assertEquals(4, index.size)
        assertEquals("Córdoba", index.names[0])
        assertEquals("ES", index.countryOf(0))
        assertEquals("Córdoba", index.regionOf(0))
        assertEquals(325_708, index.population[0])
        assertEquals(37.89, index.lat[0], 0.0)
        assertEquals(-4.77, index.lon[0], 0.0)
        assertEquals("AR", index.countryOf(1))
        assertEquals("", index.regionOf(2))
        assertNull(index.namesEs[0])
        assertEquals("Londres", index.namesEs[3])
        assertEquals("England", index.regionOf(3))
        assertEquals("cordoba", index.keys[0])
        assertEquals("londres", index.keysEs[3])
    }

    @Test fun `load es idempotente`() = runTest {
        var opened = 0
        val index = PlaceIndex { opened++; tsv.byteInputStream() }
        index.load()
        index.load()
        assertEquals(1, opened)
    }

    private val real by lazy {
        PlaceIndex { GZIPInputStream(File("src/main/assets/places.tsv.gz").inputStream()) }
    }

    @Test fun `la lista real trae Cordoba de Espana con su provincia`() = runTest {
        real.load()
        val i = (0 until real.size).first { real.names[it] == "Córdoba" && real.countryOf(it) == "ES" && real.population[it] > 100_000 }
        assertEquals("Córdoba", real.regionOf(i))
    }

    @Test fun `ningun lugar de Espana tiene region con prefijo de provincia`() = runTest {
        real.load()
        val bad = (0 until real.size).filter { i ->
            real.countryOf(i) == "ES" && listOf("Provincia", "Province", "Província").any { real.regionOf(i).startsWith(it) }
        }
        assertEquals(emptyList<Int>(), bad)
    }

    @Test fun `la lista real tiene unos 248000 lugares`() = runTest {
        real.load()
        assertTrue("${real.size}", real.size in 240_000..256_000)
    }
}
