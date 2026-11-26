package com.trastealabs.tiempo.ui

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.TestData.at
import com.trastealabs.tiempo.data.AirSource
import com.trastealabs.tiempo.data.LocationSource
import com.trastealabs.tiempo.data.PlaceLookup
import com.trastealabs.tiempo.data.ReliabilitySource
import com.trastealabs.tiempo.data.ReliabilityStore
import com.trastealabs.tiempo.data.SnapshotStore
import com.trastealabs.tiempo.data.WarningSource
import com.trastealabs.tiempo.data.WeatherSource
import com.trastealabs.tiempo.domain.AirHour
import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.PollenLevel
import com.trastealabs.tiempo.domain.airNow
import com.trastealabs.tiempo.domain.pollenToday
import com.trastealabs.tiempo.domain.Place
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.moonInfo
import com.trastealabs.tiempo.domain.sunInfo
import com.trastealabs.tiempo.refresh.ReliabilityRepository
import com.trastealabs.tiempo.refresh.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import com.trastealabs.tiempo.data.PlaceStores
import com.trastealabs.tiempo.data.key
import com.trastealabs.tiempo.data.SavedPlacesStore
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.toPlace
import com.trastealabs.tiempo.refresh.SavedPlacesRepository
import org.junit.Assert.assertTrue
import android.content.SharedPreferences
import com.trastealabs.tiempo.data.ReportStore
import com.trastealabs.tiempo.data.ai.AiException
import com.trastealabs.tiempo.data.ai.AiFailure
import com.trastealabs.tiempo.data.ai.AiSettings
import com.trastealabs.tiempo.data.ai.KeyVault
import com.trastealabs.tiempo.data.ai.ReportReply
import com.trastealabs.tiempo.data.ai.ReportRequest
import com.trastealabs.tiempo.data.ai.ReportWriter
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import java.time.ZoneId
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.refresh.ReportRepository
import org.junit.Assert.assertFalse

