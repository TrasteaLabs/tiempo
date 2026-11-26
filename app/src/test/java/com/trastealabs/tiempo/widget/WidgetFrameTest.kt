package com.trastealabs.tiempo.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.LOADING_PHOTO
import com.trastealabs.tiempo.domain.MoonShow
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.WIDGET_MAX
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.domain.skyPhotoNow
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lo que pinta el 4×2 (010, P3), sin dibujar nada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetFrameTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val now = TestData.at(2025, 6, 10, 10, 20)
    private val snapshot = TestData.snapshot(now, currentC = 12.0)
    private val state = StoredState(snapshot, lastAttemptFailed = false)

    private fun frame(s: StoredState = state, at: Long = now, layout: WidgetLayout = WidgetLayout.LARGE) = widgetFrame(s, layout, at, context)

    private fun warning(level: WarningLevel, phenomenon: String, expires: Long = now + 3600) =
        Warning("a-$phenomenon", level, phenomenon, "Llanura de Ejemplo", now - 3600, expires, null, "", null, now - 7200)

    private fun withWarnings(vararg w: Warning) = StoredState(snapshot.copy(warnings = w.toList()), false)

    @Test fun `la foto es la de la principal y sin datos la de nubes`() {
        assertEquals(skyPhotoNow(snapshot, now), widgetPhoto(state, now))
        assertEquals(skyPhotoNow(snapshot, now), frame().photo)
        assertEquals(LOADING_PHOTO, widgetPhoto(StoredState(null, false), now))
        assertEquals(SkyPhoto.CLOUDS, frame(StoredState(null, false)).photo)
    }

    @Test fun `sin datos no hay sitio y la foto es la de nubes`() {
        val f = frame(StoredState(null, false))
        assertNull(f.place)
        assertTrue(f.hours.isEmpty())
        assertNull(f.badge)
        assertEquals(MoonShow.NONE, f.moon)
        assertEquals("Abre la app para empezar", widgetDescription(f, context))
    }

    @Test fun `Ahora y las cinco horas siguientes con su icono y temperatura`() {
        val f = frame()
        assertEquals(listOf("Ahora", "11:00", "12:00", "13:00", "14:00", "15:00"), f.hours.map { it.label })
        assertEquals("12°", f.hours[0].temp)
        assertEquals("10°", f.hours[1].temp)
        assertTrue(f.hours.all { it.icon != 0 })
        assertEquals("Villa Ejemplo", f.place)
        assertEquals("12°", f.temp)
        assertEquals("18°", f.max)
        assertEquals("9°", f.min)
        assertEquals("Nublado", f.skyText)
        assertEquals(context.getString(R.string.now), f.hours[0].label)
    }

    @Test fun `el mediano lleva tres horas y el pequeno ninguna`() {
        assertEquals(listOf("11:00", "12:00", "13:00"), frame(layout = WidgetLayout.MEDIUM).hours.map { it.label })
        assertTrue(frame(layout = WidgetLayout.SMALL).hours.isEmpty())
    }

    @Test fun `la etiqueta es la hora del dato, y sin conexion la nube tachada`() {
        assertEquals(WidgetBadge.Updated("10:20"), frame().badge)
        val offline = StoredState(TestData.snapshot(now, fetchedAt = TestData.at(2025, 6, 10, 8, 15)), true)
        assertEquals(WidgetBadge.Offline("08:15"), frame(offline).badge)
    }

    @Test fun `con varios avisos sale el mas grave, con su hora de fin si el fenomeno es corto`() {
        val s = withWarnings(
            warning(WarningLevel.YELLOW, "viento"),
            warning(WarningLevel.RED, "tormentas", expires = TestData.at(2025, 6, 10, 21, 0)),
            warning(WarningLevel.ORANGE, "lluvias"),
        )
        assertEquals(WidgetBadge.Warning(WarningLevel.RED, "Tormentas · hasta las 21:00"), frame(s).badge)
    }

    @Test fun `un fenomeno largo va solo, y un aviso que acaba otro dia tambien`() {
        val long = withWarnings(warning(WarningLevel.ORANGE, "temperaturas máximas", expires = TestData.at(2025, 6, 10, 20, 0)))
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Temperaturas máximas"), frame(long).badge)
        val tomorrow = withWarnings(warning(WarningLevel.ORANGE, "tormentas", expires = TestData.at(2025, 6, 11, 6, 0)))
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Tormentas"), frame(tomorrow).badge)
    }

    @Test fun `el aviso manda sobre sin conexion`() {
        val both = StoredState(snapshot.copy(warnings = listOf(warning(WarningLevel.RED, "tormentas"))), lastAttemptFailed = true)
        assertTrue(frame(both).badge is WidgetBadge.Warning)
    }

    @Test fun `la luna sale de noche segun el cielo y no de dia`() {
        val night = TestData.at(2025, 6, 10, 23, 30)
        // El código 3 de TestData es nublado: de noche, luna velada; de día, ninguna.
        val nightSnapshot = TestData.snapshot(night).let { it.copy(current = it.current.copy(isDay = false)) }
        val nightFrame = frame(StoredState(nightSnapshot, false), at = night)
        assertEquals(MoonShow.VEILED, nightFrame.moon)
        assertTrue(nightFrame.moonPhase != null)
        assertEquals(MoonShow.NONE, frame().moon)
        assertNull(frame().moonPhase)
    }

    @Test fun `pasada la medianoche el max y min son los del dia nuevo`() {
        val late = TestData.at(2025, 6, 10, 23, 59)
        val after = TestData.at(2025, 6, 11, 0, 1)
        val f = frame(StoredState(TestData.snapshot(late), false), at = after)
        assertEquals("19°", f.max)
        assertEquals("10°", f.min)
    }

    @Test fun `menos 5 y 44 grados`() {
        assertEquals("-5°", frame(StoredState(TestData.snapshot(now, currentC = -5.0), false)).temp)
        val hot = TestData.at(2025, 6, 10, 15, 0)
        val f = frame(StoredState(TestData.snapshot(hot, currentC = 44.0), false), at = hot)
        assertEquals("44°", f.temp)
        assertEquals(skyPhotoNow(TestData.snapshot(hot, currentC = 44.0), hot), f.photo)
    }

    @Test fun `la descripcion dice sitio, temperatura, cielo, max y min y el aviso`() {
        val s = withWarnings(warning(WarningLevel.ORANGE, "tormentas", expires = TestData.at(2025, 6, 10, 21, 0)))
        assertEquals(
            "Villa Ejemplo, 12°, Nublado, máxima 18° y mínima 9°. Aviso naranja: Tormentas · hasta las 21:00",
            widgetDescription(frame(s), context),
        )
        assertEquals("Villa Ejemplo, 12°, Nublado, máxima 18° y mínima 9°. Datos de las 10:20", widgetDescription(frame(), context))
        val offline = StoredState(TestData.snapshot(now, fetchedAt = TestData.at(2025, 6, 10, 8, 15)), true)
        assertTrue(widgetDescription(frame(offline), context).endsWith("Sin conexión, datos de las 08:15"))
    }

    // ---------- P4: aviso y sin datos por tamaño ----------

    @Test fun `el texto del aviso por tamano, entero, primera palabra o Aviso`() {
        val s = withWarnings(warning(WarningLevel.ORANGE, "temperaturas máximas", expires = TestData.at(2025, 6, 10, 20, 0)))
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Temperaturas máximas"), frame(s).badge)
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Temperaturas"), frame(s, layout = WidgetLayout.MEDIUM).badge)
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Aviso"), frame(s, layout = WidgetLayout.SMALL).badge)
    }

    @Test fun `en el mediano y el pequeno el aviso manda sobre sin conexion y la hora a secas no sale`() {
        val both = StoredState(snapshot.copy(warnings = listOf(warning(WarningLevel.RED, "tormentas"))), lastAttemptFailed = true)
        assertTrue(frame(both, layout = WidgetLayout.MEDIUM).badge is WidgetBadge.Warning)
        assertTrue(frame(both, layout = WidgetLayout.SMALL).badge is WidgetBadge.Warning)
        assertNull(frame(layout = WidgetLayout.MEDIUM).badge)
        assertNull(frame(layout = WidgetLayout.SMALL).badge)
        val offline = StoredState(TestData.snapshot(now, fetchedAt = TestData.at(2025, 6, 10, 8, 15)), true)
        assertEquals(WidgetBadge.Offline("08:15"), frame(offline, layout = WidgetLayout.SMALL).badge)
    }

    @Test fun `sin datos, los tres tamanos piden abrir la app con la foto de nubes`() {
        for (layout in WidgetLayout.entries) {
            val f = widgetFrame(StoredState(null, false), layout, now, context)
            assertNull(f.place)
            assertEquals(LOADING_PHOTO, f.photo)
            assertEquals("Abre la app para empezar", widgetDescription(f, context))
        }
    }

    // ---------- 018 P3: la frase del parte ----------

    private fun reportOf(
        at: Long = now, snapshot: WeatherSnapshot = this.snapshot, widget: String? = "Sol toda la tarde; mañana, nubes", shift: Int = 0,
    ): AiReport {
        val basis = reportFacts(snapshot, at, ZoneId.of(snapshot.zoneId))!!.basis
        return AiReport(at, AiProvider.CLAUDE, basis.copy(maxC = basis.maxC + shift), today = "Hoy…", widget = widget)
    }

    @Test fun `la frase solo entra en el 4x2`() {
        val phrase = "Sol toda la tarde; mañana, nubes"
        assertEquals(phrase, widgetFrame(state, WidgetLayout.LARGE, now, context, phrase).phrase)
        assertNull(widgetFrame(state, WidgetLayout.MEDIUM, now, context, phrase).phrase)
        assertNull(widgetFrame(state, WidgetLayout.SMALL, now, context, phrase).phrase)
        assertNull(frame().phrase)
        // Sin datos tampoco, aunque la pidan.
        assertNull(widgetFrame(StoredState(null, false), WidgetLayout.LARGE, now, context, phrase).phrase)
    }

    @Test fun `un parte de hoy con frase la da, tal cual`() {
        assertEquals("Sol toda la tarde; mañana, nubes", widgetPhrase(state, reportOf(), now))
    }

    @Test fun `sin parte, sin frase, en blanco o de mas de 55 caracteres no hay frase`() {
        assertNull(widgetPhrase(state, null, now))
        assertNull(widgetPhrase(state, reportOf(widget = null), now)) // parte viejo, de antes de este encargo
        assertNull(widgetPhrase(state, reportOf(widget = ""), now))
        assertNull(widgetPhrase(state, reportOf(widget = "   "), now))
        assertNull(widgetPhrase(state, reportOf(widget = "x".repeat(WIDGET_MAX + 1)), now))
        assertEquals("x".repeat(WIDGET_MAX), widgetPhrase(state, reportOf(widget = "x".repeat(WIDGET_MAX)), now))
    }

    @Test fun `sin snapshot no hay frase`() {
        assertNull(widgetPhrase(StoredState(null, false), reportOf(), now))
    }

    @Test fun `un parte de ayer no se ve pasada la medianoche local`() {
        val late = TestData.at(2025, 6, 10, 23, 30)
        val s = StoredState(TestData.snapshot(late), false)
        val report = reportOf(late, s.snapshot!!)
        assertEquals("Sol toda la tarde; mañana, nubes", widgetPhrase(s, report, late))
        val after = TestData.at(2025, 6, 11, 0, 1)
        assertNull(widgetPhrase(s, report, after))
        val frameAfter = widgetFrame(s, WidgetLayout.LARGE, after, context, widgetPhrase(s, report, after))
        assertNull(frameAfter.phrase)
    }

    @Test fun `un parte con la prevision cambiada de verdad no se ve`() {
        assertNull(widgetPhrase(state, reportOf(shift = 3), now))
        assertEquals("Sol toda la tarde; mañana, nubes", widgetPhrase(state, reportOf(shift = 0), now))
    }

    @Test fun `datos sin horas por delante no dan frase`() {
        val old = TestData.at(2025, 6, 20, 10, 0)
        assertNull(widgetPhrase(state, reportOf(), old))
    }

    @Test fun `TalkBack lee la frase entre el cielo y el aviso, y sin frase la descripcion es la de siempre`() {
        val f = widgetFrame(state, WidgetLayout.LARGE, now, context, "Sol toda la tarde; mañana, nubes")
        assertEquals(
            "Villa Ejemplo, 12°, Nublado, máxima 18° y mínima 9°. Sol toda la tarde; mañana, nubes. Datos de las 10:20",
            widgetDescription(f, context),
        )
        assertEquals("Villa Ejemplo, 12°, Nublado, máxima 18° y mínima 9°. Datos de las 10:20", widgetDescription(frame(), context))
        val m = widgetFrame(state, WidgetLayout.MEDIUM, now, context, "Sol toda la tarde; mañana, nubes")
        assertEquals(widgetDescription(frame(layout = WidgetLayout.MEDIUM), context), widgetDescription(m, context))
    }

    @Test fun `el mediano y el pequeno llevan luna de noche como el grande`() {
        val night = TestData.at(2025, 6, 10, 23, 30)
        val s = TestData.snapshot(night).let { it.copy(current = it.current.copy(isDay = false)) }
        for (layout in WidgetLayout.entries) assertEquals(MoonShow.VEILED, frame(StoredState(s, false), layout = layout, at = night).moon)
    }
}
