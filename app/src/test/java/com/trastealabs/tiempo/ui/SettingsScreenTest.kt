package com.trastealabs.tiempo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.RefreshOutcome
import com.trastealabs.tiempo.domain.ApiUsage
import com.trastealabs.tiempo.refresh.AemetOutcome
import com.trastealabs.tiempo.refresh.RefreshOrigin
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Ajustes en Cristal (010, H3): el consumo de hoy y del mes, la nota y «Acerca de» con el sello exacto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1800dp")
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val now = TestData.at(2025, 6, 10, 10, 20)
    private val zone = TestData.zone

    private fun usage(at: Long, tenthsToday: Long, tenthsMonth: Long): ApiUsage {
        val day = Instant.ofEpochSecond(at).atZone(zone).toLocalDate().toString()
        return ApiUsage(day, tenthsToday, day.take(7), tenthsMonth)
    }

    private fun show(usage: ApiUsage, at: Long = now, onBack: () -> Unit = {}) =
        rule.setContent { WeatherTheme { Box { SettingsScreen(usage, at, onBack, zone) } } }

    @Test fun `hoy y este mes salen con la cifra, llamadas, la referencia y el porcentaje`() {
        show(usage(now, 2140, 10460))
        rule.onNodeWithText("Ajustes").assertIsDisplayed()
        rule.onNodeWithText("Consumo de Open-Meteo").assertIsDisplayed()
        rule.onNodeWithText("Hoy").assertIsDisplayed()
        rule.onNodeWithText("214 llamadas").assertIsDisplayed()
        rule.onNodeWithText("de 10.000 · 2 % · vuelve a 0 a medianoche").assertIsDisplayed()
        rule.onNodeWithText("Este mes").assertIsDisplayed()
        rule.onNodeWithText("1.046 llamadas").assertIsDisplayed()
        rule.onNodeWithText("de 300.000 · 0,3 % · vuelve a 0 el día 1").assertIsDisplayed()
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h1800dp")
    fun `la cifra de llamadas sale en una sola linea con 0, 1, 9999 y 99999 a 360 dp (017 H2)`() {
        var tenths by mutableStateOf(0L)
        rule.setContent { WeatherTheme { Box { SettingsScreen(usage(now, tenths, tenths), now, {}, zone) } } }
        for ((t, shown) in listOf(0L to "0", 10L to "1", 99_990L to "9.999", 999_990L to "99.999")) {
            tenths = t
            rule.waitForIdle()
            // Hoy y este mes llevan la misma cifra: las dos tienen que estar en una línea.
            val nodes = rule.onAllNodesWithText("$shown llamadas").fetchSemanticsNodes()
            assertEquals("$shown llamadas: dos cifras", 2, nodes.size)
            for (node in nodes) {
                val results = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                assertEquals("$shown llamadas en una línea", 1, results.first().lineCount)
            }
        }
    }

    @Test fun `con cero llamadas salen dos ceros`() {
        show(usage(now, 0, 0))
        rule.onAllNodesWithText("0 llamadas").assertCountEquals(2)
        rule.onNodeWithText("de 10.000 · 0 % · vuelve a 0 a medianoche").assertIsDisplayed()
        rule.onNodeWithText("de 300.000 · 0 % · vuelve a 0 el día 1").assertIsDisplayed()
    }

    @Test fun `sale la nota de siempre`() {
        show(usage(now, 2140, 10460))
        rule.onNodeWithText("Estimación de esta app", substring = true).assertIsDisplayed()
    }

    @Test fun `Acerca de lleva el nombre, la version, la luna, las fuentes, la letra y el sello exacto`() {
        var version = ""
        val appName = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(com.trastealabs.tiempo.R.string.app_name)   // 017: «Tiempo dev» en debug
        rule.setContent {
            val context = LocalContext.current
            version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            WeatherTheme { Box { SettingsScreen(usage(now, 0, 0), now, {}, zone) } }
        }
        rule.waitForIdle()
        assertEquals(true, version.isNotEmpty())
        rule.onNodeWithText("Acerca de").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(appName).performScrollTo().assertIsDisplayed()
        assertEquals(com.trastealabs.tiempo.BuildConfig.VERSION_NAME, version)
        rule.onNodeWithText("Versión $version (${com.trastealabs.tiempo.BuildConfig.VERSION_CODE})").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Luna: NASA").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Datos del tiempo de Open-Meteo.com · CC BY 4.0").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("información elaborada utilizando, entre otras, la obtenida de la Agencia Estatal de Meteorología", substring = true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Lugares: GeoNames · CC BY 4.0").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Letra: Google Sans Flex, con licencia OFL").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Trastea Labs · por Álvaro García · Córdoba").performScrollTo().assertIsDisplayed()
    }

    @Test fun `Acerca de enlaza al codigo, la privacidad y los comentarios, y despliega las licencias`() {
        val opened = mutableListOf<String>()
        val handler = object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides handler) {
                WeatherTheme { Box { SettingsScreen(usage(now, 0, 0), now, {}, zone) } }
            }
        }
        for (tag in listOf("about_openmeteo", "about_aemet", "about_code", "about_privacy", "about_feedback", "about_email")) {
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.onNodeWithTag(tag).assertTouchAtLeast48(tag)
        }
        assertEquals(
            listOf(
                "https://open-meteo.com/",
                "https://www.aemet.es/",
                "https://github.com/trastealabs/tiempo",
                "https://github.com/trastealabs/tiempo/blob/main/PRIVACY.md",
                "https://github.com/trastealabs/tiempo/issues/new/choose",
                "mailto:hola@trastealabs.com",
            ),
            opened,
        )
        // 022: el correo se lee entero y, manteniendo pulsado, se copia.
        rule.onNodeWithText("Por correo: hola@trastealabs.com").assertExists()
        rule.onNodeWithTag("about_email").performTouchInput { longClick() }
        val clipboard = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(android.content.ClipboardManager::class.java)
        assertEquals("hola@trastealabs.com", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(6, opened.size) // mantener pulsado no abre el correo
        rule.onNodeWithText("Código abierto (GPL-3.0)").assertExists()
        rule.onAllNodesWithText("OkHttp · Apache 2.0").assertCountEquals(0)
        rule.onNodeWithTag("about_licenses").performScrollTo().performClick()
        rule.onNodeWithText("OkHttp · Apache 2.0").performScrollTo().assertIsDisplayed()
        // Lo que solo sirve para probar no se enseña.
        rule.onAllNodesWithText("Robolectric", substring = true).assertCountEquals(0)
    }

    @Test fun `la flecha llama a volver`() {
        var backs = 0
        show(usage(now, 0, 0), onBack = { backs++ })
        rule.onNode(hasContentDescription("Volver")).performClick()
        assertEquals(1, backs)
    }

    // --- 015 P2: la sección «Refrescos» (H11, H12) ---

    private fun line(
        at: Long,
        origin: RefreshOrigin = RefreshOrigin.WORKER,
        outcome: RefreshOutcome = RefreshOutcome.OK,
        aemet: AemetOutcome = AemetOutcome.UNCHANGED,
        bytes: Long = 0,
    ) = RefreshLogEntry(at, origin, outcome, aemet, bytes)

    private fun showRefreshes(refreshes: List<RefreshLogEntry>, at: Long = now, z: java.time.ZoneId = zone) =
        rule.setContent { WeatherTheme { Box { SettingsScreen(usage(at, 0, 0), at, {}, z, refreshes) } } }

    @Test fun `con tres lineas salen el ultimo, el peor hueco y las filas en orden (H11)`() {
        showRefreshes(
            listOf(
                line(TestData.at(2025, 6, 10, 6, 0), RefreshOrigin.ALARM),
                line(TestData.at(2025, 6, 10, 8, 0), RefreshOrigin.WORKER, RefreshOutcome.OFFLINE, AemetOutcome.NOT_APPLICABLE),
                line(TestData.at(2025, 6, 10, 9, 45), RefreshOrigin.WORKER, aemet = AemetOutcome.NEW, bytes = 352_256),
                line(TestData.at(2025, 6, 10, 10, 15), RefreshOrigin.APP),
            ),
        )

        rule.onNodeWithText("Refrescos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Último refresco").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("refresh_last").assertTextContains("10:15", substring = true)
        rule.onNodeWithTag("refresh_last").assertTextContains("Al abrir", substring = true)
        rule.onNodeWithText("Peor hueco en 24 h").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("El dato llegó a tener 3 h 45 min").performScrollTo().assertIsDisplayed()

        val rows = rule.onAllNodesWithTag("refresh_row")
        rows.assertCountEquals(4)
        rows[0].assertTextContains("10:15", substring = true)
        rows[0].assertTextContains("Al abrir", substring = true)
        rows[0].assertTextContains("Bien", substring = true)
        rows[0].assertTextContains("AEMET sin cambios", substring = true)
        rows[1].assertTextContains("09:45", substring = true)
        rows[1].assertTextContains("Segundo plano", substring = true)
        rows[1].assertTextContains("AEMET nuevos · 344 KB", substring = true)
        rows[2].assertTextContains("08:00", substring = true)
        rows[2].assertTextContains("Sin conexión", substring = true)
        rows[3].assertTextContains("06:00", substring = true)
        rows[3].assertTextContains("Alarma de respaldo", substring = true)
    }

    @Test fun `solo una fila sin conexion no lleva linea de AEMET y no hay peor hueco`() {
        showRefreshes(
            listOf(line(TestData.at(2025, 6, 10, 10, 0), outcome = RefreshOutcome.NO_LOCATION, aemet = AemetOutcome.NOT_APPLICABLE)),
        )
        rule.onNodeWithTag("refresh_last").assertTextContains("Sin ubicación", substring = true)
        rule.onAllNodesWithText("Peor hueco", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("AEMET", substring = true).assertCountEquals(1)   // solo la atribución de AEMET de «Acerca de»
    }

    @Test fun `sin cambios pero con KB de red enseña los KB y nuevos con 0 bytes no dice nuevos`() {
        showRefreshes(
            listOf(
                line(TestData.at(2025, 6, 10, 9, 0), aemet = AemetOutcome.UNCHANGED, bytes = 155_000),
                line(TestData.at(2025, 6, 10, 10, 0), aemet = AemetOutcome.NEW, bytes = 0),
            ),
        )
        val rows = rule.onAllNodesWithTag("refresh_row")
        rows[0].assertTextContains("AEMET sin cambios", substring = true)
        rows[1].assertTextContains("AEMET sin cambios · 151 KB", substring = true)
        rule.onAllNodesWithText("AEMET nuevos", substring = true).assertCountEquals(0)
    }

    @Test fun `una consulta que fallo a AEMET lo dice`() {
        showRefreshes(listOf(line(TestData.at(2025, 6, 10, 10, 0), aemet = AemetOutcome.FAILED)))
        rule.onAllNodesWithTag("refresh_row")[0].assertTextContains("AEMET falló", substring = true)
    }

    @Test fun `sin ninguna linea sale el aviso y ni peor hueco ni ceros raros (H12)`() {
        showRefreshes(emptyList())
        rule.onNodeWithText("Refrescos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Aún no hay refrescos registrados").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Peor hueco", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Último refresco").assertCountEquals(0)
        rule.onAllNodesWithText("0 min", substring = true).assertCountEquals(0)
        rule.onAllNodesWithTag("refresh_row").assertCountEquals(0)
    }

    @Test fun `solo salen las ultimas 24 horas y lo de ayer lleva ayer`() {
        showRefreshes(
            listOf(
                line(TestData.at(2025, 6, 8, 12, 0)),                 // de hace más de 24 h: no sale
                line(TestData.at(2025, 6, 9, 23, 50)),                // ayer
                line(TestData.at(2025, 6, 10, 10, 0)),
            ),
        )
        val rows = rule.onAllNodesWithTag("refresh_row")
        rows.assertCountEquals(2)
        rows[0].assertTextContains("10:00", substring = true)
        rows[1].assertTextContains("ayer 23:50", substring = true)
    }

    @Test fun `con el ultimo de hace mas de 24 horas la lista dice que no hay ninguno`() {
        showRefreshes(listOf(line(TestData.at(2025, 6, 8, 12, 0))))
        rule.onNodeWithTag("refresh_last").assertTextContains("08/06 12:00", substring = true)
        rule.onAllNodesWithTag("refresh_row").assertCountEquals(0)
        rule.onNodeWithText("Ningún refresco en las últimas 24 h").performScrollTo().assertIsDisplayed()
    }

    @Test fun `las horas cambian con la zona`() {
        // 10:15 en Madrid (CEST, UTC+2) son las 17:15 en Tokio (UTC+9).
        val tokyo = java.time.ZoneId.of("Asia/Tokyo")
        showRefreshes(listOf(line(TestData.at(2025, 6, 10, 10, 15))), z = tokyo)
        rule.onAllNodesWithTag("refresh_row")[0].assertTextContains("17:15", substring = true)
        rule.onNodeWithTag("refresh_last").assertTextContains("17:15", substring = true)
    }

    @Test fun `la noche del cambio de hora el hueco es el real y las horas las de la pared`() {
        val at = TestData.at(2026, 10, 25, 4, 10)
        showRefreshes(
            listOf(line(TestData.at(2026, 10, 25, 1, 30)), line(TestData.at(2026, 10, 25, 4, 0))),
            at = at,
        )
        rule.onNodeWithText("El dato llegó a tener 3 h 30 min").performScrollTo().assertIsDisplayed()
        val rows = rule.onAllNodesWithTag("refresh_row")
        rows[0].assertTextContains("04:00", substring = true)
        rows[1].assertTextContains("01:30", substring = true)
    }

    @Test fun `pasada la medianoche hoy vuelve a 0 y el mes se queda`() {
        var at by mutableStateOf(TestData.at(2025, 6, 10, 23, 55))
        val u = usage(at, 2140, 10460)
        rule.setContent { WeatherTheme { Box { SettingsScreen(u, at, {}, zone) } } }
        rule.onNodeWithText("214 llamadas").assertIsDisplayed()
        at = TestData.at(2025, 6, 11, 0, 5)
        rule.waitForIdle()
        rule.onNodeWithText("0 llamadas").assertIsDisplayed()
        rule.onNodeWithText("1.046 llamadas").assertIsDisplayed()
    }

    // ---------- 016 P3: la sección «IA» dentro de Ajustes ----------

    @Test fun `Ajustes lleva la seccion IA entre el consumo y los refrescos y reenvia sus acciones`() {
        val actions = mutableListOf<AiAction>()
        val ai = AiPanel(hints = mapOf(com.trastealabs.tiempo.domain.AiProvider.CLAUDE to "1234"))
        rule.setContent {
            WeatherTheme { Box { SettingsScreen(usage(now, 0, 0), now, {}, zone, ai = ai, onAi = { actions += it }) } }
        }
        rule.onNodeWithTag("ai_card").performScrollTo()
        rule.onNodeWithTag("ai_key_hint").assertTextContains("…1234", substring = true)
        rule.onNodeWithTag("ai_clear").performClick()
        assertEquals(listOf<AiAction>(AiAction.ClearKey(com.trastealabs.tiempo.domain.AiProvider.CLAUDE)), actions)
    }

    @Test fun `con el panel de IA por defecto no hay clave ni boton de probar`() {
        show(usage(now, 0, 0))
        rule.onNodeWithTag("ai_card").performScrollTo()
        rule.onAllNodesWithTag("ai_test").assertCountEquals(0)
        rule.onAllNodesWithTag("ai_key_hint").assertCountEquals(0)
    }
}
