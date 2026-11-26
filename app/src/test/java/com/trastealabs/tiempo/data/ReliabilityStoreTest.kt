package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.DayReliability
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.ModelDay
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.StoredReliability
import com.trastealabs.tiempo.domain.WeatherModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReliabilityStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val stored = StoredReliability(
        Reliability(
            position = LatLon(40.0, -3.0),
            zoneId = "Europe/Madrid",
            fetchedAt = 1_793_000_000L,
            days = listOf(
                DayReliability("2026-11-05", 122, 30, 19.5, 21.25, listOf(ModelDay(WeatherModel.METEO_FRANCE, 61, 16.6, 2.8))),
                DayReliability("2026-11-06", 82, 0, 18.0, 18.5),
            ),
        ),
        lastFailedAt = 1_793_003_600L,
    )

    @Test fun `escribir y leer da lo mismo`() = runTest {
        val file = tmp.newFolder().resolve("reliability.json")
        ReliabilityStore(file).write(stored)
        assertEquals(stored, ReliabilityStore(file).read())
    }

    @Test fun `sin fichero no hay fiabilidad (datos de antes de esta version)`() = runTest {
        assertEquals(StoredReliability(), ReliabilityStore(tmp.newFolder().resolve("reliability.json")).read())
    }

    @Test fun `un fichero roto se lee como vacio`() = runTest {
        val file = tmp.newFolder().resolve("reliability.json")
        ReliabilityStore(file).write(stored)
        file.writeText(file.readText().take(120))
        assertEquals(StoredReliability(), ReliabilityStore(file).read())
    }

    @Test fun `escribir no deja ficheros temporales`() = runTest {
        val dir = tmp.newFolder()
        ReliabilityStore(dir.resolve("reliability.json")).write(stored)
        assertEquals(listOf("reliability.json"), dir.list()!!.toList())
    }

    @Test fun `el flujo emite lo guardado y despues cada escritura`() = runTest {
        val store = ReliabilityStore(tmp.newFolder().resolve("reliability.json"))
        assertEquals(StoredReliability(), store.state.first())
        store.write(stored)
        assertEquals(stored, store.state.first())
    }

    @Test fun `una fiabilidad guardada por la version 005 se lee entera (H14 del 006)`() = runTest {
        val file = tmp.newFolder().resolve("reliability.json")
        file.writeText(javaClass.getResource("/reliability_005.json")!!.readText())
        val stored = ReliabilityStore(file).read()
        assertEquals(2, stored.reliability!!.days.size)
        assertEquals(122, stored.reliability!!.days[0].scenarios)
        assertEquals(null, stored.lastFailedAt)
    }
}
