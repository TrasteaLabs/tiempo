package com.trastealabs.tiempo.widget

import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.CurrentConditions
import com.trastealabs.tiempo.domain.Place
import com.trastealabs.tiempo.domain.Scene
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.domain.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetModelTest {
    private val now = TestData.at(2025, 6, 10, 10, 20)
    private val hour = 3600L
    private val snapshot = TestData.snapshot(now, currentC = 12.0)

    private fun warning(level: WarningLevel, phenomenon: String, onset: Long = now - hour, expires: Long = now + hour) =
        Warning("$level$phenomenon", level, phenomenon, "Llanura de Ejemplo", onset, expires, null, "", null, now - hour)

    private fun state(
        warnings: List<Warning> = emptyList(),
        offline: Boolean = false,
        snapshot: WeatherSnapshot = this.snapshot,
    ) = StoredState(snapshot.copy(warnings = warnings), lastAttemptFailed = offline)

    private fun WeatherSnapshot.withCurrent(t: Double, isDay: Boolean = true, code: Int = 0) =
        copy(current = CurrentConditions(t, t, code, isDay))

    // ---------- tamaño ----------

    @Test fun `los tres tamanos del OPPO (H13)`() {
        assertEquals(WidgetLayout.SMALL, widgetLayout(156f, 90f))
        assertEquals(WidgetLayout.MEDIUM, widgetLayout(328f, 90f))
        assertEquals(WidgetLayout.LARGE, widgetLayout(328f, 146f))
    }

    @Test fun `estrecho es pequeno aunque sea alto, y el umbral del grande es 130 dp`() {
        assertEquals(WidgetLayout.SMALL, widgetLayout(156f, 200f))
        assertEquals(WidgetLayout.MEDIUM, widgetLayout(396f, 117f)) // 4×1 del launcher de Pixel
        assertEquals(WidgetLayout.LARGE, widgetLayout(250f, 130f))
    }

    // ---------- etiqueta ----------

    @Test fun `aviso naranja de tormentas en los tres tamanos (H15)`() {
        val s = state(listOf(warning(WarningLevel.ORANGE, "tormentas")))
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Tormentas"), widgetBadge(s, WidgetLayout.LARGE, now, "Aviso"))
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Tormentas"), widgetBadge(s, WidgetLayout.MEDIUM, now, "Aviso"))
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Aviso"), widgetBadge(s, WidgetLayout.SMALL, now, "Aviso"))
    }

    @Test fun `temperaturas maximas entero en el grande y primera palabra en el mediano (H15)`() {
        val s = state(listOf(warning(WarningLevel.YELLOW, "temperaturas máximas")))
        assertEquals(WidgetBadge.Warning(WarningLevel.YELLOW, "Temperaturas máximas"), widgetBadge(s, WidgetLayout.LARGE, now, "Aviso"))
        assertEquals(WidgetBadge.Warning(WarningLevel.YELLOW, "Temperaturas"), widgetBadge(s, WidgetLayout.MEDIUM, now, "Aviso"))
        assertEquals(WidgetBadge.Warning(WarningLevel.YELLOW, "Aviso"), widgetBadge(s, WidgetLayout.SMALL, now, "Aviso"))
    }

    @Test fun `sin aviso, la hora del dato solo en el grande`() {
        val s = state()
        assertEquals(WidgetBadge.Updated("10:20"), widgetBadge(s, WidgetLayout.LARGE, now, "Aviso"))
        assertNull(widgetBadge(s, WidgetLayout.MEDIUM, now, "Aviso"))
        assertNull(widgetBadge(s, WidgetLayout.SMALL, now, "Aviso"))
    }

    @Test fun `sin conexion, la nube tachada con la hora del dato en los tres (H16)`() {
        val s = state(offline = true, snapshot = TestData.snapshot(now, fetchedAt = TestData.at(2025, 6, 10, 8, 15)))
        WidgetLayout.entries.forEach { assertEquals(it.name, WidgetBadge.Offline("08:15"), widgetBadge(s, it, now, "Aviso")) }
    }

    @Test fun `con aviso y sin conexion manda el aviso (H16)`() {
        val s = state(listOf(warning(WarningLevel.RED, "lluvias")), offline = true)
        assertEquals(WidgetBadge.Warning(WarningLevel.RED, "Lluvias"), widgetBadge(s, WidgetLayout.LARGE, now, "Aviso"))
        assertEquals(WidgetBadge.Warning(WarningLevel.RED, "Aviso"), widgetBadge(s, WidgetLayout.SMALL, now, "Aviso"))
    }

    @Test fun `con dos avisos sale el de activeWarning, el de mas nivel`() {
        val s = state(listOf(warning(WarningLevel.YELLOW, "vientos"), warning(WarningLevel.ORANGE, "tormentas", onset = now + 2 * hour, expires = now + 6 * hour)))
        assertEquals(WidgetBadge.Warning(WarningLevel.ORANGE, "Tormentas"), widgetBadge(s, WidgetLayout.LARGE, now, "Aviso"))
    }

    @Test fun `un aviso caducado no da etiqueta de aviso`() {
        val s = state(listOf(warning(WarningLevel.RED, "tormentas", onset = now - 5 * hour, expires = now - hour)))
        assertNull(widgetBadge(s, WidgetLayout.MEDIUM, now, "Aviso"))
        assertEquals(WidgetBadge.Updated("10:20"), widgetBadge(s, WidgetLayout.LARGE, now, "Aviso"))
    }

    @Test fun `fuera de Espana no hay avisos ni etiqueta`() {
        val abroad = TestData.snapshot(now, place = Place("Ville Exemple", "FR", TestData.place.position))
        assertNull(widgetBadge(state(snapshot = abroad), WidgetLayout.SMALL, now, "Aviso"))
        assertNull(widgetBadge(state(snapshot = abroad), WidgetLayout.MEDIUM, now, "Aviso"))
    }

    @Test fun `sin snapshot no hay etiqueta`() {
        WidgetLayout.entries.forEach { assertNull(widgetBadge(StoredState(null, lastAttemptFailed = true), it, now, "Aviso")) }
    }
}
