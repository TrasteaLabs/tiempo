package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** H2, H3 (006): la búsqueda sobre la lista real del APK y sobre listas en línea. */
class PlaceSearchTest {
    private companion object {
        /** Una sola carga para toda la clase: son 8 MB. */
        val real = PlaceSearch(PlaceIndex { GZIPInputStream(File("src/main/assets/places.tsv.gz").inputStream()) })
    }

    private fun inline(vararg lines: String) = PlaceSearch(PlaceIndex { lines.joinToString(NL).byteInputStream() })

    @Test fun `con 0 y 1 letra no hay resultados y con 2 si`() = runTest {
        assertEquals(SearchResults(emptyList(), emptyList()), real.search(""))
        assertEquals(SearchResults(emptyList(), emptyList()), real.search(" c "))
        val co = real.search("co")
        assertTrue(co.spain.isNotEmpty() && co.world.isNotEmpty())
    }

    @Test fun `cordoba da primero la de Espana y luego la de Argentina, con region y pais en espanol (H3)`() = runTest {
        val r = real.search("cordoba")
        val es = r.spain.first()
        assertEquals("Córdoba", es.name)
        assertEquals("Córdoba", es.region)
        assertEquals("ES", es.countryCode)
        assertEquals("España", es.country)
        val ar = r.world.first()
        assertEquals("Córdoba", ar.name)
        assertEquals("AR", ar.countryCode)
        assertEquals("Argentina", ar.country)
        assertTrue(ar.region.isNotEmpty())
        assertTrue(ar.population > es.population)
        assertEquals(listOf(0..6), es.bold)
    }

    @Test fun `coruna encuentra A Coruna`() = runTest {
        val hit = real.search("coruna").spain.first()
        assertEquals("A Coruña", hit.name)
        assertEquals(listOf(2..7), hit.bold)
    }

    @Test fun `villanueva da varios de Espana con provincias distintas y el de mas habitantes primero (H3)`() = runTest {
        val spain = real.search("villanueva").spain
        assertTrue(spain.size > 5)
        assertTrue(spain.map { it.region }.toSet().size > 3)
        val populated = spain.filter { it.population > 0 }
        assertEquals(populated.sortedByDescending { it.population }, populated)
        assertTrue(spain.first().population >= spain.maxOf { it.population })
    }

    @Test fun `como mucho 30 por grupo`() = runTest {
        val r = real.search("villa")
        assertEquals(MAX_PER_GROUP, r.spain.size)
        assertEquals(MAX_PER_GROUP, r.world.size)
    }

    @Test fun `sevila no da nada`() = runTest {
        assertEquals(SearchResults(emptyList(), emptyList()), real.search("sevila"))
    }

    @Test fun `londres y london encuentran London con el nombre en espanol`() = runTest {
        for (q in listOf("londres", "london")) {
            val hit = real.search(q).world.first()
            assertEquals(q, "Londres", hit.name)
            assertEquals("GB", hit.countryCode)
            assertEquals("Reino Unido", hit.country)
        }
        assertEquals(listOf(0..6), real.search("londres").world.first().bold)
    }

    @Test fun `los de 0 habitantes van al final de su grupo por nombre y el empate de habitantes va por nombre`() = runTest {
        val s = inline(
            "R\tES\tUna", "R\tFR\tAutre",
            "Villa Zeta\t0\t0\t40.00\t-3.00",
            "Villa Beta\t0\t0\t40.00\t-3.00",
            "Villa Ávila\t0\t0\t40.00\t-3.00",
            "Villa Grande\t0\t500\t40.00\t-3.00",
            "Villa Media Z\t0\t100\t40.00\t-3.00",
            "Villa Media A\t0\t100\t40.00\t-3.00",
            "Villa France\t1\t0\t45.00\t2.00",
            "Villa Paris\t1\t900\t45.00\t2.00",
        )
        val r = s.search("villa")
        assertEquals(
            listOf("Villa Grande", "Villa Media A", "Villa Media Z", "Villa Ávila", "Villa Beta", "Villa Zeta"),
            r.spain.map { it.name },
        )
        assertEquals(listOf("Villa Paris", "Villa France"), r.world.map { it.name })
        assertEquals(LatLon(45.0, 2.0), r.world.first().position)
        assertEquals("Autre", r.world.first().region)
        assertEquals("Francia", r.world.first().country)
    }
}

private const val NL = "\n"
