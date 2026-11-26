package com.trastealabs.tiempo.refresh

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.OpenMeteoReliabilityClient
import com.trastealabs.tiempo.data.ReliabilitySource
import com.trastealabs.tiempo.data.ReliabilityStore
import com.trastealabs.tiempo.data.SnapshotStore
import com.trastealabs.tiempo.domain.DayReliability
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.StoredReliability
import com.trastealabs.tiempo.domain.StoredState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import com.trastealabs.tiempo.data.PlaceStores
import com.trastealabs.tiempo.data.SavedPlacesStore
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.toPlace

/** H9 (la previsión no espera), H8 (solo cuando toca) y H7 (el widget no se entera). */
class ReliabilityRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val now = TestData.at(2026, 11, 4, 10, 0)

    private fun reliability(fetchedAt: Long, position: LatLon = TestData.place.position) =
        Reliability(position, "Europe/Madrid", fetchedAt, listOf(DayReliability("2026-11-04", 122, 10, 19.0, 20.5)))

    private class FakeSource(var delayMs: Long = 0, var failure: Exception? = null, val make: () -> Reliability) : ReliabilitySource {
        val requested = mutableListOf<LatLon>()
        override suspend fun fetch(position: LatLon): Reliability {
            requested += position
            delay(delayMs)
            failure?.let { throw it }
            return make()
        }
    }

    private lateinit var snapshotFile: File
    private lateinit var reliabilityFile: File

    private suspend fun stores(withSnapshot: Boolean = true, saved: StoredReliability? = null): Pair<ReliabilityStore, SnapshotStore> {
        val dir = tmp.newFolder()
        snapshotFile = dir.resolve("snapshot.json")
        reliabilityFile = dir.resolve("reliability.json")
        val snapshots = SnapshotStore(snapshotFile)
        if (withSnapshot) snapshots.write(StoredState(TestData.snapshot(now), false))
        val store = ReliabilityStore(reliabilityFile)
        if (saved != null) store.write(saved)
        return ReliabilityStore(reliabilityFile) to snapshots
    }

    private fun TestScope.repo(source: ReliabilitySource, stores: Pair<ReliabilityStore, SnapshotStore>) =
        ReliabilityRepository(source, stores.first, stores.second, scope = backgroundScope)

    @Test fun `el estado tiene valor vacio nada mas crearse, sin esperar al fichero`() = runTest {
        val saved = StoredReliability(reliability(now - 600))
        val repo = repo(FakeSource { reliability(now) }, stores(saved = saved))
        assertEquals(StoredReliability(), repo.state.value)
        assertEquals(saved, repo.state.first { it.reliability != null })
    }

    @Test fun `si no toca no sale ninguna peticion`() = runTest {
        val source = FakeSource { reliability(now) }
        val saved = StoredReliability(reliability(now - 3600))
        val stores = stores(saved = saved)
        assertFalse(repo(source, stores).refreshIfDue(now))
        assertTrue(source.requested.isEmpty())
        assertEquals(saved, stores.first.read())
    }

    @Test fun `si toca se pide para el sitio de la prevision y se guarda`() = runTest {
        val source = FakeSource { reliability(now) }
        val stores = stores(saved = StoredReliability(reliability(now - 4 * 3600), lastFailedAt = now - 2 * 3600))
        val repo = repo(source, stores)

        assertTrue(repo.refreshIfDue(now))
        assertEquals(listOf(TestData.place.position), source.requested)
        assertEquals(StoredReliability(reliability(now), lastFailedAt = null), stores.first.read())
        assertEquals(StoredReliability(reliability(now)), repo.state.first { it.reliability?.fetchedAt == now })
    }

    @Test fun `si tarda mas de 30 s se rinde y queda la anterior con la hora del fallo`() = runTest {
        val old = reliability(now - 4 * 3600)
        val stores = stores(saved = StoredReliability(old))
        val repo = repo(FakeSource(delayMs = 60_000) { reliability(now) }, stores)

        val start = currentTime
        assertFalse(repo.refreshIfDue(now))
        assertEquals(30_000L, currentTime - start)
        assertEquals(StoredReliability(old, lastFailedAt = now), stores.first.read())
    }

    @Test fun `si Open-Meteo dice que se ha pasado el limite queda la anterior`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            repeat(2) { server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":true}")) }
            val client = OpenMeteoReliabilityClient(
                OkHttpClient(),
                forecastUrl = server.url("/v1/forecast").toString(),
                ensembleUrl = server.url("/v1/ensemble").toString(),
            )
            val old = reliability(now - 4 * 3600)
            val stores = stores(saved = StoredReliability(old))

            val repo = repo(client, stores)
            // En tiempo real: con el reloj virtual de runTest el tope de 30 s saltaría antes de que llegue el 429
            assertFalse(withContext(Dispatchers.Default) { repo.refreshIfDue(now) })
            assertTrue(server.requestCount >= 1)
            assertEquals(StoredReliability(old, lastFailedAt = now), stores.first.read())
        } finally {
            server.shutdown()
        }
    }

    @Test fun `un fallo sin ninguna guardada sigue sin ninguna`() = runTest {
        val stores = stores()
        val repo = repo(FakeSource(failure = IOException("sin red")) { reliability(now) }, stores)
        assertFalse(repo.refreshIfDue(now))
        assertEquals(StoredReliability(null, lastFailedAt = now), stores.first.read())
    }

    @Test fun `un error inesperado tampoco se escapa`() = runTest {
        val repo = repo(FakeSource(failure = IllegalStateException("raro")) { reliability(now) }, stores())
        assertFalse(repo.refreshIfDue(now))
    }

    @Test fun `sin prevision guardada no se pide`() = runTest {
        val source = FakeSource { reliability(now) }
        assertFalse(repo(source, stores(withSnapshot = false)).refreshIfDue(now))
        assertTrue(source.requested.isEmpty())
    }

    @Test fun `dos llamadas a la vez hacen una sola peticion`() = runTest {
        val source = FakeSource(delayMs = 1_000) { reliability(now) }
        val repo = repo(source, stores())
        val results = listOf(async { repo.refreshIfDue(now) }, async { repo.refreshIfDue(now) }).awaitAll()
        assertEquals(1, source.requested.size)
        assertEquals(listOf(false, true), results.sorted())
    }

    @Test fun `guardar la fiabilidad no toca snapshot json (el widget no se entera)`() = runTest {
        val stores = stores()
        val before = snapshotFile.readBytes()
        val modified = snapshotFile.lastModified()
        assertTrue(repo(FakeSource { reliability(now) }, stores).refreshIfDue(now))
        assertArrayEquals(before, snapshotFile.readBytes())
        assertEquals(modified, snapshotFile.lastModified())
        assertTrue(reliabilityFile.exists())
    }

    // --- 006 · Sitios guardados (H11): el ritmo de siempre, contado sobre lo de cada guardado ---

    private val savedPosition = LatLon(37.39, -5.98)

    private suspend fun TestScope.savedRepo(
        source: ReliabilitySource,
        at: Long,
        savedReliability: StoredReliability? = null,
        hereReliability: StoredReliability? = null,
    ): Triple<ReliabilityRepository, SavedPlacesRepository, SavedPlace> {
        val (store, snapshots) = stores(saved = hereReliability)
        val dir = tmp.newFolder()
        val saved = SavedPlacesRepository(
            SavedPlacesStore(dir.resolve("saved_places.json")), PlaceStores(dir.resolve("places")),
            nowSeconds = { at }, scope = backgroundScope,
        )
        val place = (saved.save("Ciudad Guardada", "Región", "ES", savedPosition) as SavedPlacesRepository.SaveResult.Saved).place
        saved.stores.snapshot(place.id).write(StoredState(TestData.snapshot(at, place = place.toPlace()), false))
        if (savedReliability != null) saved.stores.reliability(place.id).write(savedReliability)
        return Triple(ReliabilityRepository(source, store, snapshots, scope = backgroundScope, saved = saved), saved, place)
    }

    @Test fun `un guardado sin fiabilidad la pide con su posicion y la guarda en su carpeta`() = runTest {
        val at = TestData.at(2026, 11, 4, 10, 30)
        val source = FakeSource { reliability(at, savedPosition) }
        val (repo, saved, place) = savedRepo(source, at)

        assertTrue(repo.refreshSavedIfDue(place, at))

        assertEquals(listOf(savedPosition), source.requested)
        assertEquals(savedPosition, saved.stores.reliability(place.id).read().reliability!!.position)
        assertEquals(savedPosition, repo.savedState(place.id).first { it.reliability != null }.reliability!!.position)
        assertFalse(reliabilityFile.exists())
    }

    @Test fun `con la del guardado de hace 1 h a las 10 30 no pide`() = runTest {
        val at = TestData.at(2026, 11, 4, 10, 30)
        val source = FakeSource { reliability(at, savedPosition) }
        val (repo, _, place) = savedRepo(source, at, savedReliability = StoredReliability(reliability(at - 3600, savedPosition)))

        assertFalse(repo.refreshSavedIfDue(place, at))
        assertEquals(emptyList<LatLon>(), source.requested)
    }

    @Test fun `con la del guardado de las 07 25 a las 10 30 pide`() = runTest {
        val at = TestData.at(2026, 11, 4, 10, 30)
        val source = FakeSource { reliability(at, savedPosition) }
        val (repo, _, place) = savedRepo(
            source, at, savedReliability = StoredReliability(reliability(TestData.at(2026, 11, 4, 7, 25), savedPosition)),
        )

        assertTrue(repo.refreshSavedIfDue(place, at))
        assertEquals(listOf(savedPosition), source.requested)
    }

    @Test fun `que la de donde estoy sea reciente no cuenta para el guardado`() = runTest {
        val at = TestData.at(2026, 11, 4, 10, 30)
        val source = FakeSource { reliability(at, savedPosition) }
        val (repo, _, place) = savedRepo(source, at, hereReliability = StoredReliability(reliability(at - 600)))

        assertTrue(repo.refreshSavedIfDue(place, at))
        assertEquals(listOf(savedPosition), source.requested)
    }

    @Test fun `la del guardado no toca reliability json de donde estoy`() = runTest {
        val at = TestData.at(2026, 11, 4, 10, 30)
        val here = StoredReliability(reliability(at - 600))
        val (repo, _, place) = savedRepo(FakeSource { reliability(at, savedPosition) }, at, hereReliability = here)
        val before = reliabilityFile.readBytes()

        repo.refreshSavedIfDue(place, at)

        assertArrayEquals(before, reliabilityFile.readBytes())
        assertEquals(here, repo.state.first { it.reliability != null })
    }

    @Test fun `si falla se apunta el fallo en la del guardado`() = runTest {
        val at = TestData.at(2026, 11, 4, 10, 30)
        val source = FakeSource(failure = IOException("sin red")) { reliability(at, savedPosition) }
        val (repo, saved, place) = savedRepo(source, at)

        assertFalse(repo.refreshSavedIfDue(place, at))
        assertEquals(at, saved.stores.reliability(place.id).read().lastFailedAt)
    }
}
