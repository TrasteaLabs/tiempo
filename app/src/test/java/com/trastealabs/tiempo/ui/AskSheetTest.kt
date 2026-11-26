package com.trastealabs.tiempo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AskAnswer
import com.trastealabs.tiempo.domain.AskFailure
import com.trastealabs.tiempo.domain.AskKind
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 018 P4 (H1, H4, H5, H6, H7, H8): la hoja de la pregunta, con todos sus estados. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class AskSheetTest {
    @get:Rule val rule = createComposeRule()

    private val zone = TestData.zone
    private val dataAt = TestData.at(2026, 11, 20, 10, 20)
    private val answerAt = TestData.at(2026, 11, 20, 14, 52)
    private val lastDay = LocalDate.of(2026, 11, 24)

    private var state by mutableStateOf<AskState>(AskState.Writing)
    private var providerNow by mutableStateOf(AiProvider.CLAUDE)
    private val sent = mutableListOf<String>()
    private var cancels = 0

    private fun show(
        initial: AskState = AskState.Writing,
        provider: AiProvider = AiProvider.CLAUDE,
        place: String = "Villa Ejemplo",
    ) {
        state = initial
        providerNow = provider
        sent.clear()
        cancels = 0
        rule.setContent {
            WeatherTheme {
                AskSheet(
                    place = place, provider = providerNow, state = state, dataAt = dataAt, lastDay = lastDay,
                    now = answerAt, zone = zone,
                    onSend = { sent += it }, onCancel = { cancels++ }, onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun answer(kind: AskKind = AskKind.ANSWER, text: String = "Sí, hoy secará bien: sol y 21° a las 16:00.", fetched: Long = dataAt) =
        AskAnswer(kind, text, answerAt, fetched)

    // ---------- Escribiendo ----------

    @Test fun `enviar tiene 48 dp de zona (019)`() {
        show()
        rule.onNodeWithTag("ask_send").assertTouchAtLeast48("enviar")
    }

    @Test fun `la cabecera dice el sitio, de cuando es la prevision y hasta que dia llega`() {
        show()
        rule.onNodeWithTag("ask_title").assertTextEquals("Pregunta por Villa Ejemplo")
        rule.onNodeWithTag("ask_subtitle").assertTextEquals("Con la previsión guardada de las 10:20 · hasta el 24 nov.")
    }

    @Test fun `enviar esta apagado con el campo vacio o solo con espacios`() {
        show()
        rule.onNodeWithTag("ask_send").assertIsNotEnabled()
        rule.onNodeWithTag("ask_field").performTextInput("   ")
        rule.onNodeWithTag("ask_send").assertIsNotEnabled()
        rule.onNodeWithTag("ask_send").performClick()
        assertTrue(sent.isEmpty())
    }

    @Test fun `con texto se envia recortado de espacios`() {
        show()
        rule.onNodeWithTag("ask_field").performTextInput("  ¿Puedo tender mañana? ")
        rule.onNodeWithTag("ask_send").assertIsEnabled().performClick()
        assertEquals(listOf("¿Puedo tender mañana?"), sent)
    }

    @Test fun `el contador cuenta n sobre 200`() {
        show()
        rule.onNodeWithTag("ask_counter").assertTextEquals("0/200")
        rule.onNodeWithTag("ask_field").performTextInput("¿Llueve hoy?")
        rule.onNodeWithTag("ask_counter").assertTextEquals("12/200")
    }

    @Test fun `pegar 250 caracteres deja 200 y avisa`() {
        show()
        rule.onNodeWithTag("ask_info").assertTextEquals("Lo que escribas se envía a Claude.")
        rule.onNodeWithTag("ask_field").performTextInput("a".repeat(250))
        rule.onNodeWithTag("ask_counter").assertTextEquals("200/200")
        rule.onNodeWithTag("ask_info").assertTextEquals("He cortado lo pegado a 200 caracteres.")
        // Al borrar algo, vuelve la línea de siempre.
        rule.onNodeWithTag("ask_field").performTextReplacement("a".repeat(150))
        rule.onNodeWithTag("ask_info").assertTextEquals("Lo que escribas se envía a Claude.")
    }

    @Test fun `la linea de bajo el campo lleva el nombre del proveedor elegido`() {
        show(provider = AiProvider.GEMINI)
        rule.onNodeWithTag("ask_info").assertTextEquals("Lo que escribas se envía a Gemini.")
    }

    @Test fun `con ChatGPT tambien`() {
        show(provider = AiProvider.OPENAI)
        rule.onNodeWithTag("ask_info").assertTextEquals("Lo que escribas se envía a ChatGPT.")
    }

    // ---------- Contestando ----------

    @Test fun `contestando enseña la pregunta, que esta mirando y un Cancelar que cancela`() {
        show(AskState.Asking("¿Puedo tender mañana?"))
        rule.onNodeWithTag("ask_question").assertTextEquals("¿Puedo tender mañana?")
        rule.onNodeWithTag("ask_working").assertTextEquals("Mirando la previsión de Villa Ejemplo…")
        rule.onNodeWithTag("ask_field").assertDoesNotExist()
        rule.onNodeWithTag("ask_cancel").assertIsDisplayed().performClick()
        assertEquals(1, cancels)
    }

    // ---------- Respuesta ----------

    @Test fun `la respuesta lleva su hora y no hay aviso de dato viejo si es de hace menos de 3 h`() {
        show(AskState.Answered("¿Puedo tender mañana?", answer(fetched = answerAt - 3 * 3600)))
        rule.onNodeWithTag("ask_question").assertTextEquals("¿Puedo tender mañana?")
        rule.onNodeWithTag("ask_answer").assertTextEquals("Sí, hoy secará bien: sol y 21° a las 16:00.")
        rule.onNodeWithTag("ask_answer_label").assertTextEquals("IA · 14:52")
        rule.onNodeWithTag("ask_stale").assertDoesNotExist()
    }

    @Test fun `con dato de mas de 3 h dice con datos de las HH mm`() {
        show(AskState.Answered("¿Puedo tender mañana?", answer(fetched = answerAt - 3 * 3600 - 60)))
        // 14:52 menos 3 h y 1 min = 11:51.
        rule.onNodeWithTag("ask_stale").assertTextEquals("Con datos de las 11:51")
    }

    @Test fun `Otra pregunta vacia el campo y vuelve a escribir`() {
        show()
        rule.onNodeWithTag("ask_field").performTextInput("¿Puedo tender mañana?")
        rule.onNodeWithTag("ask_send").performClick()
        state = AskState.Answered("¿Puedo tender mañana?", answer())
        rule.waitForIdle()
        rule.onNodeWithTag("ask_again").performClick()
        assertEquals(1, cancels)
        state = AskState.Writing
        rule.waitForIdle()
        rule.onNodeWithTag("ask_counter").assertTextEquals("0/200")
        rule.onNodeWithTag("ask_send").assertIsNotEnabled()
    }

    @Test fun `fuera de tema lo dice con la frase de la app y el nombre del sitio`() {
        show(AskState.Answered("¿Quién ganó la liga?", answer(AskKind.OFF_TOPIC, text = "")), place = "Córdoba")
        rule.onNodeWithTag("ask_answer").assertTextEquals("Solo sé del tiempo de Córdoba en los próximos 10 días, hasta el 24 nov.")
    }

    @Test fun `fuera de rango dice lo mismo`() {
        show(AskState.Answered("¿Y dentro de 15 días?", answer(AskKind.OUT_OF_RANGE, text = "")), place = "Córdoba")
        rule.onNodeWithTag("ask_answer").assertTextEquals("Solo sé del tiempo de Córdoba en los próximos 10 días, hasta el 24 nov.")
    }

    // ---------- Fallos ----------

    /** Enseña cada fallo, uno tras otro en la misma hoja, y devuelve la frase de cada uno. */
    private fun failureTexts(vararg failures: AskFailure, provider: AiProvider = AiProvider.CLAUDE): List<String> {
        show(AskState.Failed("¿Puedo tender mañana?", failures.first()), provider = provider)
        return failures.map { failure ->
            state = AskState.Failed("¿Puedo tender mañana?", failure)
            rule.waitForIdle()
            rule.onNodeWithTag("ask_question").assertTextEquals("¿Puedo tender mañana?")
            rule.onNodeWithTag("ask_failure").fetchSemanticsNode().config
                .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }
        }
    }

    @Test fun `cada causa tiene su frase en llano`() {
        val texts = failureTexts(
            AskFailure.UNSURE, AskFailure.OFFLINE, AskFailure.BAD_KEY, AskFailure.NO_CREDIT, AskFailure.TIMEOUT, AskFailure.CUT, AskFailure.OTHER,
        )
        assertEquals(
            listOf(
                "No he podido contestar con seguridad.",
                "No hay conexión, así que no he podido preguntar.",
                "Claude no acepta la clave. Puedes cambiarla en Ajustes → IA.",
                "La cuenta de Claude se ha quedado sin saldo.",
                "Claude ha tardado más de 20 s en contestar.",
                "La respuesta ha llegado cortada.",
                "Claude no ha podido contestar ahora.",
            ),
            texts,
        )
    }

    @Test fun `con otro proveedor las frases llevan su nombre`() {
        assertEquals(
            listOf("Gemini no acepta la clave. Puedes cambiarla en Ajustes → IA."),
            failureTexts(AskFailure.BAD_KEY, provider = AiProvider.GEMINI),
        )
        providerNow = AiProvider.OPENAI
        state = AskState.Failed("¿Puedo tender mañana?", AskFailure.NO_CREDIT)
        rule.waitForIdle()
        rule.onNodeWithTag("ask_failure").assertTextEquals("La cuenta de ChatGPT se ha quedado sin saldo.")
    }

    @Test fun `el tope y la falta de clave tambien tienen frase`() {
        assertEquals(
            listOf("Mañana más: ya van 20 preguntas hoy.", "Falta la clave de Claude. Puedes ponerla en Ajustes → IA."),
            failureTexts(AskFailure.CAP, AskFailure.NO_KEY),
        )
    }

    @Test fun `un fallo ofrece Reintentar con la misma pregunta y Cambiar la pregunta`() {
        show(AskState.Failed("¿Puedo tender mañana?", AskFailure.OFFLINE))
        rule.onNodeWithTag("ask_retry").assertIsDisplayed().performClick()
        assertEquals(listOf("¿Puedo tender mañana?"), sent)
        rule.onNodeWithTag("ask_change").assertIsDisplayed().performClick()
        assertEquals(1, cancels)
    }

    @Test fun `con el tope o sin clave no se ofrece Reintentar, solo cambiar o cerrar`() {
        show(AskState.Failed("¿Puedo tender mañana?", AskFailure.CAP))
        rule.onNodeWithTag("ask_retry").assertDoesNotExist()
        state = AskState.Failed("¿Puedo tender mañana?", AskFailure.NO_KEY)
        rule.waitForIdle()
        rule.onNodeWithTag("ask_retry").assertDoesNotExist()
        rule.onNodeWithTag("ask_change").assertIsDisplayed()
    }

    @Test fun `Cambiar la pregunta deja la pregunta en el campo`() {
        show()
        rule.onNodeWithTag("ask_field").performTextInput("¿Puedo tender mañana?")
        rule.onNodeWithTag("ask_send").performClick()
        state = AskState.Failed("¿Puedo tender mañana?", AskFailure.OFFLINE)
        rule.waitForIdle()
        rule.onNodeWithTag("ask_change").performClick()
        state = AskState.Writing
        rule.waitForIdle()
        rule.onNodeWithTag("ask_counter").assertTextEquals("21/200")
    }
}
