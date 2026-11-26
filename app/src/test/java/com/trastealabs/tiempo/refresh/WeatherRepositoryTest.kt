package com.trastealabs.tiempo.refresh

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.AirSource
import com.trastealabs.tiempo.data.KnownWarnings
import com.trastealabs.tiempo.data.LocationSource
import com.trastealabs.tiempo.data.PlaceLookup
import com.trastealabs.tiempo.data.RefreshLog
import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.RefreshOutcome
import com.trastealabs.tiempo.data.SnapshotStore
import com.trastealabs.tiempo.data.WarningFetch
import com.trastealabs.tiempo.data.WarningSource
import com.trastealabs.tiempo.data.WeatherSource
import com.trastealabs.tiempo.domain.AirHour
import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Place
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.WeatherSnapshot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import com.trastealabs.tiempo.data.PlaceStores
import com.trastealabs.tiempo.data.SavedPlacesStore
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.toPlace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import org.junit.Assert.assertArrayEquals

class WeatherRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val now = TestData.at(2025, 6, 10, 10, 20)

    private class FakeWeather(var failing: Boolean = false, val now: Long) : WeatherSource {
        val requested = mutableListOf<LatLon>()
        override suspend fun fetch(position: LatLon): WeatherSnapshot {
            requested += position
            if (failing) throw IOException("sin red")
            return TestData.snapshot(now, place = Place("", "", position))
        }
    }

    private class FakeLocation(
        var current: LatLon?,
        var lastKnown: LatLon?,
        var background: Boolean = false,
    ) : LocationSource {
        var asked = 0
        override suspend fun current(): LatLon? { asked++; return current }
        override suspend fun lastKnown(): LatLon? { asked++; return lastKnown }
        override fun hasBackgroundPermission() = background
    }

    private class FakeWarnings(var result: List<Warning> = emptyList(), var failing: Boolean = false) : WarningSource {
        val requested = mutableListOf<LatLon>()
        override suspend fun fetchFor(position: LatLon): List<Warning> {
            requested += position
            if (failing) throw IOException("AEMET no responde")
            return result
        }
    }

    /**
     * Un AEMET que sí implementa `fetch` (015): apunta qué [KnownWarnings] recibe y contesta lo que se le diga.
     * [failing] lanza y [hanging] no responde nunca.
     */
    private class KnownAwareWarnings(
        var result: List<Warning> = emptyList(),
        var source: String? = "tar-2",
        var news: Boolean = true,
        var failing: Boolean = false,
        var hanging: Boolean = false,
        var bytes: Long = 0,
    ) : WarningSource {
        val received = mutableListOf<KnownWarnings?>()
        val requested = mutableListOf<LatLon>()
        override suspend fun fetchFor(position: LatLon): List<Warning> = fetch(position, null).warnings
        override suspend fun fetch(position: LatLon, known: KnownWarnings?): WarningFetch {
            requested += position
            received += known
            if (hanging) awaitCancellation()
            if (failing) throw IOException("AEMET no responde")
            return WarningFetch(result, source, news, bytes = bytes)
        }
    }

    /** El aire: una hora con el índice [aqi]; [failing] lanza y [hanging] no responde nunca. */
    private class FakeAir(val now: Long, var aqi: Int = 35, var failing: Boolean = false, var hanging: Boolean = false) : AirSource {
        val requested = mutableListOf<LatLon>()
        override suspend fun fetch(position: LatLon): AirQuality {
            requested += position
            if (hanging) awaitCancellation()
            if (failing) throw IOException("el aire no responde")
            return AirQuality(position, now, listOf(AirHour(now, aqi = aqi)))
        }
    }

    private val places = object : PlaceLookup {
        override fun nearest(position: LatLon) = when {
            position.lon > 5 -> Place("Ville Exemple", "FR", position)
            position.lat > 41 -> Place("Otra Villa", "ES", position)
            else -> Place("Villa Ejemplo", "ES", position)
        }
    }

    private fun warning(id: String) = Warning(
        id, WarningLevel.ORANGE, "tormentas", "Llanura de Ejemplo",
        now + 3600, now + 7200, "40%-70%", "Tormentas.", null, now - 3600,
    )

    private fun repo(
        weather: FakeWeather,
        location: FakeLocation,
        store: SnapshotStore,
        onUpdated: suspend () -> Unit = {},
        warnings: WarningSource = FakeWarnings(),
        air: FakeAir = FakeAir(now),
    ) = WeatherRepository(weather, warnings, air, places, location, store, nowSeconds = { now }, onUpdated = onUpdated)

    private fun store() = SnapshotStore(tmp.newFolder().resolve("snapshot.json"))

    @Test fun `un refresco correcto guarda el snapshot con el sitio y la posicion redondeada`() = runTest {
        val store = store()
        val weather = FakeWeather(now = now)
        val r = repo(weather, FakeLocation(LatLon(40.41678, -3.70379), null), store).refresh(Trigger.APP)

        assertEquals(RefreshResult.Ok, r)
        assertEquals(listOf(LatLon(40.42, -3.70)), weather.requested)
        val saved = store.read()
        assertFalse(saved.lastAttemptFailed)
        assertEquals("Villa Ejemplo", saved.snapshot!!.place.name)
        assertEquals(LatLon(40.42, -3.70), saved.snapshot!!.place.position)
    }

    @Test fun `si Open-Meteo falla el snapshot no cambia y se marca el intento fallido`() = runTest {
        val store = store()
        val good = TestData.snapshot(now)
        store.write(StoredState(good, false))
        val weather = FakeWeather(failing = true, now = now)

        val r = repo(weather, FakeLocation(LatLon(40.42, -3.70), null), store).refresh(Trigger.APP)

        assertEquals(RefreshResult.Offline, r)
        assertEquals(StoredState(good, true), store.read())
    }

    @Test fun `un refresco correcto despues de un fallo limpia la marca`() = runTest {
        val store = store()
        store.write(StoredState(TestData.snapshot(now), true))
        repo(FakeWeather(now = now), FakeLocation(LatLon(40.42, -3.70), null), store).refresh(Trigger.APP)
        assertFalse(store.read().lastAttemptFailed)
    }

    @Test fun `la primera vez sin conexion no hay snapshot pero se marca el fallo`() = runTest {
        val store = store()
        val r = repo(FakeWeather(failing = true, now = now), FakeLocation(LatLon(40.42, -3.70), null), store)
            .refresh(Trigger.APP)
        assertEquals(RefreshResult.Offline, r)
        assertEquals(StoredState(null, true), store.read())
    }

    @Test fun `sin ubicacion actual usa la ultima conocida`() = runTest {
        val weather = FakeWeather(now = now)
        val r = repo(weather, FakeLocation(null, LatLon(39.5, -2.5)), store()).refresh(Trigger.APP)
        assertEquals(RefreshResult.Ok, r)
        assertEquals(listOf(LatLon(39.5, -2.5)), weather.requested)
    }

    @Test fun `sin ninguna ubicacion no descarga ni toca lo guardado`() = runTest {
        val store = store()
        val weather = FakeWeather(now = now)
        val r = repo(weather, FakeLocation(null, null), store).refresh(Trigger.APP)
        assertEquals(RefreshResult.NoLocation, r)
        assertTrue(weather.requested.isEmpty())
        assertNull(store.read().snapshot)
    }

    @Test fun `una ubicacion nueva da un sitio nuevo`() = runTest {
        val store = store()
        val location = FakeLocation(LatLon(40.0, -3.0), null)
        val repo = repo(FakeWeather(now = now), location, store)
        repo.refresh(Trigger.APP)
        assertEquals("Villa Ejemplo", store.read().snapshot!!.place.name)

        location.current = LatLon(41.5, 2.1)
        repo.refresh(Trigger.APP)
        assertEquals("Otra Villa", store.read().snapshot!!.place.name)
    }

    @Test fun `en segundo plano sin permiso todo el tiempo usa el sitio guardado (H7)`() = runTest {
        val store = store()
        val saved = Place("Villa Ejemplo", "ES", LatLon(40.0, -3.0))
        store.write(StoredState(TestData.snapshot(now, place = saved), false))
        val weather = FakeWeather(now = now)
        val location = FakeLocation(LatLon(41.5, 2.1), LatLon(41.5, 2.1), background = false)

        val r = repo(weather, location, store).refresh(Trigger.BACKGROUND)

        assertEquals(RefreshResult.Ok, r)
        assertEquals(listOf(LatLon(40.0, -3.0)), weather.requested)
        assertEquals(0, location.asked)
        assertEquals("Villa Ejemplo", store.read().snapshot!!.place.name)
    }

    @Test fun `en segundo plano con permiso todo el tiempo usa la ubicacion actual (H7)`() = runTest {
        val store = store()
        store.write(StoredState(TestData.snapshot(now), false))
        val weather = FakeWeather(now = now)
        val location = FakeLocation(LatLon(41.5, 2.1), null, background = true)

        repo(weather, location, store).refresh(Trigger.BACKGROUND)

        assertEquals(listOf(LatLon(41.5, 2.1)), weather.requested)
        assertEquals("Otra Villa", store.read().snapshot!!.place.name)
    }

    @Test fun `en segundo plano con permiso pero sin senal se queda con el sitio guardado`() = runTest {
        val store = store()
        store.write(StoredState(TestData.snapshot(now), false))
        val weather = FakeWeather(now = now)

        repo(weather, FakeLocation(null, null, background = true), store).refresh(Trigger.BACKGROUND)

        assertEquals(listOf(LatLon(40.0, -3.0)), weather.requested)
    }

    @Test fun `en segundo plano sin nada guardado ni permiso no descarga`() = runTest {
        val weather = FakeWeather(now = now)
        val r = repo(weather, FakeLocation(LatLon(41.5, 2.1), null), store()).refresh(Trigger.BACKGROUND)
        assertEquals(RefreshResult.NoLocation, r)
        assertTrue(weather.requested.isEmpty())
    }

    @Test fun `tras refrescar, bien o mal, se avisa a los widgets`() = runTest {
        var updates = 0
        val store = store()
        val weather = FakeWeather(now = now)
        val repo = repo(weather, FakeLocation(LatLon(40.42, -3.70), null), store, onUpdated = { updates++ })

        repo.refresh(Trigger.APP)
        assertEquals(1, updates)

        weather.failing = true
        repo.refresh(Trigger.BACKGROUND)
        assertEquals(2, updates)
        assertTrue(store.read().lastAttemptFailed)
    }

    @Test fun `si avisar a los widgets falla el refresco sigue siendo correcto`() = runTest {
        val repo = repo(
            FakeWeather(now = now),
            FakeLocation(LatLon(40.42, -3.70), null),
            store(),
            onUpdated = { throw IllegalStateException("sin widgets") },
        )
        assertEquals(RefreshResult.Ok, repo.refresh(Trigger.APP))
    }

    @Test fun `en Espana se guardan los avisos de AEMET con su hora`() = runTest {
        val store = store()
        val aemet = FakeWarnings(listOf(warning("a")))
        repo(FakeWeather(now = now), FakeLocation(LatLon(40.0, -3.0), null), store, warnings = aemet).refresh(Trigger.APP)

        assertEquals(listOf(LatLon(40.0, -3.0)), aemet.requested)
        val saved = store.read().snapshot!!
        assertEquals(listOf(warning("a")), saved.warnings)
        assertEquals(saved.fetchedAt, saved.warningsFetchedAt)
    }

    @Test fun `si AEMET falla se conservan los avisos guardados sin error (H16)`() = runTest {
        val store = store()
        val before = TestData.snapshot(now).copy(warnings = listOf(warning("viejo")), warningsFetchedAt = now - 1800)
        store.write(StoredState(before, false))
        val aemet = FakeWarnings(failing = true)

        val r = repo(FakeWeather(now = now), FakeLocation(LatLon(40.0, -3.0), null), store, warnings = aemet)
            .refresh(Trigger.APP)

        assertEquals(RefreshResult.Ok, r)
        val saved = store.read()
        assertFalse(saved.lastAttemptFailed)
        assertEquals(listOf(warning("viejo")), saved.snapshot!!.warnings)
        assertEquals(now - 1800, saved.snapshot!!.warningsFetchedAt)
    }

    @Test fun `si AEMET falla en otro sitio no se arrastran los avisos del anterior`() = runTest {
        val store = store()
        store.write(StoredState(TestData.snapshot(now).copy(warnings = listOf(warning("viejo"))), false))

        repo(FakeWeather(now = now), FakeLocation(LatLon(41.5, -3.0), null), store, warnings = FakeWarnings(failing = true))
            .refresh(Trigger.APP)

        val saved = store.read().snapshot!!
        assertEquals("Otra Villa", saved.place.name)
        assertTrue(saved.warnings.isEmpty())
        assertNull(saved.warningsFetchedAt)
    }

    @Test fun `fuera de Espana no se llama a AEMET y no hay avisos`() = runTest {
        val store = store()
        store.write(StoredState(TestData.snapshot(now).copy(warnings = listOf(warning("viejo"))), false))
        val aemet = FakeWarnings(listOf(warning("a")))

        val r = repo(FakeWeather(now = now), FakeLocation(LatLon(45.0, 6.0), null), store, warnings = aemet)
            .refresh(Trigger.APP)

        assertEquals(RefreshResult.Ok, r)
        assertTrue(aemet.requested.isEmpty())
        val saved = store.read().snapshot!!
        assertEquals("FR", saved.place.countryCode)
        assertTrue(saved.warnings.isEmpty())
        assertNull(saved.warningsFetchedAt)
    }

    @Test fun `si Open-Meteo falla los avisos guardados no cambian`() = runTest {
        val store = store()
        val before = TestData.snapshot(now).copy(warnings = listOf(warning("viejo")))
        store.write(StoredState(before, false))

        repo(FakeWeather(failing = true, now = now), FakeLocation(LatLon(40.0, -3.0), null), store,
            warnings = FakeWarnings(listOf(warning("nuevo")))).refresh(Trigger.APP)

        assertEquals(StoredState(before, true), store.read())
    }

    // ---------- AEMET ligero (015 P1) ----------

    private fun withTar(source: String? = "tar-1", position: LatLon = here, name: String = "viejo") =
        TestData.snapshot(now - 1800).copy(
            warnings = listOf(warning(name)), warningsFetchedAt = now - 1800, warningsSource = source,
            place = Place("Villa Ejemplo", "ES", position),
        )

    @Test fun `el segundo refresco del mismo sitio pasa a AEMET lo que ya sabe (fuente, posicion y avisos)`() = runTest {
        val store = store()
        val aemet = KnownAwareWarnings(result = listOf(warning("a")), source = "tar-1", news = true)
        val r = repo(FakeWeather(now = now), FakeLocation(here, null), store, warnings = aemet)

        r.refresh(Trigger.APP)
        assertNull(aemet.received[0])                                   // la primera vez no sabe nada
        assertEquals("tar-1", store.read().snapshot!!.warningsSource)

        r.refresh(Trigger.APP)
        assertEquals(KnownWarnings("tar-1", here, listOf(warning("a"))), aemet.received[1])
    }

    @Test fun `H3 si AEMET dice que no hay nada nuevo, los avisos son los de antes y la hora de comprobados es la de ahora`() = runTest {
        val store = store()
        store.write(StoredState(withTar(), false))
        val aemet = KnownAwareWarnings(result = listOf(warning("viejo")), source = "tar-1", news = false)

        repo(FakeWeather(now = now), FakeLocation(here, null), store, warnings = aemet).refresh(Trigger.APP)

        val saved = store.read().snapshot!!
        assertEquals(listOf(warning("viejo")), saved.warnings)
        assertEquals(now, saved.warningsFetchedAt)                       // antes: now - 1800
        assertEquals("tar-1", saved.warningsSource)
    }

    @Test fun `H5 con un tar gz nuevo se guardan los avisos y la fuente nuevos`() = runTest {
        val store = store()
        store.write(StoredState(withTar(), false))
        val aemet = KnownAwareWarnings(result = listOf(warning("nuevo")), source = "tar-2", news = true)

        repo(FakeWeather(now = now), FakeLocation(here, null), store, warnings = aemet).refresh(Trigger.APP)

        val saved = store.read().snapshot!!
        assertEquals(listOf(warning("nuevo")), saved.warnings)
        assertEquals("tar-2", saved.warningsSource)
    }

    @Test fun `H6 si AEMET lanza se quedan los avisos y la fuente de antes y la prevision sale`() = runTest {
        val store = store()
        store.write(StoredState(withTar(), false))
        val aemet = KnownAwareWarnings(failing = true)

        val r = repo(FakeWeather(now = now), FakeLocation(here, null), store, warnings = aemet).refresh(Trigger.APP)

        assertEquals(RefreshResult.Ok, r)
        val saved = store.read().snapshot!!
        assertEquals(now, saved.fetchedAt)
        assertEquals(listOf(warning("viejo")), saved.warnings)
        assertEquals(now - 1800, saved.warningsFetchedAt)
        assertEquals("tar-1", saved.warningsSource)
    }

    @Test fun `H6 si AEMET tarda mas del tope se quedan los de antes y la prevision sale`() = runTest {
        val store = store()
        store.write(StoredState(withTar(), false))
        val aemet = KnownAwareWarnings(hanging = true)

        val r = repo(FakeWeather(now = now), FakeLocation(here, null), store, warnings = aemet).refresh(Trigger.APP)

        assertEquals(RefreshResult.Ok, r)
        assertEquals(30_000L, currentTime)
        val saved = store.read().snapshot!!
        assertEquals(listOf(warning("viejo")), saved.warnings)
        assertEquals("tar-1", saved.warningsSource)
    }

    @Test fun `si AEMET falla y el sitio se ha movido, los avisos se quedan pero sin fuente para no darlos por buenos en la posicion nueva`() = runTest {
        val store = store()
        store.write(StoredState(withTar(position = LatLon(40.0, -3.0)), false))
        val aemet = KnownAwareWarnings(failing = true)

        // mismo nombre de sitio, 1 km más allá
        repo(FakeWeather(now = now), FakeLocation(LatLon(40.01, -3.0), null), store, warnings = aemet).refresh(Trigger.APP)

        val saved = store.read().snapshot!!
        assertEquals(listOf(warning("viejo")), saved.warnings)
        assertNull(saved.warningsSource)
    }

    @Test fun `fuera de Espana no se llama a AEMET y no hay fuente`() = runTest {
        val store = store()
        store.write(StoredState(withTar(), false))
        val aemet = KnownAwareWarnings(result = listOf(warning("a")))

        repo(FakeWeather(now = now), FakeLocation(LatLon(45.0, 6.0), null), store, warnings = aemet).refresh(Trigger.APP)

        assertTrue(aemet.requested.isEmpty())
        val saved = store.read().snapshot!!
        assertNull(saved.warningsSource)
        assertTrue(saved.warnings.isEmpty())
    }

    @Test fun `un snapshot de antes, sin fuente, no manda nada conocido a AEMET`() = runTest {
        val store = store()
        store.write(StoredState(withTar(source = null), false))
        val aemet = KnownAwareWarnings(result = listOf(warning("a")))

        repo(FakeWeather(now = now), FakeLocation(here, null), store, warnings = aemet).refresh(Trigger.APP)

        assertNull(aemet.received.single())
    }

    @Test fun `un sitio guardado manda su propio conocido y guarda su fuente`() = runTest {
        val aemet = KnownAwareWarnings(result = listOf(warning("a1")), source = "tar-9", news = true)
        val s = savedSetup(warnings = aemet)
        val place = s.saved.add("Ciudad Guardada", "ES", LatLon(37.39, -5.98))
        // lo de «donde estoy» no debe colarse en el guardado
        s.here.write(StoredState(withTar(), false))

        s.repo.refreshSaved(place)
        assertNull(aemet.received[0])
        assertEquals("tar-9", s.saved.stores.snapshot(place.id).read().snapshot!!.warningsSource)

        s.repo.refreshSaved(place)
        assertEquals(KnownWarnings("tar-9", LatLon(37.39, -5.98), listOf(warning("a1"))), aemet.received[1])
        assertEquals("tar-1", s.here.read().snapshot!!.warningsSource)     // sin tocar
    }

    // ---------- Aire (H1, H3 del 005) ----------

    private val here = LatLon(40.0, -3.0)

    private fun storedAir(fetchedAt: Long, position: LatLon = here, aqi: Int = 20) =
        AirQuality(position, fetchedAt, listOf(AirHour(fetchedAt, aqi = aqi)))

    private suspend fun storeWithAir(air: AirQuality): SnapshotStore = store().also {
        it.write(StoredState(TestData.snapshot(now).copy(air = air), false))
    }

    @Test fun `la primera vez se pide y se guarda el aire`() = runTest {
        val store = store()
        val air = FakeAir(now)
        repo(FakeWeather(now = now), FakeLocation(here, null), store, air = air).refresh(Trigger.APP)

        assertEquals(listOf(here), air.requested)
        assertEquals(AirQuality(here, now, listOf(AirHour(now, aqi = 35))), store.read().snapshot!!.air)
    }

    @Test fun `con aire del mismo sitio de hace menos de 3 h no se pide y se copia`() = runTest {
        val before = storedAir(now - (3 * 3600 - 60))
        val store = storeWithAir(before)
        val air = FakeAir(now)
        repo(FakeWeather(now = now), FakeLocation(here, null), store, air = air).refresh(Trigger.APP)

        assertTrue(air.requested.isEmpty())
        assertEquals(before, store.read().snapshot!!.air)
    }

    @Test fun `a las 3 h se pide otra vez`() = runTest {
        val store = storeWithAir(storedAir(now - 3 * 3600))
        val air = FakeAir(now)
        repo(FakeWeather(now = now), FakeLocation(here, null), store, air = air).refresh(Trigger.APP)

        assertEquals(listOf(here), air.requested)
        assertEquals(now, store.read().snapshot!!.air!!.fetchedAt)
    }

    @Test fun `si el aire falla la prevision se guarda y se queda el aire de antes (H3)`() = runTest {
        val before = storedAir(now - 4 * 3600)
        val store = storeWithAir(before)
        val air = FakeAir(now, failing = true)
        val r = repo(FakeWeather(now = now), FakeLocation(here, null), store, air = air).refresh(Trigger.APP)

        assertEquals(RefreshResult.Ok, r)
        assertEquals(listOf(here), air.requested)
        val saved = store.read()
        assertFalse(saved.lastAttemptFailed)
        assertEquals(now, saved.snapshot!!.fetchedAt)
        assertEquals(before, saved.snapshot!!.air)
    }

    @Test fun `si el aire se cuelga la prevision se guarda a los 15 s con el aire de antes (H3)`() = runTest {
        val before = storedAir(now - 4 * 3600)
        val store = storeWithAir(before)
        val air = FakeAir(now, hanging = true)
        val r = repo(FakeWeather(now = now), FakeLocation(here, null), store, air = air).refresh(Trigger.APP)

        assertEquals(RefreshResult.Ok, r)
        assertEquals(15_000L, currentTime)
        val saved = store.read().snapshot!!
        assertEquals(now, saved.fetchedAt)
        assertEquals(before, saved.air)
    }

    @Test fun `en otro sitio (mas de 5 km) con el aire fallando no se arrastra el de antes`() = runTest {
        val store = storeWithAir(storedAir(now - 600))
        val air = FakeAir(now, failing = true)
        repo(FakeWeather(now = now), FakeLocation(LatLon(41.5, -3.0), null), store, air = air).refresh(Trigger.APP)

        assertEquals(listOf(LatLon(41.5, -3.0)), air.requested)
        val saved = store.read().snapshot!!
        assertEquals("Otra Villa", saved.place.name)
        assertNull(saved.air)
    }

    @Test fun `si la prevision falla el aire guardado no cambia`() = runTest {
        val before = storedAir(now - 4 * 3600)
        val store = storeWithAir(before)
        repo(FakeWeather(failing = true, now = now), FakeLocation(here, null), store, air = FakeAir(now, aqi = 90))
            .refresh(Trigger.APP)

        assertEquals(before, store.read().snapshot!!.air)
        assertTrue(store.read().lastAttemptFailed)
    }

    // --- 006 · Sitios guardados ---

    private class SavedSetup(val repo: WeatherRepository, val saved: SavedPlacesRepository, val here: SnapshotStore, val dir: java.io.File)

    private fun kotlinx.coroutines.test.TestScope.savedSetup(
        weather: FakeWeather = FakeWeather(now = now),
        warnings: WarningSource = FakeWarnings(),
        air: FakeAir = FakeAir(now),
        location: FakeLocation = FakeLocation(LatLon(40.0, -3.0), null),
        onUpdated: suspend () -> Unit = {},
    ): SavedSetup {
        val dir = tmp.newFolder()
        val here = SnapshotStore(dir.resolve("snapshot.json"))
        val saved = SavedPlacesRepository(
            SavedPlacesStore(dir.resolve("saved_places.json")), PlaceStores(dir.resolve("places")),
            nowSeconds = { now }, scope = backgroundScope,
        )
        val repo = WeatherRepository(weather, warnings, air, places, location, here, nowSeconds = { now }, onUpdated = onUpdated, saved = saved)
        return SavedSetup(repo, saved, here, dir)
    }

    private suspend fun SavedPlacesRepository.add(name: String, cc: String, position: LatLon): SavedPlace =
        (save(name, "Región Ejemplo", cc, position) as SavedPlacesRepository.SaveResult.Saved).place

    @Test fun `refreshSaved guarda en su carpeta con su nombre y posicion, sin ubicacion ni lista de lugares`() = runTest {
        val weather = FakeWeather(now = now)
        val location = FakeLocation(LatLon(40.0, -3.0), null)
        val s = savedSetup(weather = weather, location = location)
        val place = s.saved.add("Ciudad Guardada", "ES", LatLon(37.39, -5.98))

        assertEquals(RefreshResult.Ok, s.repo.refreshSaved(place))

        assertEquals(listOf(LatLon(37.39, -5.98)), weather.requested)
        assertEquals(0, location.asked)
        val stored = s.saved.stores.snapshot(place.id).read().snapshot!!
        assertEquals(Place("Ciudad Guardada", "ES", LatLon(37.39, -5.98)), stored.place)
        assertTrue(s.dir.resolve("places/${place.id}/snapshot.json").exists())
        assertEquals(stored, s.repo.savedState(place.id).first().snapshot)
    }

    @Test fun `un guardado en Espana pide AEMET y uno de fuera no`() = runTest {
        val warnings = FakeWarnings(result = listOf(warning("a1")))
        val s = savedSetup(warnings = warnings)
        val spain = s.saved.add("Ciudad Guardada", "ES", LatLon(37.39, -5.98))
        val abroad = s.saved.add("Ciudad Lejana", "AR", LatLon(-31.42, -64.18))

        s.repo.refreshSaved(spain)
        s.repo.refreshSaved(abroad)

        assertEquals(listOf(LatLon(37.39, -5.98)), warnings.requested)
        assertEquals(listOf("a1"), s.saved.stores.snapshot(spain.id).read().snapshot!!.warnings.map { it.id })
        assertEquals(emptyList<Warning>(), s.saved.stores.snapshot(abroad.id).read().snapshot!!.warnings)
    }

    @Test fun `el aire de un guardado sigue su regla de 3 h con lo suyo`() = runTest {
        val air = FakeAir(now)
        val s = savedSetup(air = air)
        val place = s.saved.add("Ciudad Guardada", "ES", LatLon(37.39, -5.98))
        val store = s.saved.stores.snapshot(place.id)
        val recent = AirQuality(place.position, now - 2 * 3600, listOf(AirHour(now, aqi = 20)))
        store.write(StoredState(TestData.snapshot(now - 3600, place = place.toPlace()).copy(air = recent), false))

        s.repo.refreshSaved(place)
        assertEquals(emptyList<LatLon>(), air.requested)
        assertEquals(recent, store.read().snapshot!!.air)

        store.write(StoredState(TestData.snapshot(now - 3600, place = place.toPlace()).copy(air = recent.copy(fetchedAt = now - 3 * 3600)), false))
        s.repo.refreshSaved(place)
        assertEquals(listOf(place.position), air.requested)
    }

    @Test fun `sin conexion se queda lo guardado y se marca el fallo`() = runTest {
        val s = savedSetup(weather = FakeWeather(failing = true, now = now))
        val place = s.saved.add("Ciudad Guardada", "ES", LatLon(37.39, -5.98))
        val good = TestData.snapshot(now - 7200, place = place.toPlace())
        s.saved.stores.snapshot(place.id).write(StoredState(good, false))

        assertEquals(RefreshResult.Offline, s.repo.refreshSaved(place))
        assertEquals(StoredState(good, true), s.saved.stores.snapshot(place.id).read())
    }

    @Test fun `recien guardado y sin conexion no hay datos pero no rompe`() = runTest {
        val s = savedSetup(weather = FakeWeather(failing = true, now = now))
        val place = s.saved.add("Ciudad Nueva", "ES", LatLon(37.18, -3.6))

        assertEquals(RefreshResult.Offline, s.repo.refreshSaved(place))
        assertEquals(StoredState(null, true), s.saved.stores.snapshot(place.id).read())
    }

    @Test fun `refrescar y borrar un guardado no toca snapshot json ni avisa al widget (H13)`() = runTest {
        var widgetUpdates = 0
        val weather = FakeWeather(now = now)
        val s = savedSetup(weather = weather, onUpdated = { widgetUpdates++ })
        s.here.write(StoredState(TestData.snapshot(now - 600), false))
        val hereFile = s.dir.resolve("snapshot.json")
        val before = hereFile.readBytes()
        val place = s.saved.add("Ciudad Guardada", "ES", LatLon(37.39, -5.98))

        s.repo.refreshSaved(place)
        s.saved.delete(place.id)

        assertArrayEquals(before, hereFile.readBytes())
        assertEquals(0, widgetUpdates)
        // El segundo plano, sin permiso de fondo, sigue con el último sitio de «donde estoy».
        val background = WeatherRepository(
            weather, FakeWarnings(), FakeAir(now), places, FakeLocation(null, null, background = false),
            SnapshotStore(hereFile), nowSeconds = { now }, saved = s.saved,
        )
        background.refresh(Trigger.BACKGROUND)
        assertEquals(TestData.place.position, weather.requested.last())
    }

    @Test fun `un refresco que acaba despues de borrar el sitio no vuelve a crear su carpeta`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : WeatherSource {
            override suspend fun fetch(position: LatLon): WeatherSnapshot {
                gate.await()
                return TestData.snapshot(now, place = Place("", "", position))
            }
        }
        val dir = tmp.newFolder()
        val saved = SavedPlacesRepository(
            SavedPlacesStore(dir.resolve("saved_places.json")), PlaceStores(dir.resolve("places")), scope = backgroundScope,
        )
        val repo = WeatherRepository(
            slow, FakeWarnings(), FakeAir(now), places, FakeLocation(null, null), SnapshotStore(dir.resolve("snapshot.json")),
            nowSeconds = { now }, saved = saved,
        )
        val place = saved.add("Ciudad Guardada", "ES", LatLon(37.39, -5.98))

        val refresh = async { repo.refreshSaved(place) }
        kotlinx.coroutines.yield()
        saved.delete(place.id)
        gate.complete(Unit)
        refresh.await()

        assertFalse(dir.resolve("places/${place.id}").exists())
    }

    @Test fun `donde estoy y un guardado en el mismo sitio tienen cada uno lo suyo`() = runTest {
        val s = savedSetup(location = FakeLocation(LatLon(40.0, -3.0), null))
        val place = s.saved.add("Villa Ejemplo", "ES", LatLon(40.0, -3.0))

        s.repo.refresh(Trigger.APP)
        val here = s.here.read()
        s.repo.refreshSaved(place)

        assertEquals(here, s.here.read())
        val savedSnap = s.saved.stores.snapshot(place.id).read().snapshot!!
        assertEquals("Villa Ejemplo", savedSnap.place.name)
        assertTrue(s.dir.resolve("snapshot.json").exists())
        assertTrue(s.dir.resolve("places/${place.id}/snapshot.json").exists())

        // Y refrescar «donde estoy» tampoco toca el guardado.
        val savedBefore = s.saved.stores.snapshot(place.id).read()
        s.repo.refresh(Trigger.APP)
        assertEquals(savedBefore, s.saved.stores.snapshot(place.id).read())
    }

    // --- 015 P2: el registro de refrescos de «donde estoy» (H9, H14) ---

    private class LoggedSetup(val repo: WeatherRepository, val log: RefreshLog, val file: java.io.File)

    private fun logged(
        weather: FakeWeather = FakeWeather(now = now),
        location: FakeLocation = FakeLocation(LatLon(40.0, -3.0), null),
        warnings: WarningSource = KnownAwareWarnings(),
        file: java.io.File = tmp.newFolder().resolve("refresh_log.jsonl"),
        onUpdated: suspend () -> Unit = {},
        saved: SavedPlacesRepository? = null,
    ): LoggedSetup {
        val log = RefreshLog(file) { now }
        val repo = WeatherRepository(
            weather, warnings, FakeAir(now), places, location, store(),
            nowSeconds = { now }, onUpdated = onUpdated, saved = saved, log = log,
        )
        return LoggedSetup(repo, log, file)
    }

    private suspend fun LoggedSetup.lines(): List<RefreshLogEntry> = log.entries.first()

    @Test fun `un refresco de la app escribe APP, OK y lo que dijo AEMET con sus bytes (H9)`() = runTest {
        val s = logged(warnings = KnownAwareWarnings(news = true, bytes = 352_000))
        assertEquals(RefreshResult.Ok, s.repo.refresh(Trigger.APP))

        assertEquals(
            listOf(RefreshLogEntry(now, RefreshOrigin.APP, RefreshOutcome.OK, AemetOutcome.NEW, 352_000)),
            s.lines(),
        )
    }

    @Test fun `el segundo plano sin origen es WORKER y con ALARM es ALARM`() = runTest {
        val s = logged()
        s.repo.refresh(Trigger.BACKGROUND)
        s.repo.refresh(Trigger.BACKGROUND, RefreshOrigin.ALARM)
        s.repo.refresh(Trigger.BACKGROUND, RefreshOrigin.WORKER)

        assertEquals(
            listOf(RefreshOrigin.WORKER, RefreshOrigin.ALARM, RefreshOrigin.WORKER),
            s.lines().map { it.origin },
        )
    }

    @Test fun `AEMET sin cambios se apunta tal cual, con los bytes del RSS si hubo`() = runTest {
        val aemet = KnownAwareWarnings(news = false, bytes = 0)
        val s = logged(warnings = aemet)
        s.repo.refresh(Trigger.APP)
        aemet.bytes = 155_000   // el RSS cambió pero enlaza el mismo tar.gz
        s.repo.refresh(Trigger.APP)

        assertEquals(listOf(AemetOutcome.UNCHANGED, AemetOutcome.UNCHANGED), s.lines().map { it.aemet })
        assertEquals(listOf(0L, 155_000L), s.lines().map { it.aemetBytes })
    }

    @Test fun `una consulta que no bajo nada se apunta AEMET sin cambios aunque news sea true`() = runTest {
        // Decisión de la coordinadora (review-p1): news == true con 0 bytes (sin fuente conocida y todo de la caché).
        val s = logged(warnings = KnownAwareWarnings(news = true, bytes = 0))
        s.repo.refresh(Trigger.APP)

        val line = s.lines().single()
        assertEquals(AemetOutcome.UNCHANGED, line.aemet)
        assertEquals(0L, line.aemetBytes)
    }

    @Test fun `AEMET falla y se apunta FAILED con la prevision bien`() = runTest {
        val s = logged(warnings = KnownAwareWarnings(failing = true))
        assertEquals(RefreshResult.Ok, s.repo.refresh(Trigger.APP))

        assertEquals(
            listOf(RefreshLogEntry(now, RefreshOrigin.APP, RefreshOutcome.OK, AemetOutcome.FAILED, 0)),
            s.lines(),
        )
    }

    @Test fun `fuera de Espana AEMET no aplica`() = runTest {
        val aemet = KnownAwareWarnings()
        val s = logged(location = FakeLocation(LatLon(45.0, 6.0), null), warnings = aemet)
        s.repo.refresh(Trigger.APP)

        assertEquals(emptyList<LatLon>(), aemet.requested)
        assertEquals(
            listOf(RefreshLogEntry(now, RefreshOrigin.APP, RefreshOutcome.OK, AemetOutcome.NOT_APPLICABLE, 0)),
            s.lines(),
        )
    }

    @Test fun `sin ubicacion se apunta NO_LOCATION y AEMET no aplica`() = runTest {
        val s = logged(location = FakeLocation(null, null))
        assertEquals(RefreshResult.NoLocation, s.repo.refresh(Trigger.APP))

        assertEquals(
            listOf(RefreshLogEntry(now, RefreshOrigin.APP, RefreshOutcome.NO_LOCATION, AemetOutcome.NOT_APPLICABLE, 0)),
            s.lines(),
        )
    }

    @Test fun `si Open-Meteo falla se apunta OFFLINE y AEMET no aplica, aunque contestara`() = runTest {
        val s = logged(
            weather = FakeWeather(failing = true, now = now),
            warnings = KnownAwareWarnings(news = true, bytes = 352_000),
        )
        assertEquals(RefreshResult.Offline, s.repo.refresh(Trigger.APP, RefreshOrigin.ALARM))

        assertEquals(
            listOf(RefreshLogEntry(now, RefreshOrigin.ALARM, RefreshOutcome.OFFLINE, AemetOutcome.NOT_APPLICABLE, 0)),
            s.lines(),
        )
    }

    @Test fun `refreshSaved no escribe nada en el registro`() = runTest {
        val dir = tmp.newFolder()
        val saved = SavedPlacesRepository(
            SavedPlacesStore(dir.resolve("saved_places.json")), PlaceStores(dir.resolve("places")),
            nowSeconds = { now }, scope = backgroundScope,
        )
        val s = logged(saved = saved)
        val place = saved.add("Ciudad Guardada", "ES", LatLon(37.39, -5.98))

        assertEquals(RefreshResult.Ok, s.repo.refreshSaved(place))

        assertEquals(emptyList<RefreshLogEntry>(), s.lines())
        assertFalse(s.file.exists())
    }

    @Test fun `si el registro no se puede escribir el refresco sale igual y el widget ya se repinto (H14)`() = runTest {
        val blocker = tmp.newFile("no-es-carpeta")
        val events = mutableListOf<String>()
        val s = logged(file = blocker.resolve("refresh_log.jsonl"), onUpdated = { events += "widget" })

        assertEquals(RefreshResult.Ok, s.repo.refresh(Trigger.APP))

        assertEquals(listOf("widget"), events)
        // Y el siguiente refresco tampoco se rompe.
        assertEquals(RefreshResult.Ok, s.repo.refresh(Trigger.BACKGROUND))
        assertEquals(listOf("widget", "widget"), events)
    }

    @Test fun `el registro se escribe despues de repintar el widget (H14)`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        var existedWhenWidgetPainted: Boolean? = null
        val s = logged(file = file, onUpdated = { existedWhenWidgetPainted = file.exists() })

        s.repo.refresh(Trigger.APP)

        assertEquals(false, existedWhenWidgetPainted)
        assertTrue(file.exists())
    }

    @Test fun `el repositorio de la prevision no conoce la IA (016 H12)`() {
        // Test de fuente, como D24GuardTest: el parte va fuera, así el resultado de refresh() y su línea del registro
        // son los mismos con y sin IA.
        val source = java.io.File("src/main/java/com/trastealabs/tiempo/refresh/WeatherRepository.kt").readText()
        assertFalse("ReportRepository" in source)
        assertFalse("data.ai" in source)
        assertFalse("AiSettings" in source)
    }
}
