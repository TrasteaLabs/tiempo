package com.trastealabs.tiempo.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.ui.MainActivity
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tamaños medidos en el OPPO: grande 328×146, mediano 328×90, pequeño 156×90 dp. Los tres (010, P3 y P4) son una imagen
 * con su descripción: lo que pinta cada uno se prueba en `WidgetFrameTest` y `WidgetPainterTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherWidgetTest {
    private val now = TestData.at(2025, 6, 10, 10, 20) // martes
    private val snapshot = TestData.snapshot(now, currentC = 12.0)
    private val openApp = ComponentName(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)

    private val large = 328 to 146
    private val medium = 328 to 90
    private val small = 156 to 90

    private fun widget(
        size: Pair<Int, Int>,
        state: StoredState = StoredState(snapshot, lastAttemptFailed = false),
        at: Long = now,
        block: GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest {
        setContext(ApplicationProvider.getApplicationContext<Context>())
        setAppWidgetSize(DpSize(size.first.dp, size.second.dp))
        provideComposable { WidgetContent(state, at) }
        block()
    }

    private fun warning(level: WarningLevel, phenomenon: String) =
        Warning("a", level, phenomenon, "Llanura de Ejemplo", now - 3600, now + 3600, null, "", null, now - 7200)

    private val layouts = mapOf(large to WidgetLayout.LARGE, medium to WidgetLayout.MEDIUM, small to WidgetLayout.SMALL)

    /** La descripción que debe llevar la imagen del tamaño [size] para [state] en [at]. */
    private fun description(size: Pair<Int, Int>, state: StoredState, at: Long = now) =
        widgetDescription(widgetFrame(state, layouts.getValue(size), at, ApplicationProvider.getApplicationContext<Context>()), ApplicationProvider.getApplicationContext<Context>())

    private val all = listOf(large, medium, small)

    // ---------- tres tamaños (H13) ----------

    @Test fun `los tres son una imagen con su descripcion, sin textos de Glance`() {
        all.forEach { size ->
            widget(size) {
                onNode(hasContentDescriptionEqualTo(description(size, StoredState(snapshot, false)))).assertExists()
                onNode(hasTextEqualTo("Villa Ejemplo")).assertDoesNotExist()
                onNode(hasTextEqualTo("12°")).assertDoesNotExist()
                onNode(hasText("Cargando")).assertDoesNotExist()
            }
        }
        assertTrue(description(medium, StoredState(snapshot, false)).startsWith("Villa Ejemplo, 12°, Nublado"))
    }

    // ---------- aviso y sin conexión (H15, H16) ----------

    private val withWarning = StoredState(snapshot.copy(warnings = listOf(warning(WarningLevel.ORANGE, "temperaturas máximas"))), false)

    @Test fun `el aviso va en la descripcion de los tres`() {
        all.forEach { size ->
            widget(size, withWarning) {
                onNode(hasContentDescriptionEqualTo(description(size, withWarning))).assertExists()
            }
            assertTrue(description(size, withWarning).contains("Aviso naranja"))
        }
    }

    private val offline = StoredState(
        TestData.snapshot(now, currentC = 12.0, fetchedAt = TestData.at(2025, 6, 10, 8, 15)),
        lastAttemptFailed = true,
    )

    @Test fun `sin conexion, la hora del dato en la descripcion de los tres`() {
        all.forEach { size ->
            widget(size, offline) { onNode(hasContentDescriptionEqualTo(description(size, offline))).assertExists() }
            assertTrue(description(size, offline).contains("Sin conexión, datos de las 08:15"))
        }
    }

    @Test fun `con aviso y sin conexion se ve el aviso`() {
        val both = offline.copy(snapshot = offline.snapshot!!.copy(warnings = listOf(warning(WarningLevel.RED, "tormentas"))))
        all.forEach { size ->
            widget(size, both) { onNode(hasContentDescriptionEqualTo(description(size, both))).assertExists() }
            assertTrue(description(size, both).contains("Aviso rojo") && !description(size, both).contains("Sin conexión"))
        }
    }

    // ---------- antes de abrir la app (H18) ----------

    @Test fun `sin snapshot pide abrir la app, nunca Cargando`() {
        all.forEach { size ->
            widget(size, StoredState(null, lastAttemptFailed = size == small)) {
                onNode(hasContentDescriptionEqualTo("Abre la app para empezar")).assertExists()
                onNode(hasText("Cargando")).assertDoesNotExist()
            }
        }
    }

    // ---------- casos límite ----------

    @Test fun `con datos guardados nunca dice Cargando`() {
        all.forEach { size -> widget(size) { onNode(hasText("Cargando")).assertDoesNotExist() } }
    }

    @Test fun `menos 3 y 43 grados se ven`() {
        val cold = StoredState(TestData.snapshot(now, currentC = -3.0), false)
        val hot = StoredState(TestData.snapshot(now, currentC = 43.0), false)
        all.forEach { size ->
            widget(size, cold) { onNode(hasContentDescriptionEqualTo(description(size, cold))).assertExists() }
            widget(size, hot) { onNode(hasContentDescriptionEqualTo(description(size, hot))).assertExists() }
            assertTrue(description(size, cold).contains("-3°") && description(size, hot).contains("43°"))
        }
    }

    @Test fun `pasada la medianoche, max y min del dia nuevo`() {
        val late = TestData.at(2025, 6, 10, 23, 59)
        val state = StoredState(TestData.snapshot(late, currentC = 12.0), false)
        val after = TestData.at(2025, 6, 11, 0, 1)
        all.forEach { size ->
            widget(size, state, at = after) { onNode(hasContentDescriptionEqualTo(description(size, state, after))).assertExists() }
            assertTrue(description(size, state, after).contains("máxima 19° y mínima 10°"))
        }
    }

    // ---------- 018 P3: la frase del parte ----------

    private val phrase = "Sol toda la tarde; mañana, nubes"
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val basis get() = reportFacts(snapshot, now, ZoneId.of(snapshot.zoneId))!!.basis
    private val report get() = AiReport(now, AiProvider.CLAUDE, basis, today = "Hoy…", widget = phrase)

    private fun widgetWith(
        size: Pair<Int, Int>, report: AiReport?, state: StoredState = StoredState(snapshot, false), at: Long = now,
        block: GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest(timeout = 30.seconds) { // el primer widget de la clase paga el arranque en frío de Robolectric (4–9 s)
        setContext(ctx)
        setAppWidgetSize(DpSize(size.first.dp, size.second.dp))
        provideComposable { WidgetContent(state, at, report) }
        block()
    }

    @Test fun `el 4x2 con parte de hoy lee la frase en su descripcion y el 4x1 y el 2x1 no`() {
        val state = StoredState(snapshot, false)
        widgetWith(large, report) {
            onNode(hasContentDescriptionEqualTo("Villa Ejemplo, 12°, Nublado, máxima 18° y mínima 9°. $phrase. Datos de las 10:20")).assertExists()
        }
        for (size in listOf(medium, small)) widgetWith(size, report) {
            onNode(hasContentDescriptionEqualTo(description(size, state))).assertExists()
        }
    }

    // Que una frase que no cabe NO se lea en TalkBack lo cubre `WidgetPainterTest` (`phraseShown`): aquí Robolectric mide el texto de mentira.
    @Test fun `si el pintor enseña la frase, TalkBack la lee en la descripcion`() {
        val state = StoredState(snapshot, false)
        widgetWith(large, report, state) { onNode(hasContentDescriptionEqualTo(description(large, state).replace(". Datos", ". $phrase. Datos"))).assertExists() }
    }

    @Test fun `sin parte, con el parte de ayer o con un parte viejo sin frase, el 4x2 es el de siempre`() {
        val state = StoredState(snapshot, false)
        val plain = description(large, state)
        val oldReport = AiReport(now, AiProvider.CLAUDE, basis, today = "Hoy…") // sin `widget`
        val yesterday = AiReport(now - 86_400, AiProvider.CLAUDE, basis.copy(date = "2025-06-09"), widget = phrase)
        for (r in listOf(null, oldReport, yesterday)) widgetWith(large, r) { onNode(hasContentDescriptionEqualTo(plain)).assertExists() }
    }

    @Test fun `pasada la medianoche local la frase de ayer no se ve`() {
        val late = TestData.at(2025, 6, 10, 23, 30)
        val state = StoredState(TestData.snapshot(late, currentC = 12.0), false)
        val facts = reportFacts(state.snapshot!!, late, ZoneId.of(state.snapshot!!.zoneId))!!
        val r = AiReport(late, AiProvider.CLAUDE, facts.basis, widget = phrase)
        widgetWith(large, r, state, late) { onNode(hasContentDescriptionEqualTo(description(large, state, late).replace(". Datos", ". $phrase. Datos"))).assertExists() }
        val after = TestData.at(2025, 6, 11, 0, 1)
        widgetWith(large, r, state, after) { onNode(hasContentDescriptionEqualTo(description(large, state, after))).assertExists() }
    }

    @Test fun `componer el widget con frase no consulta ninguna red (WidgetContent no recibe cliente y provideGlance solo lee disco)`() {
        val old = ProxySelector.getDefault()
        val asked = ArrayList<URI>()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> { asked.add(uri); return listOf(Proxy.NO_PROXY) }
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        })
        try {
            all.forEach { size -> widgetWith(size, report) { onNode(hasStartActivityClickAction(openApp)).assertExists() } }
        } finally {
            ProxySelector.setDefault(old)
        }
        assertTrue("peticiones: $asked", asked.isEmpty())
    }

    @Test fun `tocarlo abre la app en todos los tamanos`() {
        all.forEach { size ->
            widget(size) { onNode(hasStartActivityClickAction(openApp)).assertExists() }
            widget(size, StoredState(null, false)) { onNode(hasStartActivityClickAction(openApp)).assertExists() }
        }
    }
}
