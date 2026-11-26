package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.SavedPlaces
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `saved_places.json` (006). */
class SavedPlacesStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val list = SavedPlaces(
        listOf(
            SavedPlace("p1000", "Villa Ejemplo", "Provincia Ejemplo", "ES", LatLon(40.0, -3.0), 1L),
            SavedPlace("p2000", "Ciudad Lejana", "Región Lejana", "AR", LatLon(-31.4, -64.18), 2L),
        ),
    )

    @Test fun `ida y vuelta conserva la lista y su orden`() = runTest {
        val file = tmp.newFolder().resolve("saved_places.json")
        SavedPlacesStore(file).write(list)
        assertEquals(list, SavedPlacesStore(file).read())
    }

    @Test fun `sin fichero la lista esta vacia`() = runTest {
        assertEquals(SavedPlaces(), SavedPlacesStore(tmp.newFolder().resolve("saved_places.json")).read())
    }

    @Test fun `un fichero roto da la lista vacia`() = runTest {
        val file = tmp.newFolder().resolve("saved_places.json")
        SavedPlacesStore(file).write(list)
        file.writeText(file.readText().take(40))
        assertEquals(SavedPlaces(), SavedPlacesStore(file).read())
    }

    @Test fun `escribir no deja ficheros temporales`() = runTest {
        val dir = tmp.newFolder()
        SavedPlacesStore(dir.resolve("saved_places.json")).write(list)
        assertEquals(listOf("saved_places.json"), dir.list()!!.toList())
    }
}
