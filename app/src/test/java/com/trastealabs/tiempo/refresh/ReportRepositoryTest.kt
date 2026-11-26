package com.trastealabs.tiempo.refresh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.ReportStore
import com.trastealabs.tiempo.data.ai.AI_DAILY_ASK
import com.trastealabs.tiempo.data.ai.AI_DAILY_HERE
import com.trastealabs.tiempo.data.ai.AI_DAILY_SAVED
import com.trastealabs.tiempo.data.ai.AiException
import com.trastealabs.tiempo.data.ai.AiFailure
import com.trastealabs.tiempo.data.ai.AiSettings
import com.trastealabs.tiempo.data.ai.AiStatus
import com.trastealabs.tiempo.data.ai.KeyVault
import com.trastealabs.tiempo.data.ai.ReportReply
import com.trastealabs.tiempo.data.ai.ReportRequest
import com.trastealabs.tiempo.data.ai.ReportScope
import com.trastealabs.tiempo.data.ai.ReportWriter
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AskFailure
import com.trastealabs.tiempo.domain.AskKind
import com.trastealabs.tiempo.domain.AskResult
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.reportFacts
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** 016 P3: H5 (generar y guardar), H6 (no guardar lo malo), H10 (pocas llamadas), H11 (topes), H12 (nunca rompe), H13, H16. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("report_repo_test", Context.MODE_PRIVATE)
    private val zone = TestData.zone
    private val start = TestData.at(2026, 11, 12, 0, 5)
    private var now = start

    private class FakeVault(val keys: MutableMap<AiProvider, String> = mutableMapOf()) : KeyVault {
        override fun get(p: AiProvider) = keys[p]
        override fun put(p: AiProvider, key: String) { keys[p] = key }
        override fun clear(p: AiProvider) { keys.remove(p) }
        override fun hint(p: AiProvider) = keys[p]?.takeLast(4)
    }

    /** Cuenta las llamadas y contesta con lo que diga [reply] (por defecto, un parte bueno según los hechos). */
    private inner class FakeWriter(val provider: AiProvider, val key: String) : ReportWriter {
        override suspend fun write(request: ReportRequest): ReportReply {
            calls++
            seen += provider to key
            onCall?.invoke()
            return reply(request)
        }
    }

    private var calls = 0
    private val seen = mutableListOf<Pair<AiProvider, String>>()
    private var onCall: (suspend () -> Unit)? = null
    private var reply: suspend (ReportRequest) -> ReportReply = { good() }

    private val vault = FakeVault()
    private lateinit var settings: AiSettings
    private lateinit var store: ReportStore
    private lateinit var snapshot: WeatherSnapshot

    @Before fun setUp() {
        prefs.edit().clear().commit()
        settings = AiSettings(prefs, zone = { zone })
        store = ReportStore(tmp.newFile("ai_report.json").also { it.delete() })
        snapshot = TestData.snapshot(start)
        vault.keys[AiProvider.CLAUDE] = "clave-de-prueba-1234"
    }

    private fun repo(timeoutMs: Long = 45_000) =
        ReportRepository({ p, k -> FakeWriter(p, k) }, vault, settings, { now }, timeoutMs)

    private fun facts(s: WeatherSnapshot = snapshot) = checkNotNull(reportFacts(s, now, zone))

    private fun jsonOf(today: String?, days: List<Pair<String, String>> = emptyList()): String {
        val d = days.joinToString(",") { """{"date":"${it.first}","text":"${it.second}"}""" }
        val t = if (today == null) "" else """"today":"$today","""
        return """{$t"days":[$d],"warnings":[]}"""
    }

    /** Un parte que pasa la comprobación: solo cifras de los hechos. */
    private fun good(s: WeatherSnapshot = snapshot): ReportReply {
        val f = facts(s)
        val next = f.days[1]
        return ReportReply(
            jsonOf("Hoy máxima de ${f.today.maxC} grados.", listOf(next.date to "Máxima de ${next.maxC} grados.")),
            inputTokens = 2500, outputTokens = 1500,
        )
    }

    private suspend fun runDue(s: WeatherSnapshot = snapshot, scope: ReportScope = ReportScope.HERE) =
        repo().refreshIfDue(s, store, scope)

    @Test fun `sin clave no hay llamada ni se guarda nada`() = runTest {
        vault.keys.clear()
        assertFalse(runDue())
        assertEquals(0, calls)
        assertNull(store.read())
    }

    @Test fun `con clave y sin parte hace una llamada y guarda el parte (H5)`() = runTest {
        assertTrue(runDue())
        assertEquals(1, calls)
        val saved = checkNotNull(store.read())
        assertEquals("Hoy máxima de ${facts().today.maxC} grados.", saved.today)
        assertEquals(AiProvider.CLAUDE, saved.provider)
        assertEquals(now, saved.generatedAt)
        assertNull(settings.status.value?.failure)
    }

    @Test fun `los tokens llegan al contador (H13)`() = runTest {
        runDue()
        val u = settings.usage.value
        assertEquals(1, u.dayHere)
        assertEquals(1, u.monthCalls)
        assertEquals(2500L, u.monthInputTokens)
        assertEquals(1500L, u.monthOutputTokens)
    }

    @Test fun `48 refrescos de un dia con la misma prevision hacen como mucho 5 llamadas (H10)`() = runTest {
        repeat(48) {
            repo().refreshIfDue(snapshot, store, ReportScope.HERE)
            now += 30 * 60
        }
        assertTrue("llamadas: $calls", calls in 1..5)
    }

    @Test fun `una prevision que cambia de verdad vuelve a llamar`() = runTest {
        runDue()
        assertEquals(1, calls)
        now += 30 * 60
        runDue()
        assertEquals(1, calls)
        val hotter = snapshot.copy(daily = snapshot.daily.map { it.copy(maxC = it.maxC + 6) })
        now += 30 * 60
        reply = { good(hotter) }
        assertTrue(runDue(hotter))
        assertEquals(2, calls)
    }

    @Test fun `tope de 12 en HERE y aparte 12 en SAVED, y a medianoche vuelve a llamar (H11)`() = runTest {
        repeat(AI_DAILY_HERE + 3) {
            store.clear()
            repo().refreshIfDue(snapshot, store, ReportScope.HERE)
        }
        assertEquals(AI_DAILY_HERE, calls)
        repeat(AI_DAILY_SAVED + 3) {
            store.clear()
            repo().refreshIfDue(snapshot, store, ReportScope.SAVED)
        }
        assertEquals(AI_DAILY_HERE + AI_DAILY_SAVED, calls)
        // Otro día: el contador vuelve a cero sin que nadie lo reinicie.
        now += 24 * 3600
        store.clear()
        assertTrue(repo().refreshIfDue(snapshot, store, ReportScope.HERE))
        assertEquals(AI_DAILY_HERE + AI_DAILY_SAVED + 1, calls)
    }

    @Test fun `los intentos fallidos tambien cuentan para el tope y a medianoche vuelve a llamar (H11)`() = runTest {
        reply = { throw AiException(AiFailure.OFFLINE, "sin red") }
        repeat(AI_DAILY_HERE + 1) {
            now += 26 * 60 // salta la espera de 25 min
            repo().refreshIfDue(snapshot, store, ReportScope.HERE)
        }
        assertEquals(AI_DAILY_HERE, calls)
        assertEquals(AI_DAILY_HERE, settings.usage.value.dayHere)
        assertEquals(0L, settings.usage.value.monthInputTokens)
        // El de SAVED va aparte.
        repo().refreshIfDue(snapshot, store, ReportScope.SAVED)
        assertEquals(AI_DAILY_HERE + 1, calls)
        // Otro día vuelve a llamar.
        now = TestData.at(2026, 11, 13, 0, 10)
        repo().refreshIfDue(snapshot, store, ReportScope.HERE)
        assertEquals(AI_DAILY_HERE + 2, calls)
    }

    @Test fun `dos refreshIfDue a la vez hacen una sola llamada`() = runTest {
        onCall = { delay(1_000) }
        val r = repo()
        val results = listOf(
            async { r.refreshIfDue(snapshot, store, ReportScope.HERE) },
            async { r.refreshIfDue(snapshot, store, ReportScope.HERE) },
        ).awaitAll()
        assertEquals(1, calls)
        assertEquals(1, results.count { it })
    }

    @Test fun `cada fallo del escritor deja false, el parte anterior y el motivo (H6, H12)`() = runTest {
        runDue()
        val before = checkNotNull(store.read())
        for (failure in AiFailure.entries) {
            now += 7 * 3600 // pasa la espera de 25 min y el parte anterior ya es viejo: toca
            reply = { throw AiException(failure, "fallo de prueba") }
            assertFalse("$failure", runDue())
            assertEquals(before, store.read())
            assertEquals(failure, settings.status.value?.failure)
        }
    }

    @Test fun `un fallo inesperado tampoco lanza`() = runTest {
        reply = { throw IllegalStateException("algo raro") }
        assertFalse(runDue())
        assertNull(store.read())
        assertNotNull(settings.status.value?.failure)
    }

    @Test fun `si tarda mas que el tiempo maximo da TIMEOUT y conserva el anterior`() = runTest {
        runDue()
        val before = store.read()
        now += 7 * 3600
        reply = { delay(10 * 60_000); good() }
        assertFalse(repo(timeoutMs = 1_000).refreshIfDue(snapshot, store, ReportScope.HERE))
        assertEquals(before, store.read())
        assertEquals(AiFailure.TIMEOUT, settings.status.value?.failure)
    }

    @Test fun `una respuesta que no es el JSON pedido es BAD_REPLY y no se guarda`() = runTest {
        reply = { ReportReply("esto no es json", 100, 10) }
        assertFalse(runDue())
        assertNull(store.read())
        assertEquals(AiFailure.BAD_REPLY, settings.status.value?.failure)
    }

    @Test fun `tras un fallo no reintenta hasta pasados 25 minutos`() = runTest {
        reply = { throw AiException(AiFailure.OFFLINE, "sin red") }
        runDue()
        assertEquals(1, calls)
        now += 24 * 60
        runDue()
        assertEquals(1, calls)
        now += 2 * 60
        runDue()
        assertEquals(2, calls)
    }

    @Test fun `con today malo pero dias buenos se guarda sin today`() = runTest {
        val f = facts()
        val next = f.days[1]
        reply = { ReportReply(jsonOf("Máxima de 99 grados.", listOf(next.date to "Máxima de ${next.maxC} grados.")), 1, 1) }
        assertTrue(runDue())
        val saved = checkNotNull(store.read())
        assertNull(saved.today)
        assertEquals(mapOf(next.date to "Máxima de ${next.maxC} grados."), saved.days)
    }

    @Test fun `con todo malo no se guarda y queda el anterior`() = runTest {
        runDue()
        val before = store.read()
        now += 7 * 3600
        reply = { ReportReply(jsonOf("Máxima de 99 grados.", listOf(facts().days[1].date to "Hará 88 grados.")), 1, 1) }
        assertFalse(runDue())
        assertEquals(before, store.read())
        assertEquals(AiFailure.BAD_REPLY, settings.status.value?.failure)
    }

    @Test fun `testNow ignora tope y espera, cuenta y guarda`() = runTest {
        reply = { throw AiException(AiFailure.OFFLINE, "sin red") }
        runDue()
        val callsBefore = calls
        reply = { good() }
        // Con la espera de 25 min sin cumplir y el tope agotado a la vez.
        repeat(AI_DAILY_HERE) { settings.record(ReportScope.HERE, ReportReply("{}", 0, 0), now) }
        val result = repo().testNow(snapshot, store)
        assertTrue(result.isSuccess)
        assertEquals(callsBefore + 1, calls)
        assertNotNull(store.read())
        assertEquals(AI_DAILY_HERE + 2, settings.usage.value.dayHere) // el intento fallido, los 12 y la prueba
        assertNull(settings.status.value?.failure)
    }

    @Test fun `testNow devuelve el fallo con nombre y no lanza`() = runTest {
        reply = { throw AiException(AiFailure.BAD_KEY, "401") }
        val result = repo().testNow(snapshot, store)
        assertTrue(result.isFailure)
        assertEquals(AiFailure.BAD_KEY, (result.exceptionOrNull() as AiException).failure)
        assertEquals(AiFailure.BAD_KEY, settings.status.value?.failure)
        assertNull(store.read())
    }

    @Test fun `testNow sin clave da NO_KEY sin llamar`() = runTest {
        vault.keys.clear()
        val result = repo().testNow(snapshot, store)
        assertEquals(AiFailure.NO_KEY, (result.exceptionOrNull() as AiException).failure)
        assertEquals(0, calls)
    }

    @Test fun `cambiar de proveedor usa la clave del nuevo y no borra la otra`() = runTest {
        vault.keys[AiProvider.GEMINI] = "otra-clave-5678"
        settings.setProvider(AiProvider.GEMINI)
        runDue()
        assertEquals(listOf(AiProvider.GEMINI to "otra-clave-5678"), seen)
        assertEquals("clave-de-prueba-1234", vault.keys[AiProvider.CLAUDE])
        assertEquals(AiProvider.GEMINI, store.read()?.provider)
    }

    @Test fun `un parte guardado de AiReport no lleva la clave`() = runTest {
        runDue()
        val text = tmp.root.walkTopDown().filter { it.isFile }.joinToString { it.readText() }
        assertFalse(text.contains("clave-de-prueba-1234"))
    }

    // ---------- 018 P1: onSaved ----------

    @Test fun `onSaved se llama una vez al guardar un parte de HERE y no con SAVED ni con un fallo`() = runTest {
        var saved = 0
        fun withHook() = ReportRepository({ p, k -> FakeWriter(p, k) }, vault, settings, { now }, onSaved = { saved++ })
        assertTrue(withHook().refreshIfDue(snapshot, store, ReportScope.HERE))
        assertEquals(1, saved)
        store.clear()
        assertTrue(withHook().refreshIfDue(snapshot, store, ReportScope.SAVED))
        assertEquals(1, saved)
        // un fallo no avisa
        store.clear()
        now += 7 * 3600
        reply = { throw AiException(AiFailure.OFFLINE, "sin red") }
        assertFalse(withHook().refreshIfDue(snapshot, store, ReportScope.HERE))
        assertEquals(1, saved)
        // «Probar ahora» guarda un parte de HERE
        reply = { good() }
        assertTrue(withHook().testNow(snapshot, store).isSuccess)
        assertEquals(2, saved)
    }

    @Test fun `un onSaved que lanza no rompe ni devuelve false, y el parte queda guardado`() = runTest {
        val r = ReportRepository({ p, k -> FakeWriter(p, k) }, vault, settings, { now }, onSaved = { error("el widget no está") })
        assertTrue(r.refreshIfDue(snapshot, store, ReportScope.HERE))
        assertNotNull(store.read())
        assertNull(settings.status.value?.failure)
    }

    // ---------- 018 P1: la pregunta ----------

    private val question = "¿Puedo tender mañana?"

    private fun answerJson(text: String, kind: String = "answer") = """{"kind":"$kind","text":"$text"}"""

    private fun okAnswer() = ReportReply(answerJson("Hoy máxima de ${facts().today.maxC} grados."), inputTokens = 2600, outputTokens = 90)

    private suspend fun ask(q: String = question) = repo().ask(snapshot, q)

    @Test fun `ask sin clave da NO_KEY sin llamar ni contar`() = runTest {
        vault.keys.clear()
        assertEquals(AskResult.Failed(AskFailure.NO_KEY), ask())
        assertEquals(0, calls)
        assertEquals(0, settings.usage.value.dayAsk)
    }

    @Test fun `ask correcto da la respuesta con su hora y la de los datos, y cuenta en dayAsk y no en los partes`() = runTest {
        reply = { okAnswer() }
        val result = ask()
        val answer = (result as AskResult.Ok).answer
        assertEquals(AskKind.ANSWER, answer.kind)
        assertEquals("Hoy máxima de ${facts().today.maxC} grados.", answer.text)
        assertEquals(now, answer.at)
        assertEquals(snapshot.fetchedAt, answer.dataAt)
        val u = settings.usage.value
        assertEquals(1, u.dayAsk)
        assertEquals(0, u.dayHere)
        assertEquals(0, u.daySaved)
        assertEquals(1, u.monthCalls)
        assertEquals(2600L, u.monthInputTokens)
        assertEquals(90L, u.monthOutputTokens)
    }

    @Test fun `ask de fuera de tema y fuera de rango dan su tipo sin texto`() = runTest {
        reply = { ReportReply(answerJson("", "off_topic"), 10, 5) }
        assertEquals(AskKind.OFF_TOPIC, (ask("¿Quién ganó la liga?") as AskResult.Ok).answer.kind)
        reply = { ReportReply(answerJson("", "out_of_range"), 10, 5) }
        val out = (ask("¿Y dentro de 15 días?") as AskResult.Ok).answer
        assertEquals(AskKind.OUT_OF_RANGE, out.kind)
        assertEquals("", out.text)
    }

    @Test fun `ask manda la pregunta recortada a 200 y los hechos del parte`() = runTest {
        var sent: ReportRequest? = null
        reply = { sent = it; okAnswer() }
        ask("z".repeat(250))
        val user = checkNotNull(sent).user
        assertTrue(user.contains("\"pregunta\":\"" + "z".repeat(200) + "\""))
        assertFalse(user.contains("z".repeat(201)))
        assertTrue(user.contains("\"dias\""))
    }

    @Test fun `ask con la pregunta vacia no llama ni cuenta`() = runTest {
        assertEquals(AskResult.Failed(AskFailure.OTHER), ask("   "))
        assertEquals(0, calls)
        assertEquals(0, settings.usage.value.dayAsk)
    }

    @Test fun `ask con cifra falsa es UNSURE, cuenta y no guarda nada`() = runTest {
        reply = { ReportReply(answerJson("Mañana hará 41 grados."), 100, 20) }
        assertEquals(AskResult.Failed(AskFailure.UNSURE), ask())
        assertEquals(1, settings.usage.value.dayAsk)
        assertEquals(100L, settings.usage.value.monthInputTokens)
        assertNull(store.read())
    }

    @Test fun `ask con una respuesta que no es el json pedido es UNSURE`() = runTest {
        reply = { ReportReply("esto no es json", 100, 20) }
        assertEquals(AskResult.Failed(AskFailure.UNSURE), ask())
    }

    @Test fun `cada fallo del proveedor da su AskFailure y cuenta el intento`() = runTest {
        val expected = mapOf(
            AiFailure.NO_KEY to AskFailure.NO_KEY,
            AiFailure.BAD_KEY to AskFailure.BAD_KEY,
            AiFailure.NO_CREDIT to AskFailure.NO_CREDIT,
            AiFailure.RATE_LIMIT to AskFailure.OTHER,
            AiFailure.OFFLINE to AskFailure.OFFLINE,
            AiFailure.PROVIDER to AskFailure.OTHER,
            AiFailure.BAD_REPLY to AskFailure.CUT,
            AiFailure.TIMEOUT to AskFailure.TIMEOUT,
        )
        assertEquals(AiFailure.entries.toSet(), expected.keys)
        var attempts = 0
        for ((failure, ask) in expected) {
            reply = { throw AiException(failure, "fallo de prueba") }
            assertEquals("$failure", AskResult.Failed(ask), ask())
            attempts++
            assertEquals(attempts, settings.usage.value.dayAsk)
        }
    }

    @Test fun `un fallo inesperado del escritor es OTHER y no lanza`() = runTest {
        reply = { throw IllegalStateException("algo raro") }
        assertEquals(AskResult.Failed(AskFailure.OTHER), ask())
        assertEquals(1, settings.usage.value.dayAsk)
    }

    @Test fun `si tarda mas de 20 s da TIMEOUT y cuenta el intento`() = runTest {
        reply = { delay(10 * 60_000); okAnswer() }
        assertEquals(AskResult.Failed(AskFailure.TIMEOUT), ask())
        assertEquals(1, settings.usage.value.dayAsk)
        assertEquals(20_000L, ASK_TIMEOUT_MS)
    }

    @Test fun `cancelar la pregunta en curso cuenta el intento`() = runTest {
        reply = { delay(60_000); okAnswer() }
        val job = launch { ask() }
        runCurrent()
        assertEquals(1, calls)
        job.cancel()
        job.join()
        assertEquals(1, settings.usage.value.dayAsk)
        assertNull(store.read())
    }

    @Test fun `el tope de 20 al dia, la 21 no llama, y los intentos fallidos cuentan`() = runTest {
        val ok: suspend (ReportRequest) -> ReportReply = { okAnswer() }
        val offline: suspend (ReportRequest) -> ReportReply = { throw AiException(AiFailure.OFFLINE, "sin red") }
        repeat(AI_DAILY_ASK) { i ->
            reply = if (i % 2 == 0) ok else offline
            ask()
        }
        assertEquals(AI_DAILY_ASK, calls)
        assertEquals(AI_DAILY_ASK, settings.usage.value.dayAsk)
        assertEquals(AskResult.Failed(AskFailure.CAP), ask())
        assertEquals(AI_DAILY_ASK, calls)
        assertEquals(AI_DAILY_ASK, settings.usage.value.dayAsk)
    }

    @Test fun `al pasar la medianoche local el tope de preguntas vuelve a cero`() = runTest {
        reply = { okAnswer() }
        repeat(AI_DAILY_ASK) { ask() }
        assertEquals(AskResult.Failed(AskFailure.CAP), ask())
        now = TestData.at(2026, 11, 13, 0, 10)
        assertTrue(ask() is AskResult.Ok)
        assertEquals(1, settings.usage.value.dayAsk)
    }

    @Test fun `las preguntas no gastan el tope de los partes`() = runTest {
        reply = { okAnswer() }
        repeat(AI_DAILY_ASK) { ask() }
        assertEquals(0, settings.usage.value.dayHere)
        assertEquals(0, settings.usage.value.daySaved)
        reply = { good() }
        assertTrue(runDue()) // el parte sigue pudiendo pedirse con el tope de preguntas agotado
        assertEquals(1, settings.usage.value.dayHere)
    }

    @Test fun `los partes no gastan el tope de las preguntas`() = runTest {
        repeat(AI_DAILY_HERE) { settings.recordAttempt(ReportScope.HERE, now) }
        reply = { okAnswer() }
        assertTrue(ask() is AskResult.Ok)
        assertEquals(1, settings.usage.value.dayAsk)
    }

    @Test fun `ask no cambia settings status, ni se frena por un fallo de parte reciente, ni escribe el parte`() = runTest {
        settings.setStatus(now - 60, AiFailure.BAD_KEY)
        reply = { okAnswer() }
        assertTrue(ask() is AskResult.Ok)
        assertEquals(AiStatus(now - 60, AiFailure.BAD_KEY), settings.status.value)
        reply = { throw AiException(AiFailure.NO_CREDIT, "sin saldo") }
        ask()
        assertEquals(AiStatus(now - 60, AiFailure.BAD_KEY), settings.status.value)
        assertNull(store.read())
    }

    @Test fun `ask no dispara onSaved`() = runTest {
        var saved = 0
        val r = ReportRepository({ p, k -> FakeWriter(p, k) }, vault, settings, { now }, onSaved = { saved++ })
        reply = { okAnswer() }
        assertTrue(r.ask(snapshot, question) is AskResult.Ok)
        assertEquals(0, saved)
    }

    @Test fun `ask no espera a un parte en curso`() = runTest {
        val order = mutableListOf<String>()
        reply = { req ->
            if (req.system.startsWith("Eres un meteorólogo de cabecera que escribe")) {
                delay(30_000)
                order += "parte"
                good()
            } else {
                order += "pregunta"
                okAnswer()
            }
        }
        val part = launch { runDue() }
        runCurrent() // el parte está dentro de su llamada, con su Mutex tomado
        assertTrue(ask() is AskResult.Ok)
        part.join()
        assertEquals(listOf("pregunta", "parte"), order)
    }

    @Test fun `ni la pregunta ni la respuesta se escriben en Log, en preferencias ni en disco`() = runTest {
        val secretQuestion = "pregunta-secreta-¿lloverá en casa?"
        val secretText = "respuesta-secreta máxima de ${facts().today.maxC} grados"
        for (r in listOf<suspend (ReportRequest) -> ReportReply>(
            { ReportReply(answerJson(secretText), 10, 5) }, // bien
            { ReportReply(answerJson("$secretText 999 grados"), 10, 5) }, // UNSURE
            { throw AiException(AiFailure.PROVIDER, "fallo con $secretQuestion") }, // fallo del proveedor
            { throw IllegalStateException("fallo con $secretQuestion") }, // inesperado
        )) {
            reply = r
            ask(secretQuestion)
        }
        val logs = ShadowLog.getLogs().joinToString("\n") { it.msg.orEmpty() + (it.throwable?.message ?: "") }
        assertFalse(logs.contains("pregunta-secreta"))
        assertFalse(logs.contains("respuesta-secreta"))
        val prefsText = prefs.all.values.joinToString("|")
        assertFalse(prefsText.contains("secret"))
        val disk = (tmp.root.walkTopDown() + context.filesDir.walkTopDown() + context.cacheDir.walkTopDown())
            .filter { it.isFile }.joinToString { runCatching { it.readText() }.getOrDefault("") }
        assertFalse(disk.contains("pregunta-secreta"))
        assertFalse(disk.contains("respuesta-secreta"))
    }

    @Test fun `una prevision guardada rota no hace lanzar a ask`() = runTest {
        val broken = snapshot.copy(zoneId = "No/Existe")
        assertEquals(AskResult.Failed(AskFailure.OTHER), repo().ask(broken, question))
        assertEquals(0, calls)
    }

    @Test fun `una pregunta con datos demasiado viejos no llama`() = runTest {
        val old = snapshot.copy(hourly = snapshot.hourly.filter { it.time + 3600 <= now - 3600 })
        assertEquals(AskResult.Failed(AskFailure.UNSURE), repo().ask(old, question))
        assertEquals(0, calls)
        assertEquals(0, settings.usage.value.dayAsk)
    }
}
