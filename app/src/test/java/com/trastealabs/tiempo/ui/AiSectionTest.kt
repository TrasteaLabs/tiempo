package com.trastealabs.tiempo.ui

import androidx.compose.ui.test.performImeAction
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.ai.AiFailure
import com.trastealabs.tiempo.data.ai.AiStatus
import com.trastealabs.tiempo.data.ai.AiUsage
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 016 P3 (H13, H14, H15, H16): la tarjeta «IA» de Ajustes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1800dp")
class AiSectionTest {
    @get:Rule val rule = createComposeRule()

    private val now = TestData.at(2026, 11, 12, 10, 20)
    private val zone = TestData.zone
    private val actions = mutableListOf<AiAction>()

    private fun show(panel: AiPanel) {
        actions.clear()
        rule.setContent { WeatherTheme { Box { AiSection(panel, now, zone) { actions += it } } } }
    }

    @Test fun `los proveedores tienen 48 dp de zona (019)`() {
        show(AiPanel())
        AiProvider.values().forEach { rule.onNodeWithTag("ai_provider_${it.name}").assertTouchAtLeast48(it.name) }
    }

    @Test fun `la tecla Hecho del campo de la clave guarda como el boton (019)`() {
        show(AiPanel())
        rule.onNodeWithTag("ai_key_field").performTextInput("  clave-de-prueba-1234 ")
        rule.onNodeWithTag("ai_key_field").performImeAction()
        assertEquals(listOf<AiAction>(AiAction.SaveKey(AiProvider.CLAUDE, "clave-de-prueba-1234")), actions)
    }

    @Test fun `sin clave explica que hace y que hace falta`() {
        show(AiPanel())
        rule.onNodeWithTag("ai_intro").assertIsDisplayed()
        rule.onNodeWithTag("ai_intro").assertTextContains("clave de API", substring = true)
        rule.onNodeWithTag("ai_key_field").assertIsDisplayed()
        rule.onNodeWithTag("ai_test").assertDoesNotExist()
        rule.onNodeWithTag("ai_key_hint").assertDoesNotExist()
    }

