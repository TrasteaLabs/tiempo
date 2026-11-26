package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.AirHour
import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.StoredState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val state = StoredState(TestData.snapshot(TestData.at(2025, 6, 10, 10, 20)), lastAttemptFailed = true)

    @Test fun `escribir y leer da lo mismo`() = runTest {
        val file = tmp.newFolder().resolve("snapshot.json")
        SnapshotStore(file).write(state)
        assertEquals(state, SnapshotStore(file).read())
    }

    @Test fun `un snapshot con viento por horas y luna se guarda y se lee igual (H1 del 004)`() = runTest {
        val base = TestData.snapshot(TestData.at(2026, 11, 3, 23, 0))
        val snapshot = base.copy(
            hourly = base.hourly.map { it.copy(windKmh = 14.2, windGustsKmh = 28.0, windDirectionDeg = 225, humidityPct = 81, dewPointC = 9.4) },
            daily = base.daily.mapIndexed { i, d ->
                // el segundo día la luna no sale: salida null con fase
                d.copy(moonrise = if (i == 1) null else TestData.at(2026, 11, 4 + i, 2, 47), moonset = TestData.at(2026, 11, 4 + i, 15, 43), moonPhase = 0.841)
            },
            yesterday = DayPoint("2026-11-02", 19.0, 10.0, 0, 0.0, moonrise = TestData.at(2026, 11, 2, 0, 40), moonPhase = 0.78),
        )
        val file = tmp.newFolder().resolve("snapshot.json")
        val stored = StoredState(snapshot, lastAttemptFailed = false)
        SnapshotStore(file).write(stored)
        val read = SnapshotStore(file).read()
        assertEquals(stored, read)
        assertNull(read.snapshot!!.daily[1].moonrise)
        assertEquals(0.841, read.snapshot!!.daily[1].moonPhase!!, 0.0)
        assertEquals(225, read.snapshot!!.hourly[0].windDirectionDeg)
    }

    @Test fun `un snapshot con aire y polen se guarda y se lee igual (H1 del 005)`() = runTest {
        val now = TestData.at(2026, 5, 5, 12, 0)
        val air = AirQuality(
            LatLon(40.0, -3.0), now,
            listOf(
                AirHour(now, aqi = 35, pm25Index = 20, pm10Index = 18, no2Index = 9, ozoneIndex = 35, so2Index = 1,
                    pm10 = 14.2, dust = 3.5, olivePollen = 212.4, grassPollen = 31.0),
                AirHour(now + 3600, aqi = 38),   // sin polen ni subíndices
            ),
        )
        val stored = StoredState(TestData.snapshot(now).copy(air = air), lastAttemptFailed = false)
        val file = tmp.newFolder().resolve("snapshot.json")
        SnapshotStore(file).write(stored)
        val read = SnapshotStore(file).read()
        assertEquals(stored, read)
        assertNull(read.snapshot!!.air!!.hours[1].olivePollen)
    }

    @Test fun `sin fichero no hay datos`() = runTest {
        val store = SnapshotStore(tmp.newFolder().resolve("snapshot.json"))
        assertEquals(StoredState(null, false), store.read())
    }

    @Test fun `un fichero a medio escribir no rompe la lectura`() = runTest {
        val file = tmp.newFolder().resolve("snapshot.json")
        SnapshotStore(file).write(state)
        file.writeText(file.readText().take(200))
        assertEquals(StoredState(null, false), SnapshotStore(file).read())
    }

    @Test fun `escribir no deja ficheros temporales`() = runTest {
        val dir = tmp.newFolder()
        SnapshotStore(dir.resolve("snapshot.json")).write(state)
        assertEquals(listOf("snapshot.json"), dir.list()!!.toList())
    }

    @Test fun `un snapshot guardado por la version 001 se lee y los campos nuevos quedan vacios`() = runTest {
        val file = tmp.newFolder().resolve("snapshot.json")
        file.writeText(javaClass.getResource("/snapshot_001.json")!!.readText())
        val snapshot = SnapshotStore(file).read().snapshot!!

        assertEquals("Villa Ejemplo", snapshot.place.name)
        assertEquals(19.4, snapshot.current.temperatureC, 0.001)
        assertNull(snapshot.current.windKmh)
        assertNull(snapshot.current.windGustsKmh)
        assertNull(snapshot.current.windDirectionDeg)
        assertNull(snapshot.current.humidityPct)
        assertNull(snapshot.current.dewPointC)
        assertNull(snapshot.current.visibilityM)
        assertEquals(2, snapshot.daily.size)
        snapshot.daily.forEach {
            assertNull(it.sunrise)
            assertNull(it.sunset)
            assertNull(it.daylightSeconds)
            // y los de la 004
            assertNull(it.moonrise)
            assertNull(it.moonset)
            assertNull(it.moonPhase)
        }
        snapshot.hourly.forEach {
            assertNull(it.windKmh)
            assertNull(it.windGustsKmh)
            assertNull(it.windDirectionDeg)
            assertNull(it.humidityPct)
            assertNull(it.dewPointC)
        }
        assertNull(snapshot.yesterday)
        assertNull(snapshot.air)
    }

    @Test fun `el flujo emite lo guardado y despues cada escritura`() = runTest {
        val store = SnapshotStore(tmp.newFolder().resolve("snapshot.json"))
        assertNull(store.state.first().snapshot)
        store.write(state)
        assertEquals(state, store.state.first())
        store.write(state.copy(lastAttemptFailed = false))
        assertFalse(store.state.first().lastAttemptFailed)
    }

    @Test fun `un snapshot guardado por la version 005 se lee entero (H14 del 006)`() = runTest {
        val file = tmp.newFolder().resolve("snapshot.json")
        file.writeText(javaClass.getResource("/snapshot_005.json")!!.readText())
        val stored = SnapshotStore(file).read()
        val snapshot = stored.snapshot!!

        assertEquals(false, stored.lastAttemptFailed)
        assertEquals("Villa Ejemplo", snapshot.place.name)
        assertEquals(48, snapshot.hourly.size)
        assertEquals(2, snapshot.daily.size)
        assertEquals(0.84, snapshot.daily[0].moonPhase!!, 0.001)
        assertEquals(listOf("aviso-ejemplo-1"), snapshot.warnings.map { it.id })
        assertEquals(2, snapshot.air!!.hours.size)
        assertEquals(35, snapshot.air!!.hours[0].aqi)
        assertEquals(20.0, snapshot.yesterday!!.maxC, 0.001)
    }

    @Test fun `H15 un snapshot guardado por la version 013 se lee entero y sin fuente de avisos`() = runTest {
        val file = tmp.newFolder().resolve("snapshot.json")
        file.writeText(javaClass.getResource("/snapshot_013.json")!!.readText())
        val stored = SnapshotStore(file).read()
        val snapshot = stored.snapshot!!

        assertEquals("Villa Ejemplo", snapshot.place.name)
        assertEquals(listOf("aviso-ejemplo-1"), snapshot.warnings.map { it.id })
        assertEquals(1793779920L, snapshot.warningsFetchedAt)
        assertNull(snapshot.warningsSource)
        assertEquals(2, snapshot.air!!.hours.size)

        // y se vuelve a guardar con el campo nuevo y se lee igual
        val next = stored.copy(snapshot = snapshot.copy(warningsSource = "https://ejemplo.invalid/avisos.tar.gz"))
        SnapshotStore(file).write(next)
        assertEquals(next, SnapshotStore(file).read())
    }

    @Test fun `H15 el mismo fichero viejo lo lee tambien el almacen de un sitio guardado`() = runTest {
        val dir = tmp.newFolder()
        val file = dir.resolve("sitio1/snapshot.json").also { it.parentFile!!.mkdirs() }
        file.writeText(javaClass.getResource("/snapshot_013.json")!!.readText())
        val snapshot = PlaceStores(dir).snapshot("sitio1").read().snapshot!!
        assertEquals(listOf("aviso-ejemplo-1"), snapshot.warnings.map { it.id })
        assertNull(snapshot.warningsSource)
    }
}
