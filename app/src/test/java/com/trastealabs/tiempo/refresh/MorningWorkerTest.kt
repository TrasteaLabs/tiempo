package com.trastealabs.tiempo.refresh

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.MorningMark
import com.trastealabs.tiempo.data.MorningSettings
import com.trastealabs.tiempo.data.RefreshLog
import com.trastealabs.tiempo.data.ReportStore
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.MorningStrings
import com.trastealabs.tiempo.domain.MorningText
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.reportFacts
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 018 P2 (H14–H16): lo que hace [MorningNotice] a la hora —refrescar con tope, pedir el parte, avisar una sola vez,
 * dejar su línea en el registro— con el reloj fijado y la red y el sistema detrás de funciones falsas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MorningWorkerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("morning_worker_test", Context.MODE_PRIVATE)
    private val zone = TestData.zone
    private val askedAt = TestData.at(2026, 11, 16, 7, 30)
    private var now = TestData.at(2026, 11, 16, 7, 31)

    private val strings = MorningStrings(
        title = { min, max -> "Hoy $min° a $max°" },
        warning = { level, phenomenon -> "Aviso $level por $phenomenon" },
        dataAt = { time -> "Datos de las $time" },
    )

    private lateinit var settings: MorningSettings
    private lateinit var reportStore: ReportStore
    private lateinit var log: RefreshLog
    private var snapshot: WeatherSnapshot? = null

    private var refreshCalls = 0
    private var refreshResult: suspend () -> RefreshResult = { RefreshResult.Ok }
    private var reportCalls = 0
    private var allowed = true
    private var postResult = true
    private val posted = mutableListOf<MorningText>()

    @Before fun setUp() {
        prefs.edit().clear().commit()
        settings = MorningSettings(prefs).also { it.setEnabled(true, TestData.at(2026, 11, 16, 6, 0), zone) }
        reportStore = ReportStore(tmp.newFile("ai_report.json").also { it.delete() })
        log = RefreshLog(tmp.newFile("refresh_log.jsonl").also { it.delete() }) { now }
        snapshot = TestData.snapshot(now)
    }

    private fun notice(refreshTimeoutMs: Long = MORNING_REFRESH_TIMEOUT_MS) = MorningNotice(
        settings = settings,
        refresh = { refreshCalls++; refreshResult() },
        snapshot = { snapshot },
        requestReport = { reportCalls++ },
        reportStore = reportStore,
        log = log,
        canPost = { allowed },
        post = { if (postResult) posted += it; postResult },
        strings = { strings },
        nowSeconds = { now },
        phoneZone = { zone },
        refreshTimeoutMs = refreshTimeoutMs,
    )

    @Test fun `antes de la hora no refresca ni avisa`() = runTest {
        now = askedAt - 60
        assertFalse(notice().runIfDue())
        assertEquals(0, refreshCalls)
        assertTrue(posted.isEmpty())
    }

    @Test fun `a la hora refresca, pide el parte, avisa, marca el dia y deja su linea en el registro`() = runTest {
        assertTrue(notice().runIfDue())

        assertEquals(1, refreshCalls)
        assertEquals(1, reportCalls)
        assertEquals(1, posted.size)
        assertEquals("Hoy 9° a 18°", posted.single().title)
        assertEquals("2026-11-16", settings.lastDay)
        val line = log.entries.first().single()
        assertEquals(RefreshOrigin.MORNING, line.origin)
        assertEquals(now, line.at)
        assertEquals(MorningMark(askedAt = askedAt, withAi = false), line.morning)
    }

    @Test fun `con un parte valido de hoy avisa con el y la linea dice con IA`() = runTest {
        val basis = reportFacts(snapshot!!, now, zone)!!.basis
        reportStore.write(AiReport(now - 600, AiProvider.CLAUDE, basis, today = "Mañana fresca y despejada."))
        assertTrue(notice().runIfDue())
        assertEquals("Mañana fresca y despejada.", posted.single().body)
        assertTrue(posted.single().withAi)
        assertEquals(MorningMark(askedAt, withAi = true), log.entries.first().single().morning)
    }

    @Test fun `un refresco que falla avisa igual con lo guardado y no pide el parte`() = runTest {
        refreshResult = { RefreshResult.Offline }
        assertTrue(notice().runIfDue())
        assertEquals(1, posted.size)
        assertEquals(0, reportCalls)
    }

    @Test fun `un refresco que tarda mas de 25 s se corta y avisa con lo guardado`() = runTest {
        var finished = false
        refreshResult = { delay(120_000); finished = true; RefreshResult.Ok }
        val started = testScheduler.currentTime
        assertTrue(notice().runIfDue())
        assertEquals(MORNING_REFRESH_TIMEOUT_MS, testScheduler.currentTime - started)
        assertFalse("el refresco lento se cancela", finished)
        assertEquals(1, posted.size)
        assertEquals(0, reportCalls)
    }

    @Test fun `dos entradas a la vez, la alarma y la red de seguridad, dan una sola notificacion`() = runTest {
        refreshResult = { delay(1_000); RefreshResult.Ok }
        val n = notice()
        val results = listOf(async { n.runIfDue(refreshFirst = true) }, async { n.runIfDue(refreshFirst = false) }).awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(1, posted.size)
        assertEquals(1, log.entries.first().size)
    }

    @Test fun `volver a llamar el mismo dia no avisa otra vez`() = runTest {
        val n = notice()
        assertTrue(n.runIfDue())
        now += 600
        assertFalse(n.runIfDue())
        assertEquals(1, posted.size)
    }

    @Test fun `un reinicio con otro objeto y los mismos ajustes tampoco avisa dos veces`() = runTest {
        assertTrue(notice().runIfDue())
        assertFalse(notice().runIfDue())
        assertEquals(1, posted.size)
    }

    @Test fun `sin permiso no publica, no marca el dia ni escribe en el registro, y despues avisa`() = runTest {
        allowed = false
        val n = notice()
        assertFalse(n.runIfDue())
        assertEquals(0, refreshCalls)
        assertEquals("", settings.lastDay)
        assertTrue(log.entries.first().isEmpty())

        // El usuario da el permiso dentro de las 2 h: avisa.
        allowed = true
        now += 1_800
        assertTrue(n.runIfDue())
    }

    @Test fun `si el sistema no publica, tampoco se marca`() = runTest {
        postResult = false
        assertFalse(notice().runIfDue())
        assertEquals("", settings.lastDay)
        assertTrue(log.entries.first().isEmpty())
    }

    @Test fun `si lo guardado no cubre hoy no se avisa ni se marca`() = runTest {
        val s = TestData.snapshot(now)
        snapshot = s.copy(daily = s.daily.filter { it.date != "2026-11-16" })
        assertFalse(notice().runIfDue())
        assertTrue(posted.isEmpty())
        assertEquals("", settings.lastDay)
    }

    @Test fun `sin ninguna prevision guardada no se avisa`() = runTest {
        snapshot = null
        assertFalse(notice().runIfDue())
        assertTrue(posted.isEmpty())
    }

    @Test fun `pasadas las dos horas de margen no se avisa`() = runTest {
        now = askedAt + 2 * 3600 + 1
        assertFalse(notice().runIfDue())
        assertEquals(0, refreshCalls)
    }

    @Test fun `apagada no hace nada`() = runTest {
        settings.setEnabled(false, now - 3600, zone)
        assertFalse(notice().runIfDue())
        assertEquals(0, refreshCalls)
    }

    @Test fun `un fallo inesperado no sale hacia fuera`() = runTest {
        refreshResult = { throw IllegalStateException("inventado") }
        assertFalse(notice().runIfDue())
        assertNull(posted.firstOrNull())
    }

    // --- el aviso de verdad, con el sistema de notificaciones de Robolectric ---

    @Test fun `MorningNotifier publica una notificacion con su canal, titulo y texto, y sin permiso no publica`() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val text = MorningText("Hoy 12° a 26° · Aviso amarillo por viento", "Mañana fresca.", withAi = true)

        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(MorningNotifier.post(context, text))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())

        shadowOf(manager).setNotificationsEnabled(true)
        assertTrue(MorningNotifier.post(context, text))
        assertTrue(MorningNotifier.post(context, text)) // dos veces seguidas: la segunda sustituye a la primera
        val shown = shadowOf(manager).allNotifications
        assertEquals(1, shown.size)
        assertEquals(MorningNotifier.CHANNEL_ID, shown.single().channelId)
        assertEquals("Hoy 12° a 26° · Aviso amarillo por viento", shown.single().extras.getString("android.title"))
        assertEquals("Mañana fresca.", shown.single().extras.getCharSequence("android.text").toString())
        assertEquals("morning", manager.getNotificationChannel(MorningNotifier.CHANNEL_ID).id)
    }
}
