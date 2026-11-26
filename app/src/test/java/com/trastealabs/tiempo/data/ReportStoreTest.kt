package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.ReportBasis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val report = AiReport(
        generatedAt = 1_793_000_000L,
        provider = AiProvider.GEMINI,
        basis = ReportBasis("2026-11-12", 20, 10, true, 15, listOf("w1", "w2")),
        today = "Hoy entre 10° y 20°.",
        days = mapOf("2026-11-13" to "Máxima de 18°."),
        warnings = mapOf("w1" to "Puede llover fuerte."),
    )

    @Test fun `escribir y leer da lo mismo`() = runTest {
        val file = tmp.newFolder().resolve("ai_report.json")
        ReportStore(file).write(report)
        assertEquals(report, ReportStore(file).read())
    }

    @Test fun `un parte con solo lo obligatorio tambien vuelve igual`() = runTest {
        val file = tmp.newFolder().resolve("ai_report.json")
        val small = AiReport(5L, AiProvider.OPENAI, ReportBasis("2026-11-12", 1, 0, false))
        ReportStore(file).write(small)
        assertEquals(small, ReportStore(file).read())
    }

    @Test fun `un parte con la frase del widget vuelve igual`() = runTest {
        val file = tmp.newFolder().resolve("ai_report.json")
        val withWidget = report.copy(widget = "Lluvia desde las 17:00")
        ReportStore(file).write(withWidget)
        assertEquals(withWidget, ReportStore(file).read())
    }

    @Test fun `un ai_report json de antes del 018, sin widget, se lee igual y sin frase`() = runTest {
        val file = tmp.newFolder().resolve("ai_report.json")
        file.writeText(
            "{\"generatedAt\":1793000000,\"provider\":\"CLAUDE\",\"basis\":{\"date\":\"2026-11-12\",\"maxC\":20,\"minC\":10," +
                "\"wetToday\":true,\"rainStartHour\":15,\"warningIds\":[\"w1\"]},\"today\":\"Hoy entre 10° y 20°.\"," +
                "\"days\":{\"2026-11-13\":\"Máxima de 18°.\"},\"warnings\":{\"w1\":\"Puede llover fuerte.\"}}",
        )
        val read = checkNotNull(ReportStore(file).read())
        assertEquals("Hoy entre 10° y 20°.", read.today)
        assertEquals(mapOf("2026-11-13" to "Máxima de 18°."), read.days)
        assertNull(read.widget)
    }

    @Test fun `sin fichero no hay parte`() = runTest {
        assertNull(ReportStore(tmp.newFolder().resolve("ai_report.json")).read())
    }

    @Test fun `un fichero roto se lee como sin parte, sin lanzar`() = runTest {
        val file = tmp.newFolder().resolve("ai_report.json")
        ReportStore(file).write(report)
        file.writeText(file.readText().take(40))
        assertNull(ReportStore(file).read())
        file.writeText("")
        assertNull(ReportStore(file).read())
        file.writeText("[1,2")
        assertNull(ReportStore(file).read())
    }

    @Test fun `clear borra el parte y el fichero`() = runTest {
        val file = tmp.newFolder().resolve("ai_report.json")
        val store = ReportStore(file)
        store.write(report)
        store.clear()
        assertNull(store.read())
        assertFalse(file.exists())
        assertNull(ReportStore(file).read())
        store.clear() // sin nada que borrar tampoco lanza
    }

    @Test fun `un campo de mas se lee, por versiones futuras`() = runTest {
        val file = tmp.newFolder().resolve("ai_report.json")
        ReportStore(file).write(report)
        file.writeText(file.readText().replaceFirst("{", """{"campoFuturo":{"a":1},"""))
        assertEquals(report, ReportStore(file).read())
    }

    @Test fun `escribir no deja ficheros temporales`() = runTest {
        val dir = tmp.newFolder()
        ReportStore(dir.resolve("ai_report.json")).write(report)
        assertEquals(listOf("ai_report.json"), dir.list()!!.toList())
    }

    @Test fun `state emite lo guardado y cada cambio`() = runTest {
        val store = ReportStore(tmp.newFolder().resolve("ai_report.json"))
        assertNull(store.state.first())
        store.write(report)
        assertEquals(report, store.state.first())
        store.clear()
        assertNull(store.state.first())
    }

    @Test fun `PlaceStores da un almacen por sitio, que se borra con su carpeta`() = runTest {
        val dir = tmp.newFolder()
        val stores = PlaceStores(dir)
        val a = stores.report("p1")
        assertTrue(a === stores.report("p1"))
        a.write(report)
        assertTrue(dir.resolve("p1/ai_report.json").exists())
        assertNull(stores.report("p2").read())
        stores.delete("p1")
        assertFalse(dir.resolve("p1").exists())
        assertNull(stores.report("p1").read())
    }
}
