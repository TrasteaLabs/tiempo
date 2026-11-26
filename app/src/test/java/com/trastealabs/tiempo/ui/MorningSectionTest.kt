package com.trastealabs.tiempo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.MorningMark
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 018 P2 (H13): «Parte de la mañana» en Ajustes: apagada de salida, el permiso al encender, la hora y la última vez. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1800dp")
class MorningSectionTest {
    @get:Rule val rule = createComposeRule()

    private val zone = TestData.zone
    private val now = TestData.at(2026, 11, 16, 12, 0)

    private class FakePermission(var has: Boolean) : NotificationPermission {
        var requests = 0
        var callback: ((Boolean) -> Unit)? = null
        override fun granted() = has
        override fun request(onResult: (Boolean) -> Unit) {
            requests++
            callback = onResult
        }
    }

    private val toggles = mutableListOf<Boolean>()
    private val times = mutableListOf<Int>()

    private fun show(state: MorningPanel, permission: FakePermission = FakePermission(true)) = rule.setContent {
        WeatherTheme {
            Box { MorningSection(state, now, zone, { toggles += it }, { times += it }, permission) }
        }
    }

    @Test fun `de salida esta apagada y la fila de la hora tambien`() {
        show(MorningPanel())
        rule.onNodeWithText("Notificación cada mañana").assertIsDisplayed()
        rule.onNodeWithTag("morning_switch").assertIsOff()
        rule.onNodeWithTag("morning_time_row").assertIsNotEnabled()
        rule.onNodeWithTag("morning_time_row").assertTextContains("7:30")
    }

    @Test fun `encender sin permiso lo pide y solo enciende si lo dan`() {
        val permission = FakePermission(has = false)
        show(MorningPanel(), permission)
        rule.onNodeWithTag("morning_switch").performClick()
        assertEquals(1, permission.requests)
        assertEquals(emptyList<Boolean>(), toggles)

        rule.runOnUiThread { permission.callback!!(true) }
        rule.waitForIdle()
        assertEquals(listOf(true), toggles)
    }

    @Test fun `con el permiso negado el interruptor queda apagado y sale la ayuda`() {
        val permission = FakePermission(has = false)
        show(MorningPanel(), permission)
        rule.onNodeWithTag("morning_switch").performClick()
        rule.runOnUiThread { permission.callback!!(false) }
        rule.waitForIdle()

        assertEquals(emptyList<Boolean>(), toggles)
        rule.onNodeWithTag("morning_switch").assertIsOff()
        rule.onNodeWithTag("morning_denied").assertIsDisplayed()
        rule.onNodeWithText("Sin permiso para avisarte", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Aplicaciones → Tiempo → Notificaciones", substring = true).assertIsDisplayed()
    }

    @Test fun `con el permiso ya dado enciende sin preguntar`() {
        val permission = FakePermission(has = true)
        show(MorningPanel(), permission)
        rule.onNodeWithTag("morning_switch").performClick()
        assertEquals(0, permission.requests)
        assertEquals(listOf(true), toggles)
    }

    @Test fun `encendida el interruptor esta encendido, la hora se puede tocar y apagar avisa`() {
        show(MorningPanel(enabled = true, minuteOfDay = 8 * 60 + 5))
        rule.onNodeWithTag("morning_switch").assertIsOn()
        rule.onNodeWithTag("morning_time_row").assertIsEnabled()
        rule.onNodeWithTag("morning_time_row").assertTextContains("8:05")
        rule.onNodeWithTag("morning_switch").performClick()
        assertEquals(listOf(false), toggles)
    }

    @Test fun `un permiso quitado desde el sistema apaga el interruptor y enseña la ayuda`() {
        show(MorningPanel(enabled = true, denied = true))
        rule.onNodeWithTag("morning_switch").assertIsOff()
        rule.onNodeWithTag("morning_time_row").assertIsNotEnabled()
        rule.onNodeWithTag("morning_denied").assertIsDisplayed()
    }

    @Test fun `la ultima vez sale con el dia, la hora real y con que texto`() {
        val at = TestData.at(2026, 11, 16, 7, 34)
        show(MorningPanel(enabled = true, last = MorningMark(TestData.at(2026, 11, 16, 7, 30), withAi = true), lastAt = at))
        rule.onNodeWithTag("morning_last").assertTextContains("Última: hoy a las 7:34, con IA")
    }

    @Test fun `la ultima vez de ayer con reglas, y apagada no sale`() {
        val at = TestData.at(2026, 11, 15, 7, 41)
        show(MorningPanel(enabled = true, last = MorningMark(TestData.at(2026, 11, 15, 7, 30), withAi = false), lastAt = at))
        rule.onNodeWithTag("morning_last").assertTextContains("Última: ayer a las 7:41, con reglas")
    }

    @Test fun `apagada no enseña la ultima vez`() {
        show(MorningPanel(enabled = false, last = MorningMark(1, true), lastAt = now - 3600))
        rule.onAllNodesWithTag("morning_last").assertCountEquals(0)
    }

    @Test fun `encendida sin permiso, si se niega al tocar, se apaga del todo`() {
        val permission = FakePermission(has = false)
        show(MorningPanel(enabled = true, denied = true), permission)
        rule.onNodeWithTag("morning_switch").performClick()
        rule.runOnUiThread { permission.callback!!(false) }
        rule.waitForIdle()
        assertEquals(listOf(false), toggles)
    }

    @Test fun `el panel sale de los ajustes, del permiso y de la ultima linea de aviso del registro`() {
        val refresh = com.trastealabs.tiempo.data.RefreshLogEntry(
            now - 60, com.trastealabs.tiempo.refresh.RefreshOrigin.WORKER, com.trastealabs.tiempo.data.RefreshOutcome.OK,
            com.trastealabs.tiempo.refresh.AemetOutcome.UNCHANGED,
        )
        fun notice(at: Long, ai: Boolean) = refresh.copy(
            at = at, origin = com.trastealabs.tiempo.refresh.RefreshOrigin.MORNING, morning = MorningMark(at - 240, ai),
        )
        val entries = listOf(notice(now - 90_000, true), notice(now - 3_600, false), refresh)

        val panel = morningPanelOf(true, 450, true, entries)
        assertEquals(MorningPanel(true, 450, false, MorningMark(now - 3_600 - 240, false), now - 3_600), panel)
        assertEquals(true, morningPanelOf(true, 450, false, entries).denied)
        assertEquals(false, morningPanelOf(false, 450, false, entries).denied)
        assertEquals(null, morningPanelOf(true, 450, true, listOf(refresh)).last)
    }

    @Test fun `tocar la hora abre el selector y Poner hora devuelve la hora puesta`() {
        show(MorningPanel(enabled = true, minuteOfDay = 7 * 60 + 30))
        rule.onNodeWithTag("morning_time_row").performClick()
        rule.onNodeWithText("Poner hora").assertIsDisplayed()
        rule.onNodeWithText("Dejar como está").assertIsDisplayed()
        rule.onNodeWithText("Poner hora").performClick()
        assertEquals(listOf(7 * 60 + 30), times)
    }
}