/** H8: con la app abierta, la ficha pasa de sol a luna al anochecer sin refrescar. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    // viewModelScope va en Dispatchers.Main: el de la prueba, con el reloj virtual, hasta después de runTest.
    private val main = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() = Dispatchers.resetMain()

    // Dobles como los de WeatherRepositoryTest: aquí nadie debería pedir nada.
    private class FakeWeather : WeatherSource {
        var fetches = 0
        override suspend fun fetch(position: LatLon): WeatherSnapshot { fetches++; throw IOException("sin red") }
    }

    private class FakeLocation(private val at: LatLon? = null) : LocationSource {
        override suspend fun current(): LatLon? = at
        override suspend fun lastKnown(): LatLon? = null
        override fun hasBackgroundPermission() = false
    }

    private class FakeWarnings : WarningSource {
        override suspend fun fetchFor(position: LatLon): List<Warning> = emptyList()
    }

    private class FakeAir : AirSource {
        override suspend fun fetch(position: LatLon): AirQuality = throw IOException("sin red")
    }

    private class FakeReliability : ReliabilitySource {
        override suspend fun fetch(position: LatLon): Reliability = throw IOException("sin red")
    }

    private val places = object : PlaceLookup {
        override fun nearest(position: LatLon) = Place("Villa Ejemplo", "ES", position)
    }

    /** 3-nov-2026: anochece a las 18:21; la luna sale a las 02:47 del 4 y se pone a las 15:43. */
    private fun snapshot(): WeatherSnapshot {
        val base = TestData.snapshot(at(2026, 11, 3, 12, 0))
        val daily = base.daily.mapIndexed { i, d ->
            d.copy(
                sunrise = at(2026, 11, 3 + i, 7, 58), sunset = at(2026, 11, 3 + i, 18, 21),
                moonrise = at(2026, 11, 3 + i, 2, 47), moonset = at(2026, 11, 3 + i, 15, 43),
                moonPhase = 0.807 + i * 0.01,
            )
        }
        return base.copy(daily = daily)
    }

    @Test fun `el registro de refrescos llega a Content y se actualiza con cada linea nueva (015 P2)`() = runTest(main) {
        val start = at(2026, 11, 3, 12, 0)
        val dir = tmp.newFolder()
        val snapshots = SnapshotStore(dir.resolve("snapshot.json"))
        snapshots.write(StoredState(snapshot(), lastAttemptFailed = false))
        val repository = WeatherRepository(FakeWeather(), FakeWarnings(), FakeAir(), places, FakeLocation(), snapshots)
        val reliability = ReliabilityRepository(
            FakeReliability(), ReliabilityStore(dir.resolve("reliability.json")), snapshots, scope = backgroundScope,
        )
        val log = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.trastealabs.tiempo.data.RefreshLogEntry>())
        val vm = MainViewModel(
            repository, reliability, hasLocationPermission = { true },
            refreshLog = log,
            nowSeconds = { start + testScheduler.currentTime / 1000 },
            zone = { TestData.zone },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(emptyList<com.trastealabs.tiempo.data.RefreshLogEntry>(), (vm.uiState.value as UiState.Content).refreshes)

        val entry = com.trastealabs.tiempo.data.RefreshLogEntry(
            start - 600, com.trastealabs.tiempo.refresh.RefreshOrigin.APP, com.trastealabs.tiempo.data.RefreshOutcome.OK,
            com.trastealabs.tiempo.refresh.AemetOutcome.UNCHANGED, 0,
        )
        log.value = listOf(entry)
        runCurrent()

        assertEquals(listOf(entry), (vm.uiState.value as UiState.Content).refreshes)
    }

    @Test fun `sin registro configurado Content lleva la lista vacia`() = runTest(main) {
        val start = at(2026, 11, 3, 12, 0)
        val dir = tmp.newFolder()
        val snapshots = SnapshotStore(dir.resolve("snapshot.json"))
        snapshots.write(StoredState(snapshot(), lastAttemptFailed = false))
        val repository = WeatherRepository(FakeWeather(), FakeWarnings(), FakeAir(), places, FakeLocation(), snapshots)
        val reliability = ReliabilityRepository(
            FakeReliability(), ReliabilityStore(dir.resolve("reliability.json")), snapshots, scope = backgroundScope,
        )
        val vm = MainViewModel(repository, reliability, hasLocationPermission = { true }, nowSeconds = { start })
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(emptyList<com.trastealabs.tiempo.data.RefreshLogEntry>(), (vm.uiState.value as UiState.Content).refreshes)
    }

    @Test fun `con el reloj pasando el anochecer, now se recalcula sin refrescar y sale la luna`() = runTest(main) {
        run {
            val start = at(2026, 11, 3, 18, 0)
            val dir = tmp.newFolder()
            val snapshots = SnapshotStore(dir.resolve("snapshot.json"))
            snapshots.write(StoredState(snapshot(), lastAttemptFailed = false))
            val weather = FakeWeather()
            val repository = WeatherRepository(weather, FakeWarnings(), FakeAir(), places, FakeLocation(), snapshots)
            val reliability = ReliabilityRepository(
                FakeReliability(), ReliabilityStore(dir.resolve("reliability.json")), snapshots, scope = backgroundScope,
            )
            val vm = MainViewModel(
                repository, reliability, hasLocationPermission = { true },
                nowSeconds = { start + testScheduler.currentTime / 1000 },
                zone = { TestData.zone },
            )
            backgroundScope.launch { vm.uiState.collect {} }
            runCurrent()

            val before = vm.uiState.value as UiState.Content
            assertEquals(start, before.now)
            assertNotNull(sunInfo(before.snapshot, before.now))
            assertNull(moonInfo(before.snapshot, before.now))

            // Un segundo antes del latido (anochecer + 1 s) sigue igual.
            advanceTimeBy(21 * 60 * 1000L)
            runCurrent()
            assertEquals(start, (vm.uiState.value as UiState.Content).now)

            advanceTimeBy(1_000L)
            runCurrent()
            val after = vm.uiState.value as UiState.Content
            assertEquals(at(2026, 11, 3, 18, 21) + 1, after.now)
            assertEquals("Sale a las 02:47 · se pone a las 15:43", moonInfo(after.snapshot, after.now)!!.events)
            assertEquals(0, weather.fetches)
        }
    }

    /**
     * Sin sol ni luna (datos de antes del 002), para que solo lata la hora del aire y la medianoche. Aire desde las
     * 00:00 del 5-may-2026: índice 20 + número de la hora; olivo alto el 5 y bajo el 6.
     */
    private fun snapshotWithAir(): WeatherSnapshot {
        val start = at(2026, 5, 5, 0, 0)
        val hours = (0 until 98).map { i ->
            AirHour(start + i * 3600L, aqi = 20 + i, ozoneIndex = 20 + i, olivePollen = if (i < 24) 300.0 else 30.0, grassPollen = 0.0)
        }
        return TestData.snapshot(at(2026, 5, 5, 12, 0)).copy(air = AirQuality(TestData.place.position, start, hours))
    }

    private suspend fun vmWithAir(start: Long, scope: kotlinx.coroutines.test.TestScope): MainViewModel {
        val dir = tmp.newFolder()
        val snapshots = SnapshotStore(dir.resolve("snapshot.json"))
        snapshots.write(StoredState(snapshotWithAir(), lastAttemptFailed = false))
        val repository = WeatherRepository(FakeWeather(), FakeWarnings(), FakeAir(), places, FakeLocation(), snapshots)
        val reliability = ReliabilityRepository(
            FakeReliability(), ReliabilityStore(dir.resolve("reliability.json")), snapshots, scope = scope.backgroundScope,
        )
        return MainViewModel(
            repository, reliability, hasLocationPermission = { true },
            nowSeconds = { start + scope.testScheduler.currentTime / 1000 },
            zone = { TestData.zone },
        )
    }

    @Test fun `al pasar la hora en punto la cifra del aire es la de la hora nueva (H6 del 005)`() = runTest(main) {
        val start = at(2026, 5, 5, 12, 40)
        val vm = vmWithAir(start, this)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val before = vm.uiState.value as UiState.Content
        assertEquals(32, airNow(before.snapshot, before.now)!!.aqi)

        advanceTimeBy(20 * 60 * 1000L)   // 13:00:00, aún no
        runCurrent()
        assertEquals(start, (vm.uiState.value as UiState.Content).now)

        advanceTimeBy(1_000L)
        runCurrent()
        val after = vm.uiState.value as UiState.Content
        assertEquals(at(2026, 5, 5, 13, 0) + 1, after.now)
        assertEquals(33, airNow(after.snapshot, after.now)!!.aqi)
    }

    @Test fun `al pasar la medianoche el polen de hoy es el del dia nuevo (H8 del 005)`() = runTest(main) {
        val start = at(2026, 5, 5, 23, 59)
        val vm = vmWithAir(start, this)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()

        val before = vm.uiState.value as UiState.Content
        assertEquals(PollenLevel.HIGH, pollenToday(before.snapshot, before.now)!!.types.single().second)

        advanceTimeBy(61_000L)
        runCurrent()
        val after = vm.uiState.value as UiState.Content
        assertEquals(at(2026, 5, 6, 0, 0) + 1, after.now)
        assertEquals(PollenLevel.LOW, pollenToday(after.snapshot, after.now)!!.types.single().second)
    }

    // --- 006 · Sitios guardados ---

    /**
     * Los almacenes leen y escriben en Dispatchers.IO (hilos de verdad), que el reloj virtual no espera: se deja correr
     * la cola de la prueba y se da a esos hilos un momento, varias veces, hasta que todo se asienta.
     */
    private fun kotlinx.coroutines.test.TestScope.settle() {
        repeat(30) {
            runCurrent()
            Thread.sleep(5)
        }
        runCurrent()
    }

    /** Previsión por posición: la temperatura de ahora es la latitud, para distinguir cada sitio. */
    private class PlaceWeather(val now: () -> Long) : WeatherSource {
        val requested = mutableListOf<LatLon>()
        var failing = false
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override suspend fun fetch(position: LatLon): WeatherSnapshot {
            requested += position
            gate?.await()
            if (failing) throw IOException("sin red")
            return TestData.snapshot(now(), currentC = position.lat).copy(place = Place("", "", position))
        }
    }

    private class CountingReliability : ReliabilitySource {
        val requested = mutableListOf<LatLon>()
        override suspend fun fetch(position: LatLon): Reliability {
            requested += position
            return Reliability(position, "Europe/Madrid", 0, emptyList())
        }
    }

    private class World(
        val vm: MainViewModel,
        val weather: PlaceWeather,
        val rel: CountingReliability,
        val saved: SavedPlacesRepository,
        val dir: java.io.File,
    )

    private val here = LatLon(40.0, -3.0)
    private val placeA = LatLon(21.0, -3.0)
    private val placeB = LatLon(5.0, -3.0)

    private suspend fun kotlinx.coroutines.test.TestScope.world(
        start: Long,
        hereSnapshot: WeatherSnapshot? = TestData.snapshot(start, currentC = 12.0),
        phoneZone: java.time.ZoneId = TestData.zone,
        prepare: suspend (java.io.File) -> Unit = {},
        search: com.trastealabs.tiempo.data.PlaceSearch? = null,
        ai: ((() -> Long) -> AiKit)? = null,
        hereAt: LatLon? = null,
        setup: suspend World.() -> Unit = {},
    ): World {
        val dir = tmp.newFolder()
        prepare(dir)
        val snapshots = SnapshotStore(dir.resolve("snapshot.json"))
        if (hereSnapshot != null) snapshots.write(StoredState(hereSnapshot, lastAttemptFailed = false))
        val clock = { start + testScheduler.currentTime / 1000 }
        val saved = SavedPlacesRepository(
            SavedPlacesStore(dir.resolve("saved_places.json")), PlaceStores(dir.resolve("places")),
            nowSeconds = clock, scope = backgroundScope,
        )
        val weather = PlaceWeather(clock)
        val rel = CountingReliability()
        val repository = WeatherRepository(weather, FakeWarnings(), FakeAir(), places, FakeLocation(hereAt), snapshots, nowSeconds = clock, saved = saved)
        val reliability = ReliabilityRepository(
            rel, ReliabilityStore(dir.resolve("reliability.json")), snapshots, scope = backgroundScope, saved = saved,
        )
        val kit = ai?.invoke(clock)
        val vm = MainViewModel(
            repository, reliability, hasLocationPermission = { true },
            nowSeconds = clock, zone = { phoneZone }, saved = saved, placeSearch = search,
            reports = kit?.reports, reportStore = kit?.store, aiSettings = kit?.settings, keyVault = kit?.vault,
            ioDispatcher = main,
        )
        // Los datos se preparan antes de escuchar: mientras la prueba espera al disco, el reloj virtual correría solo
        // con los latidos de medianoche.
        val world = World(vm, weather, rel, saved, dir)
        world.setup()
        backgroundScope.launch { vm.uiState.collect {} }
        backgroundScope.launch { vm.placeCards.collect {} }
        return world
    }

    /** Guarda un sitio con datos de hace [age] segundos. */
    private suspend fun World.addWithData(name: String, position: LatLon, now: Long, age: Long, zoneId: String = "Europe/Madrid"): SavedPlace {
        val place = (saved.save(name, "Región", "ES", position) as SavedPlacesRepository.SaveResult.Saved).place
        val snap = TestData.snapshot(now, currentC = position.lat, fetchedAt = now - age, place = place.toPlace()).copy(zoneId = zoneId)
        saved.stores.snapshot(place.id).write(StoredState(snap, false))
        return place
    }

    private fun content(vm: MainViewModel) = vm.uiState.value as UiState.Content

    @Test fun `empieza siempre en donde estoy (H9)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val w = world(start) {
            addWithData("Ciudad A", placeA, start, age = 60)
        }
        settle()
        assertEquals(PlaceRef.Here, w.vm.selected.value)
        assertEquals(PlaceRef.Here, content(w.vm).place)
        assertNull(content(w.vm).savedName)
        assertEquals(12.0, content(w.vm).snapshot.current.temperatureC, 0.0)
    }

    @Test fun `al ir y volver entre sitios cada uno trae solo lo suyo (H10)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var a: SavedPlace
        lateinit var b: SavedPlace
        val w = world(start) {
            a = addWithData("Ciudad A", placeA, start, age = 60)
            b = addWithData("Ciudad B", placeB, start, age = 60)
        }
        settle()

        for ((ref, temp, name) in listOf(
            Triple(PlaceRef.Saved(a.id), 21.0, "Ciudad A"),
            Triple(PlaceRef.Saved(b.id), 5.0, "Ciudad B"),
            Triple(PlaceRef.Saved(a.id), 21.0, "Ciudad A"),
            Triple(PlaceRef.Here, 12.0, null),
        )) {
            w.vm.select(ref)
            settle()
            val c = content(w.vm)
            assertEquals(ref, c.place)
            assertEquals(temp, c.snapshot.current.temperatureC, 0.0)
            assertEquals(name, c.savedName)
        }
        assertEquals(emptyList<LatLon>(), w.weather.requested)
    }

    @Test fun `un refresco lento de A que acaba ya en B no ensena A (H10)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var a: SavedPlace
        lateinit var b: SavedPlace
        val w = world(start) {
            a = addWithData("Ciudad A", placeA, start, age = 3600)
            b = addWithData("Ciudad B", placeB, start, age = 60)
        }
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        w.weather.gate = gate
        settle()

        w.vm.select(PlaceRef.Saved(a.id))
        settle()
        assertTrue(content(w.vm).refreshing)
        w.vm.select(PlaceRef.Saved(b.id))
        settle()
        gate.complete(Unit)
        settle()
        advanceTimeBy(1_000)
        settle()

        val c = content(w.vm)
        assertEquals(PlaceRef.Saved(b.id), c.place)
        assertEquals(5.0, c.snapshot.current.temperatureC, 0.0)
        assertFalse(c.refreshing)
    }

    @Test fun `al abrir un guardado de hace 10 min no pide y de hace 1 h si, con la fiabilidad despues (H11)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 30)
        lateinit var fresh: SavedPlace
        lateinit var stale: SavedPlace
        val w = world(start) {
            fresh = addWithData("Ciudad A", placeA, start, age = 10 * 60)
            stale = addWithData("Ciudad B", placeB, start, age = 3600)
        }
        settle()

        w.vm.select(PlaceRef.Saved(fresh.id))
        settle()
        assertEquals(emptyList<LatLon>(), w.weather.requested)
        assertEquals(emptyList<LatLon>(), w.rel.requested)

        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        w.weather.gate = gate
        w.vm.select(PlaceRef.Saved(stale.id))
        settle()
        // Mientras, lo guardado con «Actualizando…».
        assertEquals(PlaceRef.Saved(stale.id), content(w.vm).place)
        assertTrue(content(w.vm).refreshing)
        gate.complete(Unit)
        settle()
        assertEquals(listOf(placeB), w.weather.requested)
        assertEquals(listOf(placeB), w.rel.requested)
        assertFalse(content(w.vm).refreshing)
    }

    @Test fun `deslizar en un guardado pide siempre (H11)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 30)
        lateinit var a: SavedPlace
        val w = world(start) {
            a = addWithData("Ciudad A", placeA, start, age = 60)
        }
        settle()
        w.vm.select(PlaceRef.Saved(a.id))
        settle()
        assertEquals(emptyList<LatLon>(), w.weather.requested)

        w.vm.refresh()
        settle()
        assertEquals(listOf(placeA), w.weather.requested)
    }

    @Test fun `volver a la app en un guardado reciente no pide`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 30)
        lateinit var a: SavedPlace
        val w = world(start) {
            a = addWithData("Ciudad A", placeA, start, age = 10 * 60)
        }
        settle()
        w.vm.select(PlaceRef.Saved(a.id))
        settle()
        w.vm.onStart()
        settle()
        assertEquals(emptyList<LatLon>(), w.weather.requested)

        // Pasados 30 min más, sí.
        advanceTimeBy(21 * 60 * 1000L)
        w.vm.onStart()
        settle()
        assertEquals(listOf(placeA), w.weather.requested)
    }

    @Test fun `recien guardado descarga y, si falla, dice que no tiene datos`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 30)
        lateinit var place: SavedPlace
        val w = world(start) {
            place = (saved.save("Ciudad Nueva", "Región", "ES", placeA) as SavedPlacesRepository.SaveResult.Saved).place
        }
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        w.weather.gate = gate
        w.weather.failing = true
        settle()

        w.vm.select(PlaceRef.Saved(place.id))
        settle()
        assertEquals(UiState.SavedDownloading(place), w.vm.uiState.value)
        gate.complete(Unit)
        settle()
        assertEquals(UiState.SavedNoData(place), w.vm.uiState.value)

        // Reintentar con conexión: ya con datos.
        w.weather.failing = false
        w.vm.refresh()
        settle()
        assertEquals(21.0, content(w.vm).snapshot.current.temperatureC, 0.0)
    }

    @Test fun `borrar el que se ve vuelve a donde estoy y borrar otro no cambia nada`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 30)
        lateinit var a: SavedPlace
        lateinit var b: SavedPlace
        val w = world(start) {
            a = addWithData("Ciudad A", placeA, start, age = 60)
            b = addWithData("Ciudad B", placeB, start, age = 60)
        }
        settle()

        w.vm.select(PlaceRef.Saved(a.id))
        settle()
        w.vm.delete(b.id)
        settle()
        assertEquals(PlaceRef.Saved(a.id), w.vm.selected.value)
        assertEquals(listOf(a), w.saved.list())

        w.vm.delete(a.id)
        settle()
        assertEquals(PlaceRef.Here, w.vm.selected.value)
        assertEquals(PlaceRef.Here, content(w.vm).place)
        assertEquals(emptyList<SavedPlace>(), w.saved.list())
        assertFalse(w.dir.resolve("places/${a.id}").exists())
    }

    @Test fun `un guardado en otra zona cambia de dia a su medianoche, no a la del movil`() = runTest(main) {
        val ba = java.time.ZoneId.of("America/Argentina/Buenos_Aires")
        val start = java.time.ZonedDateTime.of(2026, 11, 4, 23, 59, 0, 0, ba).toEpochSecond()
        // «Donde estoy» sin sol ni luna, para que solo latan las medianoches.
        lateinit var place: SavedPlace
        val w = world(start) {
            place = (saved.save("Ciudad Lejana", "Región", "AR", placeA) as SavedPlacesRepository.SaveResult.Saved).place
            val snap = TestData.snapshot(start, fetchedAt = start - 60, place = place.toPlace()).copy(zoneId = ba.id)
            saved.stores.snapshot(place.id).write(StoredState(snap, false))
        }
        settle()
        w.vm.select(PlaceRef.Saved(place.id))
        settle()
        val midnight = java.time.ZonedDateTime.of(2026, 11, 5, 0, 0, 0, 0, ba).toEpochSecond()
        val first = content(w.vm).now
        assertTrue(first < midnight)

        // Hasta la medianoche de allí, nada (la del móvil, Madrid, es 4 h después).
        advanceTimeBy((midnight - first) * 1000L)
        settle()
        assertEquals(first, content(w.vm).now)

        advanceTimeBy(2_000L)
        settle()
        val now = content(w.vm).now
        assertEquals(midnight + 1, now)
        assertEquals("2026-11-05", java.time.Instant.ofEpochSecond(now).atZone(ba).toLocalDate().toString())
        assertEquals(emptyList<LatLon>(), w.weather.requested)
    }

    @Test fun `las postales salen con donde estoy primero y los guardados en su orden`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 30)
        lateinit var a: SavedPlace
        lateinit var b: SavedPlace
        val w = world(start, hereSnapshot = TestData.snapshot(start).copy(place = Place("Villa Ejemplo", "ES", here))) {
            a = addWithData("Ciudad A", placeA, start, age = 60)
            b = addWithData("Ciudad B", placeB, start, age = 3 * 3600)
        }
        settle()
        w.vm.select(PlaceRef.Saved(a.id))
        settle()

        val cards = w.vm.placeCards.value
        assertEquals(listOf(PlaceRef.Here, PlaceRef.Saved(a.id), PlaceRef.Saved(b.id)), cards.map { it.ref })
        assertEquals(listOf("Villa Ejemplo", "Ciudad A", "Ciudad B"), cards.map { it.name })
        assertEquals(listOf(false, true, false), cards.map { it.isShown })
        assertEquals(listOf(12, 21, 5), cards.map { it.temp })
        assertNull(cards[1].ageLabel)
        assertEquals("Datos de las 07:30", cards[2].ageLabel)
    }

    @Test fun `instalada encima de la 005 donde estoy sale con todo lo guardado (H14)`() = runTest(main) {
        val fetched = 1793779920L   // el fetchedAt de snapshot_005.json (4-nov-2026 09:12)
        val start = fetched + 120
        val w = world(start, hereSnapshot = null, prepare = { dir ->
            dir.resolve("snapshot.json").writeText(javaClass.getResource("/snapshot_005.json")!!.readText())
            dir.resolve("reliability.json").writeText(javaClass.getResource("/reliability_005.json")!!.readText())
        })
        settle()

        assertFalse(w.dir.resolve("saved_places.json").exists())
        val c = content(w.vm)
        assertEquals(PlaceRef.Here, c.place)
        assertEquals("Villa Ejemplo", c.snapshot.place.name)
        assertEquals(1, c.snapshot.warnings.size)
        assertNotNull(c.snapshot.air)
        assertNotNull(c.reliability)
        assertEquals(1, w.vm.placeCards.value.size)
    }

    @Test fun `las postales se recalculan solas a medianoche`() = runTest(main) {
        val start = at(2026, 11, 4, 23, 59)
        lateinit var a: SavedPlace
        val w = world(start) {
            a = addWithData("Ciudad A", placeA, start, age = 3600)   // de las 22:59 del 4
        }
        settle()
        assertEquals("Datos de las 22:59", w.vm.placeCards.value[1].ageLabel)

        advanceTimeBy(61_000L)
        settle()
        assertEquals("Datos de ayer", w.vm.placeCards.value[1].ageLabel)
        assertEquals(a.name, w.vm.placeCards.value[1].name)
    }

    // --- Búsqueda (006 P2) ---

    /** Una lista pequeña en línea: tres «Villanueva» de España, una de fuera y «Córdoba». */
    private fun smallSearch() = com.trastealabs.tiempo.data.PlaceSearch(
        com.trastealabs.tiempo.data.PlaceIndex {
            listOf(
                "R\tES\tCórdoba", "R\tES\tBadajoz", "R\tES\tZaragoza", "R\tMX\tOaxaca",
                "Villanueva de Córdoba\t0\t8700\t38.32\t-4.62",
                "Villanueva de la Serena\t1\t25800\t38.98\t-5.80",
                "Villanueva de Gállego\t2\t5400\t41.77\t-0.82",
                "Villanueva\t3\t1200\t17.00\t-96.00",
                "Córdoba\t0\t325708\t37.89\t-4.77",
            ).joinToString("\n", postfix = "\n").byteInputStream()
        },
    )

    private fun hit(vm: MainViewModel, name: String): com.trastealabs.tiempo.data.PlaceHit {
        val r = (vm.search.value.state as SearchState.Results).results
        return (r.spain + r.world).first { it.name == name }
    }

    @Test fun `setQuery con cada letra actualiza search y con menos de 2 letras da Hint`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val w = world(start, search = smallSearch())
        backgroundScope.launch { w.vm.search.collect {} }
        settle()
        assertEquals(SearchState.Hint, w.vm.search.value.state)

        w.vm.setQuery("v")
        settle()
        assertEquals("v", w.vm.search.value.query)
        assertEquals(SearchState.Hint, w.vm.search.value.state)

        val sizes = mutableListOf<Int>()
        for (q in listOf("vi", "villanueva", "villanueva de la")) {
            w.vm.setQuery(q)
            settle()
            assertEquals(q, w.vm.search.value.query)
            val r = (w.vm.search.value.state as SearchState.Results).results
            sizes += r.spain.size + r.world.size
        }
        assertEquals(listOf(4, 4, 1), sizes)
        assertEquals("Villanueva de la Serena", hit(w.vm, "Villanueva de la Serena").name)
    }

    @Test fun `sin resultados da NoResults`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val w = world(start, search = smallSearch())
        backgroundScope.launch { w.vm.search.collect {} }
        w.vm.setQuery("sevila")
        settle()
        assertEquals(SearchState.NoResults, w.vm.search.value.state)
    }

    @Test fun `savedKeys marca los guardados y full sale con 10`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val w = world(start, search = smallSearch())
        backgroundScope.launch { w.vm.search.collect {} }
        w.vm.setQuery("villanueva")
        settle()
        val serena = hit(w.vm, "Villanueva de la Serena")
        assertFalse(serena.key in w.vm.search.value.savedKeys)
        assertFalse(w.vm.search.value.full)

        w.saved.save(serena.name, serena.region, serena.countryCode, serena.position)
        settle()
        assertTrue(serena.key in w.vm.search.value.savedKeys)
        assertFalse(hit(w.vm, "Villanueva de Córdoba").key in w.vm.search.value.savedKeys)

        for (i in 1..9) w.saved.save("Sitio $i", "", "ES", LatLon(30.0 + i, -3.0))
        settle()
        assertTrue(w.vm.search.value.full)
    }

    @Test fun `saveAndOpen guarda y abre, si ya estaba solo abre y con 10 no hace nada (H6)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val w = world(start, search = smallSearch())
        backgroundScope.launch { w.vm.search.collect {} }
        w.vm.setQuery("villanueva")
        settle()

        val serena = hit(w.vm, "Villanueva de la Serena")
        w.vm.saveAndOpen(serena)
        settle()
        val list = w.saved.list()
        assertEquals(1, list.size)
        assertEquals("Villanueva de la Serena", list[0].name)
        assertEquals("Badajoz", list[0].region)
        assertEquals("ES", list[0].countryCode)
        assertEquals(LatLon(38.98, -5.80), list[0].position)
        assertEquals(PlaceRef.Saved(list[0].id), w.vm.selected.value)

        // Repetido: no se guarda otra vez, solo se abre.
        w.vm.select(PlaceRef.Here)
        w.vm.saveAndOpen(serena)
        settle()
        assertEquals(1, w.saved.list().size)
        assertEquals(PlaceRef.Saved(list[0].id), w.vm.selected.value)

        // Con 10, el undécimo no se guarda y no se cambia de sitio.
        for (i in 1..9) w.saved.save("Sitio $i", "", "ES", LatLon(30.0 + i, -3.0))
        w.vm.select(PlaceRef.Here)
        settle()
        w.vm.saveAndOpen(hit(w.vm, "Villanueva de Gállego"))
        settle()
        assertEquals(10, w.saved.list().size)
        assertFalse(w.saved.list().any { it.name == "Villanueva de Gállego" })
        assertEquals(PlaceRef.Here, w.vm.selected.value)
    }

    @Test fun `escribir letra a letra no hace ninguna peticion de red (H5)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val http = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain -> requests.incrementAndGet(); chain.proceed(chain.request()) }
            .build()
        val dir = tmp.newFolder()
        val snapshots = SnapshotStore(dir.resolve("snapshot.json"))
        snapshots.write(StoredState(TestData.snapshot(start, currentC = 12.0), lastAttemptFailed = false))
        val clock = { start + testScheduler.currentTime / 1000 }
        val saved = SavedPlacesRepository(
            SavedPlacesStore(dir.resolve("saved_places.json")), PlaceStores(dir.resolve("places")),
            nowSeconds = clock, scope = backgroundScope,
        )
        val index = com.trastealabs.tiempo.data.PlaceIndex {
            java.util.zip.GZIPInputStream(java.io.File("src/main/assets/places.tsv.gz").inputStream())
        }
        val repository = WeatherRepository(
            com.trastealabs.tiempo.data.OpenMeteoClient(http), com.trastealabs.tiempo.data.AemetClient(http),
            com.trastealabs.tiempo.data.OpenMeteoAirClient(http), com.trastealabs.tiempo.data.AssetPlaceLookup(index),
            FakeLocation(), snapshots, nowSeconds = clock, saved = saved,
        )
        val reliability = ReliabilityRepository(
            com.trastealabs.tiempo.data.OpenMeteoReliabilityClient(http), ReliabilityStore(dir.resolve("reliability.json")),
            snapshots, scope = backgroundScope, saved = saved,
        )
        val vm = MainViewModel(
            repository, reliability, hasLocationPermission = { true }, nowSeconds = clock, zone = { TestData.zone },
            saved = saved, placeSearch = com.trastealabs.tiempo.data.PlaceSearch(index),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        backgroundScope.launch { vm.search.collect {} }
        settle()
        index.load()

        val word = "villanueva"
        for (n in 1..word.length) {
            vm.setQuery(word.take(n))
            settle()
        }
        val r = (vm.search.value.state as SearchState.Results).results
        assertTrue(r.spain.isNotEmpty())
        assertEquals(0, requests.get())
    }

    @Test fun `con la lista sin cargar y tardando sale Loading y luego los resultados (H4)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val gate = java.util.concurrent.CountDownLatch(1)
        val slow = com.trastealabs.tiempo.data.PlaceSearch(
            com.trastealabs.tiempo.data.PlaceIndex {
                gate.await()
                "R\tES\tCórdoba\nVillanueva de Córdoba\t0\t8700\t38.32\t-4.62\n".byteInputStream()
            },
        )
        val w = world(start, search = slow)
        backgroundScope.launch { w.vm.search.collect {} }
        w.vm.setQuery("villa")
        settle()
        // Los 150 ms de espera van con el reloj virtual de la prueba.
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(SearchState.Loading, w.vm.search.value.state)

        gate.countDown()
        settle()
        val r = (w.vm.search.value.state as SearchState.Results).results
        assertEquals("Villanueva de Córdoba", r.spain.single().name)
    }

    @Test fun `si la lista no se puede leer la busqueda da NoResults sin romper`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val broken = com.trastealabs.tiempo.data.PlaceSearch(com.trastealabs.tiempo.data.PlaceIndex { throw IOException("sin asset") })
        val w = world(start, search = broken)
        backgroundScope.launch { w.vm.search.collect {} }
        w.vm.setQuery("villa")
        settle()
        assertEquals(SearchState.NoResults, w.vm.search.value.state)
    }

    // ---------- 016 P3: el parte con IA y la sección «IA» de Ajustes ----------

    /** SharedPreferences en memoria: AiSettings solo lee y escribe valores sueltos. */
    private class MemPrefs : SharedPreferences {
        val map = HashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
        override fun contains(key: String) = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private val removed = HashSet<String>()
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { removed += key }
            override fun clear() = apply { map.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                removed.forEach { map.remove(it) }
                map.putAll(pending)
            }
        }
    }

    private class MapVault : KeyVault {
        val keys = HashMap<AiProvider, String>()
        var failPut = false
        override fun get(p: AiProvider) = keys[p]
        override fun put(p: AiProvider, key: String) { if (failPut) throw IllegalStateException("keystore"); keys[p] = key }
        override fun clear(p: AiProvider) { keys.remove(p) }
        override fun hint(p: AiProvider) = keys[p]?.takeLast(4)
    }

    private class AiKit(
        val reports: ReportRepository,
        val store: ReportStore,
        val settings: AiSettings,
        val vault: MapVault,
        val writer: ScriptedWriter,
    )

    /** Cuenta las llamadas; contesta con [reply] o lanza [failure]; si hay [gate], espera a que se abra. */
    private class ScriptedWriter : ReportWriter {
        var calls = 0
        var failure: AiFailure? = null
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var reply: (ReportRequest) -> ReportReply = { ReportReply("{}", 10, 5) }
        override suspend fun write(request: ReportRequest): ReportReply {
            calls++
            gate?.await()
            failure?.let { throw AiException(it, "prueba") }
            return reply(request)
        }
    }

    private fun aiKit(dir: java.io.File, clock: () -> Long, withKey: Boolean = true): AiKit {
        val vault = MapVault().apply { if (withKey) keys[AiProvider.CLAUDE] = "clave-de-prueba-1234" }
        val settings = AiSettings(MemPrefs(), zone = { TestData.zone })
        val writer = ScriptedWriter()
        val reports = ReportRepository({ _, _ -> writer }, vault, settings, clock)
        return AiKit(reports, ReportStore(dir.resolve("ai_report.json")), settings, vault, writer)
    }

    @Test fun `tras un refresco Ok se pide el parte de donde estoy y se cuenta como HERE (016 P3)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, hereAt = here, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        settle()
        w.vm.refresh()
        settle()
        assertEquals(1, kit.writer.calls)
        assertEquals(1, kit.settings.usage.value.dayHere)
        assertEquals(0, kit.settings.usage.value.daySaved)
    }

    @Test fun `tras un refresco sin conexion no se pide el parte`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, hereAt = here, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } }) { weather.failing = true }
        settle()
        w.vm.refresh()
        settle()
        assertEquals(0, kit.writer.calls)
    }

    @Test fun `sin clave no se pide nada aunque el refresco vaya bien`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, hereAt = here, ai = { clock -> aiKit(tmp.newFolder(), clock, withKey = false).also { kit = it } })
        settle()
        w.vm.refresh()
        settle()
        assertEquals(0, kit.writer.calls)
    }

    @Test fun `al abrir un guardado viejo se pide su parte como SAVED`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        val place = w.addWithData("Ciudad A", placeA, start, age = 3 * 3600)
        settle()
        w.vm.select(PlaceRef.Saved(place.id))
        settle()
        assertEquals(1, kit.writer.calls)
        assertEquals(1, kit.settings.usage.value.daySaved)
        assertEquals(0, kit.settings.usage.value.dayHere)
    }

    @Test fun `guardar la clave la mete en el vault y Content trae los 4 ultimos, nunca la clave`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock, withKey = false).also { kit = it } })
        settle()
        assertTrue(content(w.vm).ai.hints.isEmpty())
        w.vm.onAi(AiAction.SaveKey(AiProvider.GEMINI, "otra-clave-secreta-9876"))
        settle()
        assertEquals("otra-clave-secreta-9876", kit.vault.keys[AiProvider.GEMINI])
        assertEquals(mapOf(AiProvider.GEMINI to "9876"), content(w.vm).ai.hints)
        assertFalse(content(w.vm).ai.toString().contains("otra-clave-secreta"))
    }

    @Test fun `si el Keystore falla al guardar no se cae y queda dicho`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock, withKey = false).also { kit = it } })
        kit.vault.failPut = true
        settle()
        w.vm.onAi(AiAction.SaveKey(AiProvider.CLAUDE, "clave-de-prueba-1234"))
        settle()
        assertTrue(content(w.vm).ai.hints.isEmpty())
        assertEquals(AiFailure.NO_KEY, content(w.vm).ai.status?.failure)
    }

    @Test fun `borrar la clave quita la pista y elegir proveedor cambia el elegido`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        settle()
        assertEquals("1234", content(w.vm).ai.hints[AiProvider.CLAUDE])
        w.vm.onAi(AiAction.ClearKey(AiProvider.CLAUDE))
        settle()
        assertTrue(content(w.vm).ai.hints.isEmpty())
        assertEquals(null, kit.vault.keys[AiProvider.CLAUDE])
        w.vm.onAi(AiAction.Choose(AiProvider.OPENAI))
        settle()
        assertEquals(AiProvider.OPENAI, content(w.vm).ai.provider)
    }

    @Test fun `Probar ahora pone testing y luego el texto del parte`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        val facts = checkNotNull(reportFacts(TestData.snapshot(start, currentC = 12.0), start, TestData.zone))
        kit.writer.reply = {
            ReportReply("""{"today":"Hoy máxima de ${facts.today.maxC} grados.","days":[],"warnings":[]}""", 100, 50)
        }
        kit.writer.gate = kotlinx.coroutines.CompletableDeferred()
        settle()
        w.vm.onAi(AiAction.Test)
        settle()
        assertTrue(content(w.vm).ai.testing)
        kit.writer.gate!!.complete(Unit)
        settle()
        assertFalse(content(w.vm).ai.testing)
        assertEquals("Hoy máxima de ${facts.today.maxC} grados.", content(w.vm).ai.testText)
        assertEquals(null, content(w.vm).ai.status?.failure)
    }

    @Test fun `Probar ahora con un fallo deja el motivo en el estado y ningun texto`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        kit.writer.failure = AiFailure.BAD_KEY
        settle()
        w.vm.onAi(AiAction.Test)
        settle()
        assertFalse(content(w.vm).ai.testing)
        assertEquals(null, content(w.vm).ai.testText)
        assertEquals(AiFailure.BAD_KEY, content(w.vm).ai.status?.failure)
    }

    // ---------- 016 P4 · el parte guardado llega a la pantalla ----------

    private fun reportOf(snapshot: WeatherSnapshot, now: Long, text: String, generatedAt: Long = now) =
        AiReport(generatedAt, AiProvider.CLAUDE, checkNotNull(reportFacts(snapshot, now, ZoneId.of(snapshot.zoneId))).basis, today = text)

    @Test fun `un parte guardado y vigente llega a Content sin ninguna llamada de red (H5)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } }) {
            kit.store.write(reportOf(TestData.snapshot(start, currentC = 12.0), start, "Hoy parte guardado."))
        }
        settle()
        assertEquals("Hoy parte guardado.", content(w.vm).report?.today)
        assertEquals(0, kit.writer.calls)
        assertEquals(emptyList<LatLon>(), w.weather.requested)
    }

    @Test fun `sin ai_report_json el Content es el de siempre (H18)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock) })
        settle()
        assertNull(content(w.vm).report)
        assertEquals(12, content(w.vm).snapshot.current.temperatureC.toInt())
    }

    @Test fun `pasada la medianoche del sitio el parte de ayer ya no se ve (H8)`() = runTest(main) {
        val start = at(2026, 11, 4, 23, 59)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } }) {
            kit.store.write(reportOf(TestData.snapshot(start, currentC = 12.0), start, "Hoy parte de ayer."))
        }
        settle()
        assertEquals("Hoy parte de ayer.", content(w.vm).report?.today)
        advanceTimeBy(61_000L)
        settle()
        assertNull(content(w.vm).report)
        assertEquals(0, kit.writer.calls)
    }

    @Test fun `si la prevision cambio de verdad el parte no se ve (H8)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } }) {
            val old = reportOf(TestData.snapshot(start, currentC = 12.0), start, "Hoy de antes.")
            kit.store.write(old.copy(basis = old.basis.copy(maxC = old.basis.maxC - 5)))
        }
        settle()
        assertNull(content(w.vm).report)
    }

    @Test fun `sin conexion y con parte vigente el parte se ve`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, hereAt = here, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } }) {
            weather.failing = true
            kit.store.write(reportOf(TestData.snapshot(start, currentC = 12.0), start, "Hoy sin red."))
        }
        settle()
        w.vm.refresh()
        settle()
        assertTrue(content(w.vm).offline)
        assertEquals("Hoy sin red.", content(w.vm).report?.today)
    }

    @Test fun `al cambiar a un guardado se ve su parte y no el de donde estoy`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        lateinit var place: SavedPlace
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } }) {
            kit.store.write(reportOf(TestData.snapshot(start, currentC = 12.0), start, "Hoy aqui."))
            place = addWithData("Ciudad A", placeA, start, age = 60)
            val saved = this.saved.stores.snapshot(place.id).read().snapshot!!
            this.saved.stores.report(place.id).write(reportOf(saved, start, "Hoy alli."))
        }
        settle()
        assertEquals("Hoy aqui.", content(w.vm).report?.today)
        w.vm.select(PlaceRef.Saved(place.id))
        settle()
        assertEquals("Hoy alli.", content(w.vm).report?.today)
        w.vm.select(PlaceRef.Here)
        settle()
        assertEquals("Hoy aqui.", content(w.vm).report?.today)
    }

    /** SharedPreferences en memoria: este test no usa Robolectric. */
    private class MemoryPrefs : android.content.SharedPreferences {
        val map = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String, defValue: String?) = map[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = map[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long) = map[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float) = map[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as Boolean? ?: defValue
        override fun contains(key: String) = key in map
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { map.clear() }
            override fun commit(): Boolean { map.putAll(pending); return true }
            override fun apply() { commit() }
        }
    }

    @Test fun `setMorning y setMorningTime guardan con now y la zona del movil, avisan para rearmar y el panel los refleja (018 P2)`() = runTest(main) {
        val start = at(2026, 11, 16, 9, 0)
        val dir = tmp.newFolder()
        val snapshots = SnapshotStore(dir.resolve("snapshot.json"))
        snapshots.write(StoredState(snapshot(), lastAttemptFailed = false))
        val repository = WeatherRepository(FakeWeather(), FakeWarnings(), FakeAir(), places, FakeLocation(), snapshots)
        val reliability = ReliabilityRepository(
            FakeReliability(), ReliabilityStore(dir.resolve("reliability.json")), snapshots, scope = backgroundScope,
        )
        val settings = com.trastealabs.tiempo.data.MorningSettings(MemoryPrefs())
        var rearmed = 0
        var allowed = true
        val vm = MainViewModel(
            repository, reliability, hasLocationPermission = { true }, nowSeconds = { start }, zone = { TestData.zone },
            morning = settings, notificationsAllowed = { allowed }, onMorningChanged = { rearmed++ },
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(MorningPanel(), (vm.uiState.value as UiState.Content).morning)

        // Las 9:00 y la hora de 7:30 ya ha pasado: encender empieza mañana (hoy queda saltado).
        vm.setMorning(true)
        runCurrent()
        assertEquals(1, rearmed)
        assertEquals("2026-11-16", settings.lastDay)
        assertEquals(MorningPanel(enabled = true, minuteOfDay = 450), (vm.uiState.value as UiState.Content).morning)

        // Una hora que aún no ha llegado hoy vuelve a contar hoy.
        vm.setMorningTime(10 * 60)
        runCurrent()
        assertEquals(2, rearmed)
        assertEquals("", settings.lastDay)
        assertEquals(600, (vm.uiState.value as UiState.Content).morning.minuteOfDay)

        // El permiso quitado desde el sistema se nota al volver a la app.
        allowed = false
        vm.morningPermissionMayHaveChanged()
        runCurrent()
        assertEquals(true, (vm.uiState.value as UiState.Content).morning.denied)
    }

    // ---------- 018 P4 · la pregunta ----------

    private fun questionFacts(snapshot: WeatherSnapshot, now: Long, zone: ZoneId) = checkNotNull(reportFacts(snapshot, now, zone))

    private fun answerJson(text: String) = "{\"kind\":\"answer\",\"text\":\"$text\"}"

    private val offTopicJson = "{\"kind\":\"off_topic\",\"text\":\"\"}"

    @Test fun `sin clave la entrada de la pregunta esta oculta (H7)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock, withKey = false) })
        settle()
        assertEquals(AskEntry.Hidden, content(w.vm).ask)
    }

    @Test fun `con clave y prevision la entrada esta abierta`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock) })
        settle()
        assertEquals(AskEntry.Open, content(w.vm).ask)
    }

    @Test fun `sin viewmodel de IA (los tests de antes) no hay entrada`() = runTest(main) {
        val w = world(at(2026, 11, 4, 10, 0))
        settle()
        assertEquals(AskEntry.Hidden, content(w.vm).ask)
    }

    @Test fun `con la prevision sin horas por delante no hay pildora aunque haya clave (pendiente de P1)`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        val empty = TestData.snapshot(start, currentC = 12.0).let { it.copy(hourly = emptyList()) }
        assertEquals(null, reportFacts(empty, start, TestData.zone))
        val w = world(start, hereSnapshot = empty, ai = { clock -> aiKit(tmp.newFolder(), clock) })
        settle()
        assertEquals(AskEntry.Hidden, content(w.vm).ask)
    }

    @Test fun `ask contesta sobre donde estoy y la respuesta queda en memoria`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        val facts = questionFacts(TestData.snapshot(start, currentC = 12.0), start, TestData.zone)
        kit.writer.reply = { ReportReply(answerJson("Hoy máxima de ${facts.today.maxC} grados."), 100, 20) }
        kit.writer.gate = kotlinx.coroutines.CompletableDeferred()
        settle()
        assertEquals(AskState.Writing, w.vm.askState.value)
        w.vm.ask("  ¿Puedo tender mañana? ")
        settle()
        assertEquals(AskState.Asking("¿Puedo tender mañana?"), w.vm.askState.value)
        kit.writer.gate!!.complete(Unit)
        settle()
        val state = w.vm.askState.value as AskState.Answered
        assertEquals("¿Puedo tender mañana?", state.question)
        assertEquals("Hoy máxima de ${facts.today.maxC} grados.", state.answer.text)
        assertEquals(1, kit.writer.calls)
        assertEquals(1, content(w.vm).ai.usage.dayAsk)
        assertEquals(0, content(w.vm).ai.usage.dayHere)
        // Nada de esto va a disco: el parte guardado sigue sin existir.
        assertNull(kit.store.read())
    }

    @Test fun `una pregunta vacia no llama`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        settle()
        w.vm.ask("   ")
        settle()
        assertEquals(AskState.Writing, w.vm.askState.value)
        assertEquals(0, kit.writer.calls)
    }

    @Test fun `un fallo del proveedor deja la pregunta y la causa para Reintentar`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        kit.writer.failure = AiFailure.OFFLINE
        settle()
        w.vm.ask("¿Llueve?")
        settle()
        assertEquals(AskState.Failed("¿Llueve?", com.trastealabs.tiempo.domain.AskFailure.OFFLINE), w.vm.askState.value)
    }

    @Test fun `ask sobre un guardado usa su prevision y la hora de su zona`() = runTest(main) {
        // 20:00 en Madrid ya es el día siguiente en Auckland: «hoy» para ese sitio es otro.
        val start = at(2026, 11, 4, 20, 0)
        lateinit var kit: AiKit
        lateinit var place: SavedPlace
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } }) {
            place = addWithData("Ciudad A", placeA, start, age = 600, zoneId = "Pacific/Auckland")
        }
        var sentUser = ""
        kit.writer.reply = { sentUser = it.user; ReportReply(offTopicJson, 10, 5) }
        settle()
        w.vm.select(PlaceRef.Saved(place.id))
        settle()
        w.vm.ask("¿Llueve?")
        settle()
        val answered = w.vm.askState.value as AskState.Answered
        assertEquals(start - 600, answered.answer.dataAt)
        assertTrue(sentUser, sentUser.contains("\"hoy\":\"2026-11-05\""))
    }

    @Test fun `cancelAsk corta la llamada, el intento cuenta y vuelve a escribir`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        kit.writer.gate = kotlinx.coroutines.CompletableDeferred()
        settle()
        w.vm.ask("¿Llueve?")
        settle()
        assertTrue(w.vm.askState.value is AskState.Asking)
        w.vm.cancelAsk()
        assertEquals(AskState.Writing, w.vm.askState.value)
        settle()
        assertEquals(1, content(w.vm).ai.usage.dayAsk)
        // Aunque la llamada acabe después, no resucita nada.
        kit.writer.gate!!.complete(Unit)
        settle()
        assertEquals(AskState.Writing, w.vm.askState.value)
    }

    @Test fun `closeAsk olvida pregunta y respuesta`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        kit.writer.reply = { ReportReply(offTopicJson, 10, 5) }
        settle()
        w.vm.ask("¿Quién ganó la liga?")
        settle()
        assertTrue(w.vm.askState.value is AskState.Answered)
        w.vm.closeAsk()
        assertEquals(AskState.Writing, w.vm.askState.value)
        assertFalse(w.vm.askState.value.toString().contains("liga"))
    }

    @Test fun `cambiar de sitio cierra la pregunta`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        lateinit var place: SavedPlace
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } }) {
            place = addWithData("Ciudad A", placeA, start, age = 60)
        }
        kit.writer.reply = { ReportReply(offTopicJson, 10, 5) }
        settle()
        w.vm.ask("¿Quién ganó la liga?")
        settle()
        assertTrue(w.vm.askState.value is AskState.Answered)
        w.vm.select(PlaceRef.Saved(place.id))
        assertEquals(AskState.Writing, w.vm.askState.value)
    }

    @Test fun `en la pregunta numero 20 la entrada pasa a tope`() = runTest(main) {
        val start = at(2026, 11, 4, 10, 0)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        kit.writer.reply = { ReportReply(offTopicJson, 10, 5) }
        settle()
        repeat(19) {
            w.vm.ask("¿Quién ganó la liga?")
            settle()
            w.vm.closeAsk()
        }
        assertEquals(AskEntry.Open, content(w.vm).ask)
        w.vm.ask("¿Y el año pasado?")
        settle()
        assertEquals(AskEntry.Capped(20), content(w.vm).ask)
        // La 21.ª no llama.
        w.vm.closeAsk()
        w.vm.ask("¿Una más?")
        settle()
        assertEquals(20, kit.writer.calls)
        assertEquals(AskState.Failed("¿Una más?", com.trastealabs.tiempo.domain.AskFailure.CAP), w.vm.askState.value)
    }

    @Test fun `pasada la medianoche el tope vuelve a cero y la entrada a abrirse`() = runTest(main) {
        val start = at(2026, 11, 4, 23, 59)
        lateinit var kit: AiKit
        val w = world(start, ai = { clock -> aiKit(tmp.newFolder(), clock).also { kit = it } })
        kit.writer.reply = { ReportReply(offTopicJson, 10, 5) }
        settle()
        repeat(20) {
            w.vm.ask("¿Quién ganó la liga?")
            settle()
            w.vm.closeAsk()
        }
        assertEquals(AskEntry.Capped(20), content(w.vm).ask)
        advanceTimeBy(61_000L)
        settle()
        assertEquals(AskEntry.Open, content(w.vm).ask)
    }

}
