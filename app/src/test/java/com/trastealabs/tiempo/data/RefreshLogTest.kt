package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.refresh.AemetOutcome
import com.trastealabs.tiempo.refresh.RefreshOrigin
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** El registro de refrescos (015 P2, H9, H10, H13): una línea por refresco, siete días, nada personal. */
class RefreshLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private val now = TestData.at(2026, 11, 12, 10, 0)
    private val day = 24 * 3600L

    private fun entry(
        at: Long,
        origin: RefreshOrigin = RefreshOrigin.WORKER,
        outcome: RefreshOutcome = RefreshOutcome.OK,
        aemet: AemetOutcome = AemetOutcome.UNCHANGED,
        bytes: Long = 0,
    ) = RefreshLogEntry(at, origin, outcome, aemet, bytes)

    private fun log(file: java.io.File = tmp.newFolder().resolve("refresh_log.jsonl"), at: () -> Long = { now }) =
        RefreshLog(file, at)

    @Test fun `cada append deja una linea con todos los campos y se lee igual (H9)`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        val log = log(file)
        val a = entry(now - 600, RefreshOrigin.APP, RefreshOutcome.OK, AemetOutcome.NEW, 352_000)
        val b = entry(now - 300, RefreshOrigin.ALARM, RefreshOutcome.OFFLINE, AemetOutcome.NOT_APPLICABLE)
        val c = entry(now, RefreshOrigin.WORKER, RefreshOutcome.NO_LOCATION, AemetOutcome.FAILED)
        log.append(a)
        log.append(b)
        log.append(c)

        assertEquals(listOf(a, b, c), log.entries.first())
        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        listOf("at", "origin", "outcome", "aemet", "aemetBytes").forEach { key ->
            assertTrue("falta $key en ${lines[0]}", lines[0].contains("\"$key\""))
        }
        // Otro objeto sobre el mismo fichero (el proceso murió y volvió): lo mismo.
        assertEquals(listOf(a, b, c), RefreshLog(file) { now }.entries.first())
    }

    @Test fun `lo de hace mas de siete dias se borra al escribir y lo de justo antes se queda (H10)`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        val log = log(file)
        val old = entry(now - REFRESH_LOG_DAYS * day - 1)
        val edge = entry(now - REFRESH_LOG_DAYS * day + 1)
        val exactly = entry(now - REFRESH_LOG_DAYS * day)
        // Las escribe un reloj que va por detrás, para que no se pode al meterlas.
        var clock = now - 8 * day
        val early = RefreshLog(file) { clock }
        early.append(old)
        early.append(edge)
        early.append(exactly)
        assertEquals(3, early.entries.first().size)

        clock = now
        val fresh = entry(now)
        log.append(fresh)

        assertEquals(listOf(edge, exactly, fresh), log.entries.first())
        assertEquals(3, file.readLines().count { it.isNotBlank() })
    }

    @Test fun `sin fichero la lista esta vacia`() = runTest {
        assertEquals(emptyList<RefreshLogEntry>(), log().entries.first())
    }

    @Test fun `una linea rota se salta y el resto se lee`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        val a = entry(now - 100)
        val b = entry(now - 50)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        file.writeText(
            json.encodeToString(RefreshLogEntry.serializer(), a) + "\n" +
                "{esto no es json\n" +
                "\n" +
                "{\"at\":1,\"origin\":\"NO_EXISTE\",\"outcome\":\"OK\",\"aemet\":\"NEW\"}\n" +
                json.encodeToString(RefreshLogEntry.serializer(), b) + "\n",
        )
        val log = RefreshLog(file) { now }
        assertEquals(listOf(a, b), log.entries.first())

        // Y se puede seguir escribiendo: la línea rota se va al reescribir.
        val c = entry(now)
        log.append(c)
        assertEquals(listOf(a, b, c), log.entries.first())
        assertEquals(3, file.readLines().count { it.isNotBlank() })
    }

    @Test fun `un fichero que no se puede leer como texto no tira el registro`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x7B, 0x0A))
        assertEquals(emptyList<RefreshLogEntry>(), RefreshLog(file) { now }.entries.first())
    }

    @Test fun `una lectura que falla no se guarda como vacia ni se pisa el historial`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        file.mkdir()   // un directorio donde debería haber un fichero: leerlo lanza IOException
        val log = RefreshLog(file) { now }
        assertEquals(emptyList<RefreshLogEntry>(), log.entries.first())
        var thrown = false
        try {
            log.append(entry(now - 10))
        } catch (e: java.io.IOException) {
            thrown = true
        }
        assertTrue(thrown)

        // El disco vuelve a dejar leer y había historial: no se ha perdido, porque no se cacheó el vacío.
        file.delete()
        val old = entry(now - 3 * day)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        file.writeText(json.encodeToString(RefreshLogEntry.serializer(), old) + "\n")
        val next = entry(now)
        log.append(next)
        assertEquals(listOf(old, next), log.entries.first())
        assertEquals(2, file.readLines().count { it.isNotBlank() })
    }

    @Test fun `dos append a la vez dejan dos lineas enteras`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        val log = log(file)
        val a = entry(now - 20, RefreshOrigin.APP)
        val b = entry(now - 10, RefreshOrigin.WORKER)
        val jobs = listOf(async { log.append(a) }, async { log.append(b) })
        jobs.forEach { it.await() }

        assertEquals(setOf(a, b), log.entries.first().toSet())
        assertEquals(2, file.readLines().count { it.isNotBlank() })
        assertEquals(2, RefreshLog(file) { now }.entries.first().size)
    }

    @Test fun `no queda fichero temporal tras escribir`() = runTest {
        val dir = tmp.newFolder()
        val log = RefreshLog(dir.resolve("refresh_log.jsonl")) { now }
        log.append(entry(now))
        assertEquals(listOf("refresh_log.jsonl"), dir.list()!!.toList())
    }

    @Test fun `escribir sobre una ruta imposible lanza y no deja nada a medias`() = runTest {
        val blocker = tmp.newFile("no-es-carpeta")
        val log = RefreshLog(blocker.resolve("refresh_log.jsonl")) { now }
        var thrown = false
        try {
            log.append(entry(now))
        } catch (e: java.io.IOException) {
            thrown = true
        }
        assertTrue(thrown)
        assertEquals(emptyList<RefreshLogEntry>(), log.entries.first())
    }

    @Test fun `el registro no lleva ubicacion ni nombre de sitio (H13)`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        val log = log(file)
        // Un sitio de los tests con su posición redondeada, como llega al repositorio, no puede aparecer.
        val siteNames = listOf(TestData.place.name, "Villa Ejemplo", "Otra Villa", "Ville Exemple", "Madrid", "Córdoba")
        listOf(
            entry(now - 3000, RefreshOrigin.APP, RefreshOutcome.OK, AemetOutcome.NEW, 351_999),
            entry(now - 2000, RefreshOrigin.ALARM, RefreshOutcome.NO_LOCATION, AemetOutcome.NOT_APPLICABLE),
            entry(now - 1000, RefreshOrigin.WORKER, RefreshOutcome.OFFLINE, AemetOutcome.NOT_APPLICABLE),
            entry(now, RefreshOrigin.WORKER, RefreshOutcome.OK, AemetOutcome.FAILED),
        ).forEach { log.append(it) }

        val text = file.readText()
        assertFalse("hay números con decimales: $text", Regex("""-?\d+\.\d+""").containsMatchIn(text))
        assertFalse("hay coordenadas: $text", Regex("""\b(lat|lon|latitude|longitude|place|name)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text))
        siteNames.forEach { assertFalse("sale $it", text.contains(it)) }
    }

    // --- 018 P2: la línea de un aviso de la mañana ---

    @Test fun `una linea vieja sin morning se lee con morning nulo (H18)`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        file.writeText("{\"at\":${now - 60},\"origin\":\"ALARM\",\"outcome\":\"OK\",\"aemet\":\"UNCHANGED\",\"aemetBytes\":0}\n")
        val read = RefreshLog(file) { now }.entries.first()
        assertEquals(1, read.size)
        assertEquals(null, read.single().morning)
    }

    @Test fun `la linea de un aviso lleva la hora pedida, con IA o con reglas, y se lee igual`() = runTest {
        val file = tmp.newFolder().resolve("refresh_log.jsonl")
        val log = log(file)
        val notice = RefreshLogEntry(
            at = now, origin = RefreshOrigin.MORNING, outcome = RefreshOutcome.OK, aemet = AemetOutcome.NOT_APPLICABLE,
            morning = MorningMark(askedAt = now - 120, withAi = true),
        )
        log.append(entry(now - 300, RefreshOrigin.MORNING))
        log.append(notice)

        assertEquals(notice, RefreshLog(file) { now }.entries.first().last())
        assertTrue(file.readLines().last().contains("\"morning\""))
        assertTrue(file.readLines().last().contains("\"askedAt\""))
        assertTrue(file.readLines().last().contains("\"withAi\":true"))
    }
}