    @Test fun `sin clave dice que es opcional y de pago con su proveedor, que sale del movil y donde sacar la clave (021)`() {
        val opened = mutableListOf<String>()
        actions.clear()
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalUriHandler provides object : androidx.compose.ui.platform.UriHandler {
                    override fun openUri(uri: String) {
                        opened += uri
                    }
                },
            ) { WeatherTheme { Box { AiSection(AiPanel(provider = AiProvider.GEMINI), now, zone) { actions += it } } } }
        }
        rule.onNodeWithTag("ai_intro").assertTextContains("Opcional", substring = true)
        rule.onNodeWithTag("ai_intro").assertTextContains("pago por uso", substring = true)
        rule.onNodeWithTag("ai_privacy").assertTextContains("sin tu ubicación ni el nombre del sitio", substring = true)
        rule.onNodeWithTag("ai_privacy").assertTextContains("cifrada y solo en este móvil", substring = true)
        rule.onNodeWithText("Dónde sacar una clave de Gemini").performClick()
        rule.onNodeWithTag("ai_key_where").assertTouchAtLeast48("dónde sacar la clave")
        assertEquals(listOf("https://aistudio.google.com/apikey"), opened)
    }

    @Test fun `con clave sigue diciendo que sale del movil y ya no ofrece sacar otra (021)`() {
        show(AiPanel(hints = mapOf(AiProvider.CLAUDE to "1234")))
        rule.onNodeWithTag("ai_privacy").assertIsDisplayed()
        rule.onNodeWithTag("ai_key_where").assertDoesNotExist()
    }

    @Test fun `con clave enseña los 4 ultimos y nunca la clave entera`() {
        show(AiPanel(hints = mapOf(AiProvider.CLAUDE to "1234")))
        rule.onNodeWithTag("ai_key_hint").assertTextContains("…1234", substring = true)
        rule.onNodeWithTag("ai_intro").assertDoesNotExist()
        rule.onNodeWithTag("ai_key_field").assertDoesNotExist()
        rule.onNodeWithText("clave-de-prueba-1234", substring = true).assertDoesNotExist()
    }

    @Test fun `el campo de la clave es de contraseña`() {
        show(AiPanel())
        rule.onNodeWithTag("ai_key_field").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test fun `guardar manda la clave escrita al proveedor elegido`() {
        show(AiPanel(provider = AiProvider.GEMINI))
        rule.onNodeWithTag("ai_key_field").performTextInput("  clave-de-prueba-1234 ")
        rule.onNodeWithTag("ai_save").performClick()
        assertEquals(listOf<AiAction>(AiAction.SaveKey(AiProvider.GEMINI, "clave-de-prueba-1234")), actions)
    }

    // 017 (H10): el campo solo se vacía cuando la clave ya está guardada (aparece su pista); si guardar falla, lo pegado sigue.
    @Test fun `si guardar falla el campo conserva el texto y se puede reintentar`() {
        show(AiPanel())
        rule.onNodeWithTag("ai_key_field").performTextInput("clave-de-prueba-1234")
        rule.onNodeWithTag("ai_save").performClick()
        rule.onNodeWithTag("ai_key_field").assertIsDisplayed()
        rule.onNodeWithTag("ai_save").performClick()
        assertEquals(
            listOf(AiAction.SaveKey(AiProvider.CLAUDE, "clave-de-prueba-1234"), AiAction.SaveKey(AiProvider.CLAUDE, "clave-de-prueba-1234")),
            actions,
        )
    }

    @Test fun `si guardar va bien sale la pista y al borrarla el campo vuelve vacio`() {
        var panel by mutableStateOf(AiPanel())
        actions.clear()
        rule.setContent { WeatherTheme { Box { AiSection(panel, now, zone) { actions += it } } } }
        rule.onNodeWithTag("ai_key_field").performTextInput("clave-de-prueba-1234")
        rule.onNodeWithTag("ai_save").performClick()
        panel = AiPanel(hints = mapOf(AiProvider.CLAUDE to "1234"))
        rule.waitForIdle()
        rule.onNodeWithTag("ai_key_field").assertDoesNotExist()
        panel = AiPanel()
        rule.waitForIdle()
        actions.clear()
        rule.onNodeWithTag("ai_save").performClick()
        assertTrue(actions.isEmpty())
    }

    @Test fun `guardar con el campo vacio no hace nada`() {
        show(AiPanel())
        rule.onNodeWithTag("ai_save").performClick()
        assertTrue(actions.isEmpty())
    }

    @Test fun `la accion de guardar no enseña la clave en su texto`() {
        assertEquals("SaveKey(CLAUDE)", AiAction.SaveKey(AiProvider.CLAUDE, "clave-de-prueba-1234").toString())
    }

    @Test fun `elegir proveedor llama a Choose`() {
        show(AiPanel())
        rule.onNodeWithTag("ai_provider_OPENAI").performClick()
        assertEquals(listOf<AiAction>(AiAction.Choose(AiProvider.OPENAI)), actions)
    }

    @Test fun `borrar llama a ClearKey y probar a Test`() {
        show(AiPanel(provider = AiProvider.CLAUDE, hints = mapOf(AiProvider.CLAUDE to "1234")))
        rule.onNodeWithTag("ai_clear").performClick()
        rule.onNodeWithTag("ai_test").performClick()
        assertEquals(listOf(AiAction.ClearKey(AiProvider.CLAUDE), AiAction.Test), actions)
    }

    @Test fun `probar ahora solo sale con clave`() {
        show(AiPanel())
        rule.onNodeWithText("Probar ahora").assertDoesNotExist()
    }

    @Test fun `probando enseña el aviso en lugar del boton`() {
        show(AiPanel(hints = mapOf(AiProvider.CLAUDE to "1234"), testing = true))
        rule.onNodeWithTag("ai_testing").assertIsDisplayed()
        rule.onNodeWithText("Probar ahora").assertDoesNotExist()
    }

    @Test fun `cada fallo sale con su texto llano`() {
        val expected = mapOf(
            AiFailure.NO_KEY to "No hay clave guardada",
            AiFailure.BAD_KEY to "La clave no es válida",
            AiFailure.NO_CREDIT to "sin saldo",
            AiFailure.RATE_LIMIT to "demasiado",
            AiFailure.OFFLINE to "No hay conexión",
            AiFailure.PROVIDER to "ha fallado",
            AiFailure.BAD_REPLY to "no era buena",
            AiFailure.TIMEOUT to "tardado demasiado",
        )
        assertEquals(AiFailure.entries.toSet(), expected.keys)
        var panel by mutableStateOf(AiPanel())
        rule.setContent { WeatherTheme { Box { AiSection(panel, now, zone) {} } } }
        for ((failure, text) in expected) {
            panel = AiPanel(status = AiStatus(now - 60, failure))
            rule.waitForIdle()
            rule.onNodeWithTag("ai_status").assertTextContains(text, substring = true)
            rule.onNodeWithTag("ai_status").assertTextContains("Último intento a las 10:19", substring = true)
        }
    }

    @Test fun `un intento bueno y el texto de la prueba salen`() {
        show(AiPanel(status = AiStatus(now - 60, null), testText = "Hoy máxima de 22 grados."))
        rule.onNodeWithTag("ai_status").assertTextContains("bien", substring = true)
        rule.onNodeWithTag("ai_test_result").assertTextContains("Hoy máxima de 22 grados.", substring = true)
    }

    @Test fun `llamadas de hoy y del mes y tokens con separador de miles`() {
        val day = "2026-11-12"
        show(AiPanel(usage = AiUsage(day, dayHere = 3, daySaved = 1, month = "2026-11", monthCalls = 1_047, monthInputTokens = 118_400, monthOutputTokens = 69_800)))
        rule.onNodeWithTag("ai_usage_today").assertTextContains("4 llamadas", substring = true)
        rule.onNodeWithTag("ai_usage_month").assertTextContains("1.047 llamadas", substring = true)
        rule.onNodeWithTag("ai_usage_month").assertTextContains("188.200 tokens", substring = true)
    }

    @Test fun `un contador de ayer sale a cero hoy`() {
        show(AiPanel(usage = AiUsage("2026-11-11", dayHere = 9, daySaved = 2, month = "2026-11", monthCalls = 30)))
        rule.onNodeWithTag("ai_usage_today").assertTextContains("0 llamadas", substring = true)
        rule.onNodeWithTag("ai_usage_month").assertTextContains("30 llamadas", substring = true)
    }

    @Test fun `el gasto de hoy suma las preguntas junto a las llamadas (018 P4, H5)`() {
        show(AiPanel(usage = AiUsage("2026-11-12", dayHere = 3, daySaved = 1, dayAsk = 3, month = "2026-11", monthCalls = 7)))
        rule.onNodeWithTag("ai_usage_today").assertTextContains("Hoy: 4 llamadas · 3 preguntas de 20")
    }

    @Test fun `con una sola pregunta, en singular`() {
        show(AiPanel(usage = AiUsage("2026-11-12", dayAsk = 1, month = "2026-11", monthCalls = 1)))
        rule.onNodeWithTag("ai_usage_today").assertTextContains("1 pregunta de 20", substring = true)
    }

    @Test fun `las preguntas de ayer salen a cero hoy`() {
        show(AiPanel(usage = AiUsage("2026-11-11", dayAsk = 17, month = "2026-11", monthCalls = 17)))
        rule.onNodeWithTag("ai_usage_today").assertTextContains("0 preguntas de 20", substring = true)
    }

}
