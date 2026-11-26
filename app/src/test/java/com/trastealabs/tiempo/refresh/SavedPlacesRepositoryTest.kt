package com.trastealabs.tiempo.refresh

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.PlaceStores
import com.trastealabs.tiempo.data.SavedPlacesStore
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.MAX_SAVED
import com.trastealabs.tiempo.domain.StoredState
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** H6 (guardar, tope y repetidos) y H7 (borrar con sus datos). Sitios inventados (C5). */
class SavedPlacesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = TestData.at(2026, 11, 4, 10, 0)
    private lateinit var dir: File

    private fun TestScope.repo(): SavedPlacesRepository {
        dir = tmp.newFolder()
        return SavedPlacesRepository(
            SavedPlacesStore(dir.resolve("saved_places.json")), PlaceStores(dir.resolve("places")),
            nowSeconds = { clock++ }, scope = backgroundScope,
        )
    }

    private suspend fun SavedPlacesRepository.saveN(n: Int) = (0 until n).map {
        save("Villa $it", "Provincia Ejemplo", "ES", LatLon(40.0 + it, -3.0)) as SavedPlacesRepository.SaveResult.Saved
    }

    @Test fun `se guardan en orden de guardado`() = runTest {
        val repo = repo()
        repo.saveN(3)
        assertEquals(listOf("Villa 0", "Villa 1", "Villa 2"), repo.list().map { it.name })
        // Y siguen ahí en otro arranque de la app.
        val again = SavedPlacesRepository(
            SavedPlacesStore(dir.resolve("saved_places.json")), PlaceStores(dir.resolve("places")), scope = backgroundScope,
        )
        assertEquals(listOf("Villa 0", "Villa 1", "Villa 2"), again.list().map { it.name })
    }

    @Test fun `el repetido no se guarda dos veces`() = runTest {
        val repo = repo()
        val first = repo.save("Villa Ejemplo", "Provincia Ejemplo", "ES", LatLon(40.0, -3.0)) as SavedPlacesRepository.SaveResult.Saved
        val again = repo.save("Villa Ejemplo", "Provincia Ejemplo", "ES", LatLon(40.0, -3.0))
        assertEquals(SavedPlacesRepository.SaveResult.AlreadySaved(first.place), again)
        assertEquals(1, repo.list().size)
    }

    @Test fun `mismo nombre en otra provincia si se guarda`() = runTest {
        val repo = repo()
        repo.save("Villanueva Ejemplo", "Provincia A", "ES", LatLon(38.97, -5.8))
        val other = repo.save("Villanueva Ejemplo", "Provincia B", "ES", LatLon(38.33, -4.62))
        assertTrue(other is SavedPlacesRepository.SaveResult.Saved)
        assertEquals(2, repo.list().size)
    }

    @Test fun `con 10 el undecimo no se guarda`() = runTest {
        val repo = repo()
        repo.saveN(MAX_SAVED)
        val before = repo.list()
        assertEquals(SavedPlacesRepository.SaveResult.Full, repo.save("Villa Once", "", "FR", LatLon(45.0, 5.0)))
        assertEquals(before, repo.list())
    }

    @Test fun `con 10 el repetido sigue diciendo que ya esta`() = runTest {
        val repo = repo()
        val saved = repo.saveN(MAX_SAVED)
        assertEquals(
            SavedPlacesRepository.SaveResult.AlreadySaved(saved[4].place),
            repo.save("Villa 4", "Provincia Ejemplo", "ES", LatLon(44.0, -3.0)),
        )
    }

    @Test fun `ids distintos aunque se guarden en el mismo segundo`() = runTest {
        val repo = repo()
        clock = 0
        val a = (repo.save("A", "", "ES", LatLon(40.0, -3.0)) as SavedPlacesRepository.SaveResult.Saved).place
        clock = 0
        val b = (repo.save("B", "", "ES", LatLon(41.0, -3.0)) as SavedPlacesRepository.SaveResult.Saved).place
        assertNotEquals(a.id, b.id)
    }

    @Test fun `borrar lo quita de la lista y borra su carpeta entera`() = runTest {
        val repo = repo()
        val (a, b) = repo.saveN(2).map { it.place }
        repo.stores.snapshot(a.id).write(StoredState(TestData.snapshot(clock), false))
        repo.stores.reliability(a.id).write(com.trastealabs.tiempo.domain.StoredReliability(lastFailedAt = 1))
        val folder = dir.resolve("places").resolve(a.id)
        assertTrue(folder.resolve("snapshot.json").exists())
        assertTrue(folder.resolve("reliability.json").exists())

        repo.delete(a.id)

        assertEquals(listOf(b), repo.list())
        assertFalse(folder.exists())
        assertEquals(StoredState(null, false), repo.stores.snapshot(a.id).read())
    }

    @Test fun `tras borrar uno con 10 se puede guardar otro`() = runTest {
        val repo = repo()
        val saved = repo.saveN(MAX_SAVED)
        repo.delete(saved[0].place.id)
        assertTrue(repo.save("Villa Nueva", "", "PT", LatLon(38.7, -9.1)) is SavedPlacesRepository.SaveResult.Saved)
        assertEquals(MAX_SAVED, repo.list().size)
        assertEquals("Villa Nueva", repo.list().last().name)
    }
}
