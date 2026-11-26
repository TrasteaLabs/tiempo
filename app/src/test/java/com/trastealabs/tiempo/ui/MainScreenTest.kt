package com.trastealabs.tiempo.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.click
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.LOADING_PHOTO
import com.trastealabs.tiempo.domain.SkyEffects
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.skyPhotoNow
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import com.trastealabs.tiempo.domain.AirHour
import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.ApiUsage
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.DayReliability
import com.trastealabs.tiempo.domain.ModelDay
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.ReliabilityLevel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trastealabs.tiempo.domain.WeatherModel
import com.trastealabs.tiempo.domain.StoredState
import kotlinx.serialization.json.Json
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import androidx.compose.ui.test.onAllNodesWithText
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.Scene
import com.trastealabs.tiempo.domain.Sky
import org.junit.Assert.assertTrue
import com.trastealabs.tiempo.ui.scene.SkyClock
import com.trastealabs.tiempo.ui.scene.MotionSettings
import androidx.compose.ui.test.swipeUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreenTest {
    @get:Rule val rule = createComposeRule()

    private val now = TestData.at(2025, 6, 10, 10, 20)

    private fun content(
        fetchedAt: Long = now,
        offline: Boolean = false,
        refreshing: Boolean = false,
        yesterdayTempC: Double? = 15.0,
        warnings: List<Warning> = emptyList(),
    ) = UiState.Content(
        snapshot = TestData.snapshot(now, currentC = 12.0, yesterdayTempC = yesterdayTempC, fetchedAt = fetchedAt)
            .copy(warnings = warnings),
        now = now,
        offline = offline,
        refreshing = refreshing,
    )

    private fun show(
        state: UiState,
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
        onRequestPermission: () -> Unit = {},
    ) = rule.setContent { MainScreen(state, onRefresh, onRetry, onRequestPermission, phoneZone = TestData.zone) }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `la principal pinta la capa de luz encima de la foto (009, P2)`() {
        var calls = 0
        com.trastealabs.tiempo.ui.scene.LightLayer.drawProbe = { calls++ }
        try {
            // Un reloj que no corre: Robolectric solo pinta al pedir una imagen (como en DayScreenTest).
            var view: android.view.View? = null
            rule.setContent {
                view = androidx.compose.ui.platform.LocalView.current
                MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, skyClock = SkyClock())
            }
            rule.runOnIdle {
                val root = checkNotNull(view).rootView
                root.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)))
            }
            assertTrue("la principal no pinta la capa de luz", calls > 0)
        } finally {
            com.trastealabs.tiempo.ui.scene.LightLayer.drawProbe = null
        }
    }

    /** La misma cifra puede salir en las horas o los días; aquí solo interesa la de la cabecera. */
    private fun headerNode(text: String) =
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("main_panel")), useUnmergedTree = true)

    @Test fun `con datos guardados la cabecera se ve completa sin pantalla de carga (H1)`() {
        show(content())
        rule.onNodeWithText("Villa Ejemplo").assertIsDisplayed()
        headerNode("12°").assertIsDisplayed()
        // 009 (H11): la línea de datos de la cabecera sin tarjeta junta máxima, mínima y sensación.
        rule.onNodeWithText("Máx 18° · Mín 9° · Sensación 10°").assertIsDisplayed()
        rule.onNodeWithText("3 °C más frío que ayer a esta hora").assertIsDisplayed()
        rule.onNodeWithText("Martes, 10 de junio · 10:20").assertIsDisplayed()
        rule.onNodeWithText("Buscando dónde estás…").assertDoesNotExist()
    }

    // ---------- 012 P9 · H12: la cifra que viaja ----------

    private val tempNode = SemanticsMatcher("la temperatura de la cabecera") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.firstOrNull()?.text?.matches(Regex("-?[0-9]+°")) == true
    } and hasAnyAncestor(hasTestTag("main_panel"))

    private fun headerTemp(): String = texts(rule.onNode(tempNode, useUnmergedTree = true).fetchSemanticsNode())

    private fun showTraveling(state: UiState, last: Int?, intro: IntroState?) =
        rule.setContent { MainScreen(state, {}, {}, {}, phoneZone = TestData.zone, intro = intro, lastSeenTemp = last) }

    @Test fun `al abrir en frio la cifra parte de la ultima vista y llega a la de ahora (H12)`() {
        val intro = IntroState()
        showTraveling(content(), last = 5, intro = intro)
        assertEquals("5°", headerTemp())
        rule.runOnIdle { intro.advance(300) }
        rule.waitForIdle()
        val mid = headerTemp().removeSuffix("°").toInt()
        assertTrue("a los 300 ms va de camino: $mid", mid in 6..11)
        rule.runOnIdle { intro.advance(TRAVEL_MS) }
        rule.waitForIdle()
        assertEquals("12°", headerTemp())
    }

    @Test fun `sin entrada en marcha (volver de segundo plano, sin animaciones) la cifra es la de ahora, quieta (H12)`() {
        showTraveling(content(), last = 5, intro = null)
        assertEquals("12°", headerTemp())
    }

    @Test fun `con la cifra anterior igual no se mueve (H12)`() {
        val intro = IntroState()
        showTraveling(content(), last = 12, intro = intro)
        assertEquals("12°", headerTemp())
        rule.runOnIdle { intro.advance(300) }
        rule.waitForIdle()
        assertEquals("12°", headerTemp())
    }

    @Test fun `sin cifra anterior entra con la de ahora, sin contar (H12)`() {
        val intro = IntroState()
        showTraveling(content(), last = null, intro = intro)
        assertEquals("12°", headerTemp())
        rule.runOnIdle { intro.advance(300) }
        rule.waitForIdle()
        assertEquals("12°", headerTemp())
    }

    @Test fun `un sitio guardado no cuenta aunque haya entrada (la ultima vista es de donde estoy)`() {
        val intro = IntroState()
        showTraveling(content().copy(place = PlaceRef.Saved("p1"), savedName = "Otro sitio"), last = 5, intro = intro)
        assertEquals("12°", headerTemp())
    }

    @Test fun `la cifra de la cabecera usa cifras tabulares (tnum)`() {
        showTraveling(content(), last = null, intro = null)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNode(tempNode, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals("tnum", layouts.single().layoutInput.style.fontFeatureSettings)
    }

    // ---------- 019 bloque 4: lector y zona táctil ----------

    private fun panelNode() = rule.onNodeWithTag("main_panel").fetchSemanticsNode()

    private fun assertSpokenOnce(grados: String, suelta: List<String>) {
        val node = panelNode()
        val said = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        val shown = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
        assertEquals("«$grados» una vez: $said", 1, said.count { it == grados })
        suelta.forEach { assertFalse("«$it» no se lee suelta: $shown", it in shown) }
    }

    @Test fun `la cabecera dice N grados una vez y no la cifra suelta (019)`() {
        showTraveling(content(), last = null, intro = null)
        assertSpokenOnce("12 grados", listOf("12°", "12"))
    }

    @Test fun `mientras la cifra viaja el lector solo oye el valor final (019)`() {
        val intro = IntroState()
        showTraveling(content(), last = 5, intro = intro)
        rule.runOnIdle { intro.advance(300) }
        rule.waitForIdle()
        assertSpokenOnce("12 grados", listOf("12°", "5°", headerTemp()))
    }

    @Test fun `la pildora Ajustes es un boton que dice Ajustes y tiene 48 dp (019)`() {
        show(content())
        val pill = rule.onNodeWithText("Ajustes").performScrollTo()
        val node = pill.fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.Role.Button, node.config[SemanticsProperties.Role])
        assertEquals(listOf("Ajustes"), node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text })
        pill.assertTouchAtLeast48("pastilla Ajustes")
    }

    @Test fun `las filas de dia tienen 48 dp de zona (019)`() {
        show(content())
        rule.onAllNodesWithTag("day")[0].performScrollTo().assertTouchAtLeast48("fila de día")
    }

    @Test fun `sin dato de ayer no sale la frase (H2)`() {
        show(content(yesterdayTempC = null))
        rule.onNodeWithText("Villa Ejemplo").assertIsDisplayed()
        rule.onNodeWithText("3 °C más frío que ayer a esta hora").assertDoesNotExist()
        rule.onNodeWithText("Igual que ayer a esta hora").assertDoesNotExist()
    }

    @Test fun `sin conexion se ve lo guardado con la antiguedad del dato (H13)`() {
        show(content(fetchedAt = TestData.at(2025, 6, 10, 8, 15), offline = true))
        rule.onNodeWithText("Sin conexión · datos de las 08:15").assertIsDisplayed()
        // La píldora va en lugar de la fecha (H7 del 002).
        rule.onNodeWithText("Martes, 10 de junio", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Villa Ejemplo").assertIsDisplayed()
        headerNode("12°").assertIsDisplayed()
    }

    @Test fun `con conexion no sale la franja de sin conexion`() {
        show(content())
        rule.onNodeWithText("Sin conexión", substring = true).assertDoesNotExist()
    }

    @Test fun `mientras actualiza se ve Actualizando`() {
        show(content(refreshing = true))
        rule.onNodeWithText("Actualizando…").assertIsDisplayed()
        rule.onNodeWithText("Villa Ejemplo").assertIsDisplayed()
    }

    @Test fun `deslizar hacia abajo llama a refresh (H5)`() {
        var refreshes = 0
        show(content(), onRefresh = { refreshes++ })
        rule.onNodeWithTag("main_scroll").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(1, refreshes)
    }

    private fun texts(node: SemanticsNode): String =
        (node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }).joinToString(" ")

    @Test fun `se ven 24 horas con temperatura, probabilidad y cantidad de lluvia (H3)`() {
        val base = content()
        val rainyHour = TestData.at(2025, 6, 10, 15, 0)
        val hourly = base.snapshot.hourly.map {
            when (it.time) {
                rainyHour -> it.copy(precipProbability = 70, precipMm = 1.2)
                TestData.at(2025, 6, 10, 11, 0) -> it.copy(precipProbability = 0, precipMm = 0.0)
                TestData.at(2025, 6, 10, 12, 0) -> it.copy(precipProbability = 19, precipMm = 0.0)
                else -> it
            }
        }
        show(base.copy(snapshot = base.snapshot.copy(hourly = hourly)))

        val hours = rule.onAllNodesWithTag("hour").fetchSemanticsNodes()
        assertEquals(24, hours.size)
        assertTrue(texts(hours[1]), "lluvia" !in texts(hours[1]) && "lluvia" !in texts(hours[2]))
        // "Ahora" es la temperatura de la cabecera (12°); las demás, la previsión horaria (10°). TalkBack lee cada
        // columna una vez: hora, grados, cielo y, si llega al 20 %, la lluvia (019).
        assertTrue(texts(hours.first()), texts(hours.first()).startsWith("Ahora, 12 grados"))
        hours.drop(1).forEach { node ->
            val t = texts(node)
            assertTrue(t, t.contains("10 grados"))
        }
        // 10:00 es "Ahora", así que las 15:00 son la sexta columna.
        assertTrue(texts(hours[5]), texts(hours[5]).startsWith("15:00, 10 grados") && texts(hours[5]).contains("70 % de lluvia, 1,2 mm"))
        // Solo llevan lluvia las horas del 20 % en adelante; ningún «0%» ni frase de «Sin lluvia».
        val shown = hourly.filter { it.time >= TestData.at(2025, 6, 10, 10, 0) }.take(24).count { (it.precipProbability ?: 0) >= 20 }
        assertEquals(shown, hours.count { "% de lluvia" in texts(it) })
        assertTrue(shown < 24)
        rule.onAllNodesWithText("0%", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("hours_dry").assertDoesNotExist()
    }

    @Test fun `sin ninguna hora al 20 por ciento las horas dicen Sin lluvia una vez y ningun 0 (019)`() {
        val base = content()
        val hourly = base.snapshot.hourly.map { it.copy(precipProbability = if (it.precipProbability == null) null else 10, precipMm = 0.0) }
        show(base.copy(snapshot = base.snapshot.copy(hourly = hourly)))
        rule.onNodeWithTag("hours_dry").assertTextEquals("Sin lluvia en las próximas 24 h")
        rule.onAllNodesWithText("Sin lluvia en las próximas 24 h").assertCountEquals(1)
        rule.onAllNodesWithText("10%", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("hour").fetchSemanticsNodes().forEach { assertTrue(texts(it), "lluvia" !in texts(it)) }
    }

    @Test fun `con datos de otra hora, Ahora se queda con la prevision horaria`() {
        show(content(fetchedAt = TestData.at(2025, 6, 10, 8, 15), offline = true))
        val first = rule.onAllNodesWithTag("hour").fetchSemanticsNodes().first()
        assertTrue(texts(first), texts(first).startsWith("Ahora, 10 grados"))
    }

    @Test fun `tocar una fila abre la ventana del dia sobre la principal y cerrar la quita (009, H13)`() {
        show(content())
        rule.onAllNodesWithTag("day")[2].performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("day_window").assertExists()
        // La principal sigue compuesta debajo (no se cambia de pantalla).
        rule.onNodeWithTag("main_scroll").assertExists()
        clickScrim(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("day_window").assertDoesNotExist()
        rule.onNodeWithTag("main_scroll").assertExists()
    }

    @Test fun `con la ventana del dia abierta lo de detras se oculta a TalkBack y la ventana lleva titulo de panel (011, P2 pendiente)`() {
        show(content())
        fun hidden(): Boolean {
            var n: androidx.compose.ui.semantics.SemanticsNode? = rule.onNodeWithTag("main_scroll").fetchSemanticsNode()
            while (n != null) {
                if (androidx.compose.ui.semantics.SemanticsProperties.HideFromAccessibility in n.config) return true
                n = n.parent
            }
            return false
        }
        assertFalse(hidden())
        rule.onAllNodesWithTag("day")[2].performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(hidden())
        assertEquals(
            "Detalle del día",
            rule.onNodeWithTag("day_window").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.PaneTitle],
        )
        clickScrim(rule)
        rule.waitForIdle()
        assertFalse(hidden())
    }

    @Test fun `con una hoja de horas o de dias abierta lo de detras tambien se oculta a TalkBack (011, P2 pendiente)`() {
        fun hiddenWith(content: @androidx.compose.runtime.Composable () -> Unit): Boolean {
            rule.setContent(content)
            rule.waitForIdle()
            var n: androidx.compose.ui.semantics.SemanticsNode? = rule.onNodeWithTag("main_scroll").fetchSemanticsNode()
            while (n != null) {
                if (androidx.compose.ui.semantics.SemanticsProperties.HideFromAccessibility in n.config) return true
                n = n.parent
            }
            return false
        }
        assertTrue(hiddenWith { MainScreen(withHourlyData(), {}, {}, {}, initialHourlySheet = HourlySheetKind.WIND) })
    }

    @Test fun `se ven 10 dias con icono, maxima, minima, probabilidad y cantidad (H4)`() {
        show(content())
        val days = rule.onAllNodesWithTag("day").fetchSemanticsNodes()
        assertEquals(10, days.size)
        assertTrue(texts(days.first()).startsWith("Hoy"))
        days.forEachIndexed { i, node ->
            val t = texts(node)
            assertTrue(t, t.contains("${18 + i}°") && t.contains("${9 + i}°"))
            assertTrue(t, t.contains("30%") && t.contains("1,5 mm"))
            assertTrue(t, t.contains("Nublado"))   // icono con su descripción (código 3)
        }
    }

    @Test fun `la primera vez muestra Buscando donde estas`() {
        show(UiState.Searching)
        rule.onNodeWithText("Buscando dónde estás…").assertIsDisplayed()
    }

    // ---------- 020: la espera con la forma de lo que viene ----------

    @Test fun `buscando pinta el esqueleto de la principal con su frase dentro`() {
        show(UiState.Searching)
        rule.onNodeWithTag("main_skeleton").assertIsDisplayed()
        rule.onNode(hasTestTag("main_skeleton_text") and hasText("Buscando dónde estás…")).assertIsDisplayed()
    }

    @Test fun `descargando un guardado pinta el esqueleto con el boton del sitio encima`() {
        showPlaces(UiState.SavedDownloading(granada))
        rule.onNodeWithTag("main_skeleton").assertIsDisplayed()
        rule.onNode(hasTestTag("main_skeleton_text") and hasText("Descargando el tiempo de Granada…")).assertIsDisplayed()
    }

    @Test fun `leyendo lo guardado no se pinta nada hasta los 400 ms y despues el esqueleto`() {
        rule.mainClock.autoAdvance = false
        show(UiState.Loading)
        rule.mainClock.advanceTimeBy(LOADING_SKELETON_DELAY_MS - 100)
        rule.onNodeWithTag("main_skeleton").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("main_skeleton").assertExists()
        rule.onNodeWithTag("main_skeleton_text").assertDoesNotExist()
    }

    @Test fun `sin datos y sin red se explica y se puede reintentar`() {
        var retries = 0
        show(UiState.NoConnection, onRetry = { retries++ })
        rule.onNodeWithText("Sin conexión").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test fun `sin permiso se explica y se puede darlo`() {
        var asked = 0
        show(UiState.NeedsPermission, onRequestPermission = { asked++ })
        rule.onNodeWithText("Necesito saber dónde estás").assertIsDisplayed()
        rule.onNodeWithText("Dar permiso").performClick()
        assertEquals(1, asked)
    }

    @Test fun `sin senal de ubicacion se puede reintentar`() {
        var retries = 0
        show(UiState.NoLocation, onRetry = { retries++ })
        rule.onNodeWithText("No encuentro tu ubicación").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test fun `el consejo de bateria sale con datos y pide ubicacion todo el tiempo y segundo plano`() {
        var settings = 0
        rule.setContent {
            MainScreen(content(), {}, {}, {}, showBatteryTip = true, onBatteryTipSettings = { settings++ })
        }
        rule.onNodeWithText("Para que el widget no se congele").assertIsDisplayed()
        rule.onNodeWithText("Ubicación: «Permitir todo el tiempo»").assertIsDisplayed()
        rule.onNodeWithText("Activa «Actividad en segundo plano» e «Inicio automático».").assertIsDisplayed()
        rule.onNodeWithText("Abrir ajustes").performClick()
        // Sin animaciones (aquí no hay reloj del cielo) el diálogo funde 150 ms antes de avisar (011, H12).
        rule.waitForIdle()
        assertEquals(1, settings)
    }

    @Test fun `ahora no cierra el consejo de bateria`() {
        var dismissed = 0
        rule.setContent {
            MainScreen(content(), {}, {}, {}, showBatteryTip = true, onBatteryTipDismiss = { dismissed++ })
        }
        rule.onNodeWithText("Ahora no").performClick()
        // Sin animaciones el diálogo funde 150 ms antes de avisar (011, H12).
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test fun `sin datos todavia no sale el consejo de bateria`() {
        rule.setContent { MainScreen(UiState.Searching, {}, {}, {}, showBatteryTip = true) }
        rule.onNodeWithText("Para que el widget no se congele").assertDoesNotExist()
    }

    private val storm = Warning(
        id = "x",
        level = WarningLevel.ORANGE,
        phenomenon = "tormentas",
        areaName = "Llanura de Ejemplo",
        onset = TestData.at(2025, 6, 10, 16),
        expires = TestData.at(2025, 6, 10, 22),
        probability = "40%-70%",
        description = "Tormentas con granizo y rachas muy fuertes.",
        instruction = "Evite zonas inundables.",
        sent = TestData.at(2025, 6, 10, 9, 30),
    )

    @Test fun `con aviso sale la franja del color del nivel con fenomeno y horario (H14)`() {
        show(content(warnings = listOf(storm)))
        rule.onNodeWithTag("warning_banner")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(WarningLevelKey, WarningLevel.ORANGE))
        rule.onNodeWithText("Aviso naranja · tormentas").assertIsDisplayed()
        rule.onNodeWithText("hoy 16:00–22:00 · Llanura de Ejemplo").assertIsDisplayed()   // 009: hora y zona
        assertEquals(Color(0xFFF28C28), warningColors(WarningLevel.ORANGE).first)
        assertEquals(Color(0xFFF4C20D), warningColors(WarningLevel.YELLOW).first)
        assertEquals(Color(0xFFD93025), warningColors(WarningLevel.RED).first)
        assertEquals(Color(0xFF2A1804), warningColors(WarningLevel.RED).second)   // 002: el mismo texto en los tres niveles
    }

    @Test fun `sin aviso no hay franja`() {
        show(content())
        rule.onNodeWithTag("warning_banner").assertDoesNotExist()
        rule.onNodeWithText("Aviso ", substring = true).assertDoesNotExist()   // el pie dice "Avisos: AEMET"
    }

    @Test fun `un aviso caducado no saca franja`() {
        show(content(warnings = listOf(storm.copy(onset = TestData.at(2025, 6, 9, 10), expires = TestData.at(2025, 6, 9, 20)))))
        rule.onNodeWithTag("warning_banner").assertDoesNotExist()
    }

    @Test fun `tocar la franja abre la hoja con el texto completo (H15)`() {
        show(content(warnings = listOf(storm)))
        rule.onNodeWithTag("warning_banner").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("warning_sheet").assertIsDisplayed()
        rule.onNodeWithText("Llanura de Ejemplo").assertIsDisplayed()
        rule.onNodeWithText("martes 10 jun, de 16:00 a 22:00").assertIsDisplayed()
        rule.onNodeWithText("40%-70%").assertIsDisplayed()
        rule.onNodeWithText("Tormentas con granizo y rachas muy fuertes.").assertIsDisplayed()
        // La hoja se desliza si no cabe (la ventana de Robolectric es pequeña).
        rule.onNodeWithText("Evite zonas inundables.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("AEMET · emitido hoy a las 09:30").performScrollTo().assertIsDisplayed()

        // 011, P2: la hoja se cierra con el velo (012, P5: ya no lleva cruz).
        clickScrim(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("warning_sheet").assertDoesNotExist()
    }

    // ---------- 002 · pantalla de cristal ----------

    private fun top(text: String, substring: Boolean = false): Float =
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes()
            .also { check(it.isNotEmpty()) { "no está «$text»" } }.first().positionInRoot.y

    @Test fun `la pantalla sigue el orden de la pantalla D (009, H11)`() {
        show(content(warnings = listOf(storm)))
        val sky = rule.onAllNodesWithText("Nublado", useUnmergedTree = true).fetchSemanticsNodes().first().positionInRoot.y
        // Cabecera, Resumen (aviso y frases), 24 h, 10 días y pie (las fichas, en el test de H10 a H12); los títulos tal cual, sin mayúsculas.
        val order = listOf(
            top("Villa Ejemplo"),
            top("Martes, 10 de junio · 10:20"),
            headerTop("12°"),
            sky,
            top("Máx 18° · Mín 9° · Sensación 10°"),
            top("3 °C más frío que ayer a esta hora"),
            top("Resumen"),
            top("Aviso naranja · tormentas"),
            top("Lluvia mañana"),
            top("Próximas 24 horas"),
            top("Próximos 10 días"),
            top("Datos del tiempo de Open-Meteo.com", substring = true),
            top("Ajustes"),
        )
        assertEquals(order.sorted(), order)
        rule.onNodeWithText("PRÓXIMAS 24 HORAS").assertDoesNotExist()
        // Centrada: el sitio, la temperatura y el cielo, en el medio de la pantalla.
        val width = rule.onRoot().fetchSemanticsNode().size.width.toFloat()
        listOf(headerNode("12°"), rule.onNodeWithText("Villa Ejemplo"), rule.onAllNodesWithText("Nublado", useUnmergedTree = true)[0]).forEach {
            val node = it.fetchSemanticsNode()
            assertEquals(width / 2f, node.positionInRoot.x + node.size.width / 2f, width * 0.06f)
        }
    }

    // ---------- 009 · Resumen (H12) ----------

    private fun storm(id: String, level: WarningLevel, phenomenon: String, onsetH: Int) =
        storm.copy(id = id, level = level, phenomenon = phenomenon, onset = TestData.at(2025, 6, 10, onsetH), expires = TestData.at(2025, 6, 10, 23))

    @Test fun `con tres avisos salen tres franjas, el mas grave arriba, y cada una abre la suya`() {
        show(content(warnings = listOf(storm("y", WarningLevel.YELLOW, "viento", 9), storm("r", WarningLevel.RED, "lluvias", 18), storm("o", WarningLevel.ORANGE, "tormentas", 12))))
        val banners = rule.onAllNodesWithTag("warning_banner").fetchSemanticsNodes()
        assertEquals(3, banners.size)
        assertEquals(listOf(WarningLevel.RED, WarningLevel.ORANGE, WarningLevel.YELLOW), banners.sortedBy { it.positionInRoot.y }.map { it.config[WarningLevelKey] })
        // La segunda (naranja) abre la hoja de la naranja.
        val second = banners.indexOf(banners.sortedBy { it.positionInRoot.y }[1])
        rule.onAllNodesWithTag("warning_banner")[second].performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("warning_sheet").assertIsDisplayed()
        rule.onNodeWithText("Aviso naranja").assertIsDisplayed()
        rule.onNodeWithText("Tormentas").assertIsDisplayed()
        rule.onNodeWithText("Aviso rojo").assertDoesNotExist()
    }

    @Test fun `sin avisos el Resumen solo lleva frases`() {
        show(content())
        rule.onNodeWithTag("summary_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Resumen").assertIsDisplayed()
        rule.onNodeWithTag("warning_banner").assertDoesNotExist()
        // TestData: sin lluvia por horas y 1,5 mm cada día: la regla de «más adelante».
        rule.onNodeWithText("Lluvia mañana").assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithTag("summary_line").fetchSemanticsNodes().size)
    }

    // ---------- 016 P4 · el parte de la IA ----------

    private fun reportOf(
        today: String? = "Hoy entre 9° a las 7:00 y 21° hacia las 16:00. Sin lluvia.",
        warnings: Map<String, String> = emptyMap(),
    ) = com.trastealabs.tiempo.domain.AiReport(
        generatedAt = TestData.at(2025, 6, 10, 8, 15), provider = com.trastealabs.tiempo.domain.AiProvider.CLAUDE,
        basis = com.trastealabs.tiempo.domain.ReportBasis("2025-06-10", 20, 10, false), today = today, warnings = warnings,
    )

    @Test fun `con parte sale el texto de la IA con su marca y no las frases de reglas (H1, H9)`() {
        show(content().copy(report = reportOf()))
        rule.onNodeWithTag("summary_report").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Hoy entre 9° a las 7:00 y 21° hacia las 16:00. Sin lluvia.").assertIsDisplayed()
        rule.onNodeWithTag("summary_report_label").assertIsDisplayed()
        rule.onNodeWithText("IA · 08:15").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("summary_line").fetchSemanticsNodes().size)
    }

    @Test fun `con parte las franjas de aviso siguen encima`() {
        show(content(warnings = listOf(storm)).copy(report = reportOf()))
        val banner = rule.onNodeWithTag("warning_banner").performScrollTo().fetchSemanticsNode().positionInRoot.y
        val report = rule.onNodeWithTag("summary_report").fetchSemanticsNode().positionInRoot.y
        assertTrue(banner < report)
    }

    @Test fun `sin parte o sin el de hoy salen las frases de reglas y sin marca (H6)`() {
        show(content().copy(report = reportOf(today = null)))
        rule.onNodeWithText("Lluvia mañana").assertIsDisplayed()
        rule.onNodeWithTag("summary_report").assertDoesNotExist()
        rule.onNodeWithTag("summary_report_label").assertDoesNotExist()
    }

    @Test fun `sin parte salen las frases de reglas (H6)`() {
        show(content())
        rule.onNodeWithText("Lluvia mañana").assertIsDisplayed()
        rule.onNodeWithTag("summary_report").assertDoesNotExist()
    }

    @Test fun `con parte pero sin frases de reglas la tarjeta sale`() {
        // Sin horas por delante ya no hay nada que resumir con reglas: solo queda el parte.
        val base = content()
        val old = base.copy(snapshot = base.snapshot.copy(hourly = emptyList()))
        assertTrue(com.trastealabs.tiempo.domain.summaryLines(old.snapshot, old.now, TestData.zone).isEmpty())
        show(old.copy(report = reportOf()))
        rule.onNodeWithTag("summary_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("summary_report").assertIsDisplayed()
    }

    @Test fun `la hoja de un aviso con explicacion la enseña encima del texto oficial (H3)`() {
        show(content(warnings = listOf(storm)).copy(report = reportOf(warnings = mapOf("x" to "Puede haber tormentas fuertes esta tarde."))))
        rule.onNodeWithTag("warning_banner").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("warning_sheet").assertIsDisplayed()
        rule.onNodeWithTag("warning_plain").assertIsDisplayed()
        rule.onNodeWithText("Puede haber tormentas fuertes esta tarde.").assertIsDisplayed()
        rule.onNodeWithText("En llano").assertIsDisplayed()
        // El texto oficial sigue debajo.
        rule.onNodeWithText("Tormentas con granizo y rachas muy fuertes.").performScrollTo().assertIsDisplayed()
        val plainY = rule.onNodeWithTag("warning_plain").fetchSemanticsNode().positionInRoot.y
        val areaY = rule.onNodeWithText("Llanura de Ejemplo").fetchSemanticsNode().positionInRoot.y
        assertTrue(plainY < areaY)
    }

    @Test fun `la hoja de un aviso sin explicacion es la de siempre`() {
        show(content(warnings = listOf(storm)).copy(report = reportOf()))
        rule.onNodeWithTag("warning_banner").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("warning_sheet").assertIsDisplayed()
        rule.onNodeWithTag("warning_plain").assertDoesNotExist()
    }

    @Test fun `sin conexion los avisos guardados dicen de cuando son`() {
        val fetched = TestData.at(2025, 6, 10, 8, 15)
        val state = content(fetchedAt = fetched, offline = true, warnings = listOf(storm))
        show(state.copy(snapshot = state.snapshot.copy(warningsFetchedAt = fetched)))
        rule.onNodeWithTag("warning_banner").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Avisos guardados · datos de las 08:15").performScrollTo().assertIsDisplayed()
    }

    @Test fun `con conexion no sale la hora de los avisos`() {
        show(content(warnings = listOf(storm)))
        rule.onNodeWithText("Avisos guardados", substring = true).assertDoesNotExist()
    }

    private fun headerTop(text: String): Float = headerNode(text).fetchSemanticsNode().positionInRoot.y

    @Test fun `el punto de ahora solo esta en la fila de Hoy (H6 del 002)`() {
        show(content())
        val days = rule.onAllNodesWithTag("day").fetchSemanticsNodes()
        assertEquals(12.0, days.first().config.getOrElse(NowTemperatureKey) { Double.NaN }, 0.0)
        days.drop(1).forEach { assertTrue(NowTemperatureKey !in it.config) }
    }

    // ---------- 003 · fiabilidad ----------

    private fun relDay(date: String, scenarios: Int = 122, wet: Int = 0, models: List<ModelDay> = emptyList()) =
        DayReliability(date, scenarios, wet, maxLowC = 17.0, maxHighC = 19.0, models = models)

    /** Martes 10: muy alta, seis modelos que coinciden. Miércoles 11: media, tres modelos. Jueves 12: el francés se aparta. Jueves 19: 15 escenarios. */
    private val reliability: Reliability = run {
        val dates = (0 until 10).map { "2025-06-${10 + it}" }
        val six = WeatherModel.entries.map { ModelDay(it, 2, 20.0, 0.0) }
        val three = listOf(ModelDay(WeatherModel.ECMWF, 3, 18.0, 0.4), ModelDay(WeatherModel.GFS, 2, 19.0, 0.2), ModelDay(WeatherModel.GEM, 3, 17.0, 0.8))
        val apart = six.map { if (it.model == WeatherModel.METEO_FRANCE) it.copy(weatherCode = 61, maxC = 17.0, precipMm = 2.8) else it }
        val days = dates.mapIndexed { i, d ->
            when (i) {
                0 -> relDay(d, models = six)
                1 -> relDay(d, wet = 34, models = three)
                2 -> relDay(d, wet = 34, models = apart)
                9 -> relDay(d, scenarios = 15)
                else -> relDay(d, wet = 20)
            }
        }
        Reliability(TestData.place.position, TestData.zone.id, now, days)
    }

    private fun withReliability(base: UiState.Content = content()) = base.copy(reliability = reliability)

    /** El anillo de la píldora del panel: el panel entero es el botón y la píldora ya no une su semántica. */
    private fun pill(label: String) =
        rule.onNode(hasContentDescription(label) and hasAnyAncestor(hasTestTag("reliability_pill")), useUnmergedTree = true)

    private val anyReliability = hasContentDescription("fiabilidad", substring = true, ignoreCase = true) or
        hasText("fiabilidad", substring = true, ignoreCase = true)

    @Test fun `sin fiabilidad valida no hay anillos ni texto (H6)`() {
        show(content(warnings = listOf(storm)))
        assertEquals(0, rule.onAllNodes(anyReliability).fetchSemanticsNodes().size)
        rule.onNodeWithTag("reliability_pill").assertDoesNotExist()
        rule.onNodeWithText("modelos", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test fun `con fiabilidad sale el anillo en el panel y en cada dia con su texto de TalkBack (H3)`() {
        show(withReliability())
        pill("Fiabilidad muy alta").assertIsDisplayed()
        val days = rule.onAllNodesWithTag("day").fetchSemanticsNodes()
        assertTrue(texts(days[0]), texts(days[0]).contains("Martes, 10 de junio: fiabilidad muy alta"))
        assertTrue(texts(days[1]), texts(days[1]).contains("Miércoles, 11 de junio: fiabilidad media"))
        assertTrue(texts(days[3]), texts(days[3]).contains("Viernes, 13 de junio: fiabilidad alta"))
        // Lo de siempre sigue en la fila.
        assertTrue(texts(days[0]), texts(days[0]).startsWith("Hoy") && texts(days[0]).contains("18°"))
    }

    @Test fun `un dia con menos de 20 escenarios va sin anillo (H6)`() {
        show(withReliability())
        val last = rule.onAllNodesWithTag("day").fetchSemanticsNodes()[9]
        assertTrue(texts(last), !texts(last).contains("fiabilidad", ignoreCase = true))
    }

    @Test fun `pasada la medianoche Hoy toma la fiabilidad de la fecha nueva (caso limite)`() {
        val later = TestData.at(2025, 6, 11, 0, 30)
        show(withReliability().copy(now = later))
        val first = rule.onAllNodesWithTag("day").fetchSemanticsNodes().first()
        assertTrue(texts(first), texts(first).startsWith("Hoy") && texts(first).contains("Miércoles, 11 de junio: fiabilidad media"))
        pill("Fiabilidad media").assertIsDisplayed()
    }

    // ---------- 002 · P4: fichas y hojas ----------

    private fun withCards(): UiState.Content {
        val base = content()
        val snapshot = base.snapshot
        val daily = snapshot.daily.mapIndexed { i, d ->
            if (i == 0) d.copy(sunrise = TestData.at(2025, 6, 10, 6, 44), sunset = TestData.at(2025, 6, 10, 21, 43), daylightSeconds = 53940.0) else d
        }
        return base.copy(
            snapshot = snapshot.copy(
                current = snapshot.current.copy(
                    windKmh = 12.0, windGustsKmh = 45.0, windDirectionDeg = 225, humidityPct = 68, dewPointC = 10.2,
                ),
                daily = daily,
                yesterday = DayPoint("2025-06-09", 20.0, 10.0, 0, 0.0, daylightSeconds = 53880.0),
            ),
        )
    }

    @Test fun `con datos salen las fichas de viento, humedad y sol bajo los 10 dias (H10 a H12)`() {
        show(withCards())
        rule.onNodeWithTag("wind_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Viento").assertIsDisplayed()
        rule.onNodeWithText("12 km/h").assertIsDisplayed()
        rule.onNodeWithText("Suroeste · rachas 45").assertIsDisplayed()
        rule.onNodeWithTag("humidity_card").assertIsDisplayed()
        rule.onNodeWithText("Humedad").assertIsDisplayed()
        rule.onNodeWithText("68 %").assertIsDisplayed()
        rule.onNodeWithText("Rocío 10°").assertIsDisplayed()
        rule.onNodeWithTag("sun_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Sol").assertIsDisplayed()
        rule.onNodeWithText("06:44 – 21:43").assertIsDisplayed()
        rule.onNodeWithText("14 h 59 min de luz · 1 min más que ayer").assertIsDisplayed()

        // Orden (H4): 10 días, viento y humedad en la misma fila, sol debajo y el pie al final.
        val days = top("Próximos 10 días")
        val wind = top("12 km/h")
        val humidity = top("68 %")
        val sun = top("06:44 – 21:43")
        val footer = top("Datos del tiempo de Open-Meteo.com", substring = true)
        assertTrue(days < wind && wind < sun && sun < footer)
        assertEquals(wind, humidity, 1f)
        val windX = rule.onNodeWithText("12 km/h").fetchSemanticsNode().positionInRoot.x
        val humidityX = rule.onNodeWithText("68 %").fetchSemanticsNode().positionInRoot.x
        assertTrue(humidityX > windX)
    }

    @Test fun `sin el dato principal la ficha no sale y las demas si`() {
        val full = withCards()
        show(full.copy(snapshot = full.snapshot.copy(current = full.snapshot.current.copy(windKmh = null))))
        rule.onNodeWithTag("wind_card").assertDoesNotExist()
        rule.onNodeWithTag("humidity_card").assertExists()
        rule.onNodeWithTag("sun_card").assertExists()
    }

    // ---------- 004 · P1: ficha de la luna (H7) ----------

    /** 3-nov-2026 en Europe/Madrid: sol de 07:58 a 18:21; la luna del 4 sale a las 02:47 y se pone a las 15:43. */
    private fun moonContent(now: Long, withMoon: Boolean = true): UiState.Content {
        val base = TestData.snapshot(TestData.at(2026, 11, 3, 12, 0))
        val daily = base.daily.mapIndexed { i, d ->
            d.copy(
                sunrise = TestData.at(2026, 11, 3 + i, 7, 58), sunset = TestData.at(2026, 11, 3 + i, 18, 21),
                daylightSeconds = 37_380.0,
                moonrise = TestData.at(2026, 11, 3 + i, 2, 47).takeIf { withMoon },
                moonset = TestData.at(2026, 11, 3 + i, 15, 43).takeIf { withMoon },
                moonPhase = (0.81 + i * 0.03).takeIf { withMoon },
            )
        }
        return UiState.Content(snapshot = base.copy(daily = daily), now = now, offline = false, refreshing = false)
    }

    @Test fun `de noche sale la ficha de la luna con fase y horas en lugar de la del sol (H7)`() {
        show(moonContent(TestData.at(2026, 11, 3, 23, 0)))
        rule.onNodeWithTag("moon_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("sun_card").assertDoesNotExist()
        rule.onNode(hasTestTag("moon_card") and hasContentDescription("Luna: Menguante, 23 por ciento. Sale a las 02:47 · se pone a las 15:43"))
            .assertExists()
    }

    @Test fun `de dia sigue la ficha del sol (H7)`() {
        show(moonContent(TestData.at(2026, 11, 3, 12, 0)))
        rule.onNodeWithTag("sun_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("07:58 – 18:21").assertIsDisplayed()
        rule.onNodeWithTag("moon_card").assertDoesNotExist()
    }

    @Test fun `de noche con datos de antes sigue la ficha del sol de antes (caso limite)`() {
        show(moonContent(TestData.at(2026, 11, 3, 23, 0), withMoon = false))
        rule.onNodeWithTag("sun_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("moon_card").assertDoesNotExist()
    }

    // ---------- 004 · P3: hojas de 24 h (H6) ----------

    /**
     * Viento de 12 km/h del suroeste (rachas de 20) salvo de 00 a 03, flojo (2, rachas de 7); humedad del 30 % más la
     * hora (a las 10, 40 %) y rocío de 11,2°.
     */
    private fun withHourlyData(): UiState.Content {
        val base = withCards()
        val hourly = base.snapshot.hourly.map {
            val h = Instant.ofEpochSecond(it.time).atZone(TestData.zone).hour
            it.copy(
                windKmh = if (h < 3) 2.0 else 12.0, windGustsKmh = if (h < 3) 7.0 else 20.0, windDirectionDeg = 225,
                humidityPct = 30 + h, dewPointC = 11.2,
            )
        }
        return base.copy(snapshot = base.snapshot.copy(hourly = hourly))
    }

    private fun sheetRows() = rule.onAllNodes(hasTestTag("sheet_row")).fetchSemanticsNodes()

    @Test fun `tocar la ficha de viento abre la hoja con 8 tramos de 3 horas y siempre la cifra (H6)`() {
        show(withHourlyData())
        rule.onNodeWithTag("wind_card").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hourly_sheet").assertExists()
        rule.onNodeWithText("Próximas 24 horas, de 3 en 3", substring = true).assertExists()
        val rows = sheetRows().map(::texts)
        assertEquals(8, rows.size)
        // Son las 10:20: el tramo de ahora es el de 09 a 12; luego, en punto.
        assertTrue(rows[0], rows[0].startsWith("Ahora") && rows[0].contains("12 · 20"))
        assertTrue(rows[1], rows[1].startsWith("12–15"))
        assertTrue(rows[5], rows[5].startsWith("00–03") && rows[5].contains("2 · 7"))
        assertTrue(rows[7], rows[7].startsWith("06–09"))
        rule.onNodeWithText("Calma", substring = true).assertDoesNotExist()
        assertEquals(8, rule.onAllNodes(hasTestTag("wind_arrow") and hasAnyAncestor(hasTestTag("hourly_sheet")), useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        rule.onNodeWithText("Miércoles, 11 de junio").assertExists()
    }

    @Test fun `tocar la ficha de humedad abre su hoja por partes del dia con minima, maxima y rocio (H6)`() {
        show(withHourlyData())
        rule.onNodeWithTag("humidity_card").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Próximas 24 horas por partes del día", substring = true).assertExists()
        val rows = sheetRows().map(::texts)
        assertEquals(4, rows.size)
        assertTrue(rows[0], rows[0].startsWith("Mañana") && rows[0].contains("40–41 %") && rows[0].contains("11°"))
        assertTrue(rows[1], rows[1].startsWith("Tarde") && rows[1].contains("42–47 %"))
        assertTrue(rows[2], rows[2].startsWith("Noche") && rows[2].contains("48–53 %"))
        assertTrue(rows[3], rows[3].startsWith("Madrugada") && rows[3].contains("30–35 %"))
        rule.onNodeWithText("Miércoles, 11 de junio").assertExists()
    }

    private lateinit var sheetBack: OnBackPressedDispatcher

    private fun openWindSheet() {
        rule.setContent {
            sheetBack = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MainScreen(withHourlyData(), {}, {}, {}, phoneZone = TestData.zone)
        }
        rule.onNodeWithTag("wind_card").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hourly_sheet").assertExists()
    }

    // 012 P5: la hoja de 24 h es un ModalBottomSheet; se cierra con el velo, con el asa y con atrás.
    @Test fun `el velo cierra la hoja de 24 h`() {
        openWindSheet()
        clickScrim(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("hourly_sheet").assertDoesNotExist()
    }

    @Test fun `el asa cierra la hoja de 24 h`() {
        openWindSheet()
        rule.onNode(hasClickAction() and hasAnyDescendant(hasContentDescription("Drag handle")) or hasContentDescription("Drag handle"))
            .performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hourly_sheet").assertDoesNotExist()
    }

    @Test fun `atras cierra la hoja de 24 h sin cerrar nada mas`() {
        openWindSheet()
        pressSystemBack(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("hourly_sheet").assertDoesNotExist()
        rule.onNodeWithTag("wind_card").assertExists()
    }

    @Test fun `dos toques seguidos en la ficha no abren dos hojas de 24 h`() {
        show(withHourlyData())
        rule.onNodeWithTag("wind_card").performScrollTo().performClick()
        rule.onNodeWithTag("wind_card").performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithTag("hourly_sheet").fetchSemanticsNodes().size)
    }

    @Test fun `las fichas dicen a TalkBack que abren las proximas 24 horas`() {
        show(withHourlyData())
        listOf("wind_card", "humidity_card").forEach {
            val node = rule.onNodeWithTag(it).fetchSemanticsNode()
            assertEquals("Abre las próximas 24 horas", node.config[SemanticsActions.OnClick].label)
        }
    }

    @Test fun `con datos de antes las fichas no abren nada (caso limite)`() {
        show(withCards())
        listOf("wind_card", "humidity_card").forEach {
            rule.onNodeWithTag(it).performScrollTo().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        }
        rule.onNodeWithTag("hourly_sheet").assertDoesNotExist()
    }

    @Test fun `la demo abre la hoja de viento`() {
        rule.setContent { MainScreen(withHourlyData(), {}, {}, {}, initialHourlySheet = HourlySheetKind.WIND) }
        rule.waitForIdle()
        rule.onNodeWithTag("hourly_sheet").assertExists()
        assertEquals(8, sheetRows().size)
    }

    @Test fun `con un snapshot de la 001 no hay fichas y lo demas se ve (caso limite)`() {
        val json = javaClass.getResource("/snapshot_001.json")!!.readText()
        val snapshot = Json { ignoreUnknownKeys = true }.decodeFromString(StoredState.serializer(), json).snapshot!!
        show(UiState.Content(snapshot = snapshot, now = snapshot.fetchedAt, offline = false, refreshing = false))
        rule.onNodeWithText("Villa Ejemplo").assertIsDisplayed()
        rule.onNodeWithText("Próximos 10 días").assertExists()
        rule.onNodeWithText("Datos del tiempo de Open-Meteo.com", substring = true).assertExists()
        rule.onNodeWithTag("wind_card").assertDoesNotExist()
        rule.onNodeWithTag("humidity_card").assertDoesNotExist()
        rule.onNodeWithTag("sun_card").assertDoesNotExist()
        rule.onNodeWithText("Viento").assertDoesNotExist()
    }

    @Test fun `la hoja del aviso tiene nivel, fenomeno y los pares de la maqueta (H8)`() {
        show(content(warnings = listOf(storm)))
        rule.onNodeWithTag("warning_banner").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Aviso naranja").assertIsDisplayed()
        rule.onNodeWithText("Tormentas").assertIsDisplayed()
        listOf("Zona", "Vigencia", "Probabilidad", "Comentario", "Fuente").forEach {
            rule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        // 011, P2: la hoja se cierra con el velo (012, P5: ya no lleva cruz).
        clickScrim(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("warning_sheet").assertDoesNotExist()
    }

    @Test fun `las pantallas de mensaje llevan el boton blanco`() {
        show(UiState.NeedsPermission)
        rule.onNodeWithTag("white_button").assertIsDisplayed().assert(hasText("Dar permiso"))
    }

    @Test fun `sin ningun dia con nivel la fila va como siempre`() {
        val none = reliability.copy(days = reliability.days.map { it.copy(scenarios = 15) })
        assertEquals(null, rowLevels(none))
        assertEquals(null, rowLevels(null))
        assertEquals(ReliabilityLevel.VERY_HIGH, rowLevels(reliability)?.get("2025-06-10"))
    }

    // ---------- 003 · P3: Ajustes ----------

    /** 214 llamadas hoy y 1.046 este mes (en décimas), con las fechas de `now` en la zona del móvil. */
    private fun withUsage(): UiState.Content {
        val local = java.time.Instant.ofEpochSecond(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return content().copy(usage = ApiUsage(local.toString(), 2140, local.toString().take(7), 10460))
    }

    private fun openSettings() {
        rule.onNodeWithText("Ajustes").performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test fun `la pildora Ajustes abre el consumo de Open-Meteo y nada mas (H11)`() {
        show(withUsage())
        openSettings()
        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
        rule.onNodeWithText("Consumo de Open-Meteo", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Hoy", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("214 llamadas").assertIsDisplayed()
        rule.onNodeWithText("de 10.000 · 2 % · vuelve a 0 a medianoche").assertIsDisplayed()
        rule.onNodeWithText("Este mes", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("1.046 llamadas").assertIsDisplayed()
        rule.onNodeWithText("de 300.000 · 0,3 % · vuelve a 0 el día 1").assertIsDisplayed()
        rule.onNodeWithText("Estimación de esta app", substring = true).performScrollTo().assertIsDisplayed()
        // Una sola sección: ni la previsión ni otros ajustes.
        rule.onNodeWithTag("main_scroll").assertDoesNotExist()
        rule.onAllNodesWithTag("settings_section").assertCountEquals(1)
    }

    @Test fun `tras reinstalar Ajustes sale con 0 de 10_000 y 0 de 300_000`() {
        show(content())
        openSettings()
        rule.onAllNodesWithText("0 llamadas").assertCountEquals(2)
        rule.onNodeWithText("de 10.000 · 0 % · vuelve a 0 a medianoche").assertIsDisplayed()
    }

    @Test fun `la flecha vuelve a la pantalla principal (H11)`() {
        show(withUsage())
        openSettings()
        rule.onNode(hasContentDescription("Volver")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertDoesNotExist()
        rule.onNodeWithTag("main_scroll").assertExists()
    }

    @Test fun `el gesto de volver deja la pantalla principal (H11)`() {
        lateinit var back: OnBackPressedDispatcher
        rule.setContent {
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MainScreen(withUsage(), {}, {}, {})
        }
        openSettings()
        rule.runOnUiThread { back.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertDoesNotExist()
        rule.onNodeWithTag("main_scroll").assertExists()
    }

    @Test fun `la demo puede abrir Ajustes al empezar`() {
        rule.setContent { MainScreen(withUsage(), {}, {}, {}, initialSettings = true) }
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    // ---------- 005 · P2: fichas y hojas del aire y del polen ----------

    /**
     * Aire de Villa Ejemplo desde hoy (10-jun-2025) a las 00:00, [hours] horas: a las 10 (la hora de `now`) [nowAqi],
     * a las 15 de cada día su peor hora ([worst]) y el resto 15. Polen constante por día ([olive], [grass]) en las
     * primeras [pollenHours] horas. [cause]: "ozone" (gana el ozono) o "dust" (partículas con calima).
     */
    private fun withAir(
        nowAqi: Int = 35,
        worst: List<Int> = listOf(42, 38, 45, 31),
        cause: String = "ozone",
        olive: List<Double>? = null,
        grass: List<Double>? = null,
        hours: Int = 96,
        pollenHours: Int = hours,
        start: Long = TestData.at(2025, 6, 10, 0, 0),
    ): UiState.Content {
        val base = withCards()
        val list = (0 until hours).map { i ->
            val day = i / 24
            val aqi = when {
                i == 10 -> nowAqi
                i % 24 == 15 -> worst[day]
                else -> 15
            }
            val dust = cause == "dust"
            AirHour(
                time = start + i * 3600L,
                aqi = aqi,
                pm25Index = aqi / 3,
                pm10Index = if (dust) aqi else aqi / 3,
                ozoneIndex = if (dust) aqi / 3 else aqi,
                no2Index = aqi / 4,
                so2Index = 1,
                pm10 = if (dust) 60.0 else 10.0,
                dust = if (dust) 40.0 else 0.5,
                olivePollen = olive?.getOrNull(day)?.takeIf { i < pollenHours },
                grassPollen = grass?.getOrNull(day)?.takeIf { i < pollenHours },
            )
        }
        val air = AirQuality(LatLon(40.0, -3.0), fetchedAt = now, hours = list)
        return base.copy(snapshot = base.snapshot.copy(air = air))
    }

    private fun airCard() = rule.onNodeWithTag("air_card").performScrollTo()

    private fun description(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")

    @Test fun `la ficha del aire dice nivel, cifra y causa (H6)`() {
        show(withAir())
        airCard().assertIsDisplayed()
        assertEquals("Calidad del aire: razonablemente buena, índice 35. Por el ozono.", description("air_card"))
    }

    @Test fun `con aire bueno la ficha va sin causa (H5)`() {
        show(withAir(nowAqi = 16, worst = listOf(18, 23, 16, 19)))
        airCard().assertIsDisplayed()
        assertEquals("Calidad del aire: buena, índice 16.", description("air_card"))
    }

    @Test fun `con 134 y calima la ficha dice extremadamente desfavorable y polvo del Sahara (indice muy alto)`() {
        show(withAir(nowAqi = 134, worst = listOf(134, 112, 81, 55), cause = "dust"))
        airCard().assertIsDisplayed()
        assertEquals("Calidad del aire: extremadamente desfavorable, índice 134. Por polvo del Sáhara.", description("air_card"))
    }

    @Test fun `sin aire guardado no sale ninguna de las dos fichas (datos de antes)`() {
        show(withCards())
        rule.onNodeWithTag("sun_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("air_card").assertDoesNotExist()
        rule.onNodeWithTag("pollen_card").assertDoesNotExist()
    }

    @Test fun `con el aire caducado no sale ninguna de las dos fichas (H6)`() {
        show(withAir(olive = List(4) { 300.0 }, start = TestData.at(2025, 6, 1, 0, 0)))
        rule.onNodeWithTag("sun_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("air_card").assertDoesNotExist()
        rule.onNodeWithTag("pollen_card").assertDoesNotExist()
    }

    @Test fun `la ficha del polen con solo olivo no nombra las gramineas (H8)`() {
        show(withAir(olive = listOf(300.0, 100.0, 30.0, 0.0), grass = List(4) { 0.0 }))
        rule.onNodeWithTag("pollen_card").performScrollTo().assertIsDisplayed()
        assertEquals("Polen hoy: olivo alto.", description("pollen_card"))
    }

    @Test fun `la ficha del polen con los dos tipos (H8)`() {
        show(withAir(olive = listOf(300.0, 100.0, 30.0, 0.0), grass = listOf(40.0, 60.0, 0.0, 10.0)))
        rule.onNodeWithTag("pollen_card").performScrollTo().assertIsDisplayed()
        assertEquals("Polen hoy: olivo alto, gramíneas moderado.", description("pollen_card"))
    }

    @Test fun `con el polen a 0 no sale su ficha y la del aire si (fuera de temporada)`() {
        show(withAir(olive = List(4) { 0.0 }, grass = List(4) { 0.0 }))
        airCard().assertIsDisplayed()
        rule.onNodeWithTag("pollen_card").assertDoesNotExist()
    }

    @Test fun `las fichas nuevas van debajo del sol, primero el aire y luego el polen`() {
        show(withAir(olive = List(4) { 300.0 }, grass = List(4) { 40.0 }))
        fun y(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y
        val sun = y("sun_card")
        val air = y("air_card")
        val pollen = y("pollen_card")
        val footer = top("Datos del tiempo de Open-Meteo.com", substring = true)
        assertTrue("$sun < $air < $pollen < $footer", sun < air && air < pollen && pollen < footer)
    }

    private fun sheetDays(sheet: String, block: String? = null) = rule.onAllNodes(
        hasTestTag("days_sheet_day") and hasAnyAncestor(hasTestTag(sheet)) and
            (if (block != null) hasAnyAncestor(hasTestTag(block)) else SemanticsMatcher("cualquiera") { true }),
    ).fetchSemanticsNodes()

    @Test fun `tocar el aire abre su hoja con hoy y los 3 dias siguientes y su peor hora (H9)`() {
        show(withAir())
        airCard().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("air_sheet").assertExists()
        rule.onNodeWithText("Hoy y los 3 días siguientes · índice europeo de calidad del aire").assertExists()
        val days = sheetDays("air_sheet").map(::texts)
        assertEquals(4, days.size)
        listOf("Hoy" to 42, "Mañana" to 38, "Jue 12" to 45, "Vie 13" to 31).forEachIndexed { i, (label, aqi) ->
            assertTrue(days[i], days[i].startsWith(label) && days[i].contains("índice $aqi"))
        }
        assertTrue(days[0], days[0].contains("Regular"))
        assertTrue(days[1], days[1].contains("Razonablemente buena"))
        rule.onNodeWithText("El nivel de cada día es el de su peor hora", substring = true).assertExists()
    }

    @Test fun `con el ultimo dia a medias la hoja del aire enseña 3 (H9)`() {
        show(withAir(hours = 90))
        airCard().performClick()
        rule.waitForIdle()
        assertEquals(3, sheetDays("air_sheet").size)
    }

    @Test fun `tocar el polen abre su hoja con los dos bloques y Nada incluido (H9)`() {
        show(withAir(olive = listOf(300.0, 100.0, 30.0, 0.0), grass = listOf(40.0, 60.0, 0.0, 10.0)))
        rule.onNodeWithTag("pollen_card").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("pollen_sheet").assertExists()
        rule.onNodeWithText("Hoy y los 3 días siguientes · olivo y gramíneas").assertExists()
        val olive = sheetDays("pollen_sheet", "pollen_block_olive").map(::texts)
        val grass = sheetDays("pollen_sheet", "pollen_block_grass").map(::texts)
        assertEquals(listOf("Hoy Alto", "Mañana Moderado", "Jue 12 Bajo", "Vie 13 Nada"), olive)
        assertEquals(listOf("Hoy Moderado", "Mañana Alto", "Jue 12 Nada", "Vie 13 Bajo"), grass)
        rule.onNodeWithText("Red Española de Aerobiología", substring = true).assertExists()
    }

    @Test fun `el dia a medias no sale en ninguno de los dos bloques del polen (H9)`() {
        show(withAir(olive = List(4) { 300.0 }, grass = List(4) { 40.0 }, pollenHours = 90))
        rule.onNodeWithTag("pollen_card").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(3, sheetDays("pollen_sheet", "pollen_block_olive").size)
        assertEquals(3, sheetDays("pollen_sheet", "pollen_block_grass").size)
    }

    @Test fun `el velo cierra las hojas del aire y del polen`() {
        show(withAir(olive = List(4) { 300.0 }, grass = List(4) { 40.0 }))
        listOf("air_card" to "air_sheet", "pollen_card" to "pollen_sheet").forEach { (card, sheet) ->
            rule.onNodeWithTag(card).performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag(sheet).assertExists()
            // 011, P2: la hoja se cierra con el velo (012, P5: ya no lleva cruz).
            clickScrim(rule)
            rule.waitForIdle()
            rule.onNodeWithTag(sheet).assertDoesNotExist()
        }
    }

    @Test fun `atras y el asa cierran las hojas del aire y del polen, y dos toques no abren dos`() {
        show(withAir(olive = List(4) { 300.0 }, grass = List(4) { 40.0 }))
        listOf("air_card" to "air_sheet", "pollen_card" to "pollen_sheet").forEach { (card, sheet) ->
            rule.onNodeWithTag(card).performScrollTo().performClick()
            rule.onNodeWithTag(card).performClick()
            rule.waitForIdle()
            assertEquals(1, rule.onAllNodesWithTag(sheet).fetchSemanticsNodes().size)
            pressSystemBack(rule)
            rule.waitForIdle()
            rule.onNodeWithTag(sheet).assertDoesNotExist()
            rule.onNodeWithTag(card).performClick()
            rule.waitForIdle()
            rule.onNode(hasContentDescription("Drag handle")).performClick()
            rule.waitForIdle()
            rule.onNodeWithTag(sheet).assertDoesNotExist()
        }
    }

    @Test fun `atras y el asa cierran la hoja del aviso, y dos toques no abren dos`() {
        show(content(warnings = listOf(storm)))
        rule.onNodeWithTag("warning_banner").performClick()
        rule.onNodeWithTag("warning_banner").performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithTag("warning_sheet").fetchSemanticsNodes().size)
        pressSystemBack(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("warning_sheet").assertDoesNotExist()
        rule.onNodeWithTag("warning_banner").performClick()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Drag handle")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("warning_sheet").assertDoesNotExist()
    }

    @Test fun `las fichas del aire y del polen dicen a TalkBack que abren los proximos dias`() {
        show(withAir(olive = List(4) { 300.0 }, grass = List(4) { 40.0 }))
        listOf("air_card", "pollen_card").forEach {
            val node = rule.onNodeWithTag(it).performScrollTo().fetchSemanticsNode()
            assertEquals("Abre los próximos días", node.config[SemanticsActions.OnClick].label)
        }
    }

    @Test fun `sin dias que enseñar la ficha del aire no se toca`() {
        // Solo hasta las 12 de hoy: hay cifra de ahora, pero ningún día completo.
        show(withAir(hours = 12))
        airCard().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test fun `el pie cita CAMS`() {
        show(withAir())
        rule.onNodeWithText("Aire y polen: CAMS (Copernicus), vía Open-Meteo", substring = true).assertExists()
    }

    @Test fun `la demo abre la hoja del aire`() {
        rule.setContent { MainScreen(withAir(), {}, {}, {}, initialDaysSheet = DaysSheetKind.AIR) }
        rule.waitForIdle()
        rule.onNodeWithTag("air_sheet").assertExists()
        assertEquals(4, sheetDays("air_sheet").size)
    }

    // ---- 006 · varios sitios ----

    private val granada = SavedPlace("grx", "Granada", "Granada", "ES", LatLon(37.18, -3.6), savedAt = 1)

    private fun savedContent(now: Long = this.now, fetchedAt: Long = now, offline: Boolean = false, zoneId: String = TestData.zone.id) =
        UiState.Content(
            snapshot = TestData.snapshot(now, currentC = 12.0, fetchedAt = fetchedAt).copy(zoneId = zoneId),
            now = now, offline = offline, refreshing = false,
            place = PlaceRef.Saved("sev"), savedName = "Sevilla",
        )

    private fun cards(withSaved: Boolean = true) = listOf(
        PlaceCardModel(PlaceRef.Here, "Villa Ejemplo", SkyPhoto.CLOUDS, Sky.CLOUDY, 12, 18, 9, null, null, false, true),
    ) + if (withSaved) {
        listOf(PlaceCardModel(PlaceRef.Saved("sev"), "Sevilla", SkyPhoto.CLEAR_MILD, Sky.CLEAR, 22, 24, 11, null, null, false, false))
    } else {
        emptyList()
    }

    private val selectedPlaces = mutableListOf<PlaceRef>()
    private var placesOpened = 0

    private fun showPlaces(state: UiState, onRetry: () -> Unit = {}, saved: Boolean = true) = rule.setContent {
        MainScreen(
            state, {}, onRetry, {}, phoneZone = TestData.zone,
            placeCards = cards(saved),
            onPlacesOpen = { placesOpened++ },
            onSelectPlace = { selectedPlaces += it },
        )
    }

    @Test fun `la cabecera dice Donde estoy y TalkBack lo lee con el nombre (H9)`() {
        showPlaces(content())
        rule.onNodeWithText("Donde estoy", useUnmergedTree = true).assertExists()   // 009: sin versalitas en la cabecera
        rule.onNode(hasContentDescription("Villa Ejemplo, donde estoy. Cambiar de sitio")).assertIsDisplayed()
    }

    @Test fun `un guardado dice Sitio guardado (H9)`() {
        showPlaces(savedContent())
        rule.onNodeWithText("Sitio guardado", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Donde estoy", useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(hasContentDescription("Sevilla, sitio guardado. Cambiar de sitio")).assertIsDisplayed()
    }

    @Test fun `tocar la cabecera abre Sitios y tocar una postal vuelve a la principal con ese sitio (H8)`() {
        showPlaces(content())
        rule.onNode(hasContentDescription("Villa Ejemplo, donde estoy. Cambiar de sitio")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("places_screen").assertExists()
        assertEquals(1, placesOpened)
        rule.onNodeWithText("Sevilla", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(listOf<PlaceRef>(PlaceRef.Saved("sev")), selectedPlaces)
        rule.onNodeWithTag("places_screen").assertDoesNotExist()
        rule.onNodeWithTag("main_scroll").assertExists()
    }

    @Test fun `atras en Sitios vuelve sin cambiar de sitio (H8)`() {
        lateinit var back: OnBackPressedDispatcher
        rule.setContent {
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, placeCards = cards(), onSelectPlace = { selectedPlaces += it })
        }
        rule.onNode(hasContentDescription("Villa Ejemplo, donde estoy. Cambiar de sitio")).performClick()
        rule.waitForIdle()
        rule.runOnUiThread { back.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("places_screen").assertDoesNotExist()
        rule.onNodeWithTag("main_scroll").assertExists()
        assertTrue(selectedPlaces.isEmpty())
    }

    @Test fun `con otro desfase la fecha dice hora de alli`() {
        show(savedContent(zoneId = "Atlantic/Canary"))
        rule.onNodeWithText("Martes, 10 de junio · 09:20, hora de allí").assertIsDisplayed()
    }

    @Test fun `con el mismo desfase no dice hora de alli`() {
        show(savedContent())
        rule.onNodeWithText("Martes, 10 de junio · 10:20").assertIsDisplayed()
        rule.onNodeWithText("hora de allí", substring = true).assertDoesNotExist()
    }

    @Test fun `un guardado nuevo descargando dice Descargando el tiempo de Granada`() {
        showPlaces(UiState.SavedDownloading(granada))
        rule.onNodeWithText("Descargando el tiempo de Granada…").assertIsDisplayed()
        rule.onNode(hasContentDescription("Granada, sitio guardado. Cambiar de sitio")).assertExists()
    }

    @Test fun `un guardado sin datos ni conexion se puede reintentar y cambiar de sitio`() {
        var retries = 0
        showPlaces(UiState.SavedNoData(granada), onRetry = { retries++ })
        rule.onNodeWithText("Aún no tengo datos de Granada").assertIsDisplayed()
        rule.onNodeWithText("Lo has guardado sin conexión", substring = true).assertExists()
        rule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
        rule.onNode(hasContentDescription("Granada, sitio guardado. Cambiar de sitio")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("places_screen").assertExists()
    }

    @Test fun `sin permiso Donde estoy abre Sitios y los guardados se abren`() {
        showPlaces(UiState.NeedsPermission)
        rule.onNodeWithText("Necesito saber dónde estás").assertIsDisplayed()
        rule.onNodeWithText("DONDE ESTOY", useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(hasContentDescription("Donde estoy. Cambiar de sitio")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("places_screen").assertExists()
        rule.onNodeWithText("Sevilla", useUnmergedTree = true).performClick()
        assertEquals(listOf<PlaceRef>(PlaceRef.Saved("sev")), selectedPlaces)
    }

    @Test fun `sin conexion en un guardado con datos del lunes lo dice con el dia`() {
        val wednesday = TestData.at(2025, 6, 11, 10, 20)
        show(savedContent(now = wednesday, fetchedAt = TestData.at(2025, 6, 9, 9, 12), offline = true))
        rule.onNodeWithText("Sin conexión · datos del lunes, 09:12").assertIsDisplayed()
    }

    @Test fun `la demo puede empezar en Sitios`() {
        rule.setContent { MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, placeCards = cards(), initialPlaces = true) }
        rule.waitForIdle()
        rule.onNodeWithTag("places_screen").assertIsDisplayed()
    }
    // ---------- 012 P6 · H9, H10: escena quieta ----------

    private fun showSky(state: UiState, clock: SkyClock?, settings: Boolean = false, places: Boolean = false) = rule.setContent {
        MainScreen(state, {}, {}, {}, phoneZone = TestData.zone, skyClock = clock, initialSettings = settings, initialPlaces = places)
    }

    @Test fun `con datos y permiso la escena no pide fotogramas, queda en reposo (H9)`() {
        showSky(content(), SkyClock())
        rule.onNodeWithTag("sky-moving").assertExists()
        // Con un bucle de fotogramas, esperar a que se pare no terminaría.
        rule.waitForIdle()
    }

    @Test fun `con Ajustes o Sitios abiertos tampoco (H9)`() {
        showSky(content(), SkyClock(), settings = true)
        rule.waitForIdle()
        rule.onNodeWithTag("sky-moving").assertExists()
    }

    @Test fun `sin permiso para animar la escena va marcada quieta (H8)`() {
        showSky(content(), null)
        rule.onNodeWithTag("sky-still").assertExists()
        rule.onNodeWithTag("sky-moving").assertDoesNotExist()
    }

    @Test fun `la entrada no mueve la foto ni los paneles, solo el astro y el velo (H10)`() {
        var intro: IntroState? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            intro = rememberIntro(play = true)
            MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, skyClock = SkyClock(), intro = intro)
        }
        rule.mainClock.advanceTimeByFrame()
        fun top() = rule.onNodeWithTag("main_scroll").fetchSemanticsNode().positionInRoot.y
        val first = top()
        val cards = rule.onAllNodesWithTag("day").fetchSemanticsNodes().map { it.positionInRoot.y }
        rule.mainClock.advanceTimeBy(300)
        assertEquals("el contenido se movió en la entrada", first, top(), 0.01f)
        assertEquals("los paneles se movieron en la entrada", cards, rule.onAllNodesWithTag("day").fetchSemanticsNodes().map { it.positionInRoot.y })
        rule.mainClock.advanceTimeBy(INTRO_MS)
        assertEquals(first, top(), 0.01f)
        assertNull("la entrada sigue en marcha", rule.runOnIdle { intro?.takeIf { it.running } })
    }

    // ---------- 009 · P1: fotos del cielo ----------

    @Test fun `cargando sin datos y los mensajes van sobre la foto de nubes, nunca lisos`() {
        val tag = "sky-photo-${LOADING_PHOTO.file}"
        var state by mutableStateOf<UiState>(UiState.Loading)
        rule.setContent { MainScreen(state, {}, {}, {}, phoneZone = TestData.zone, skyClock = SkyClock()) }
        rule.onNodeWithTag(tag).assertExists()
        for (next in listOf(UiState.NeedsPermission, UiState.Searching)) {
            state = next
            rule.waitForIdle()
            rule.onNodeWithTag(tag).assertExists()
        }
    }

    @Test fun `con datos va la foto del tiempo de ahora`() {
        val state = content()
        showSky(state, null)
        rule.onNodeWithTag("sky-photo-${skyPhotoNow(state.snapshot, state.now).file}").assertExists()
    }

    @Test fun `la demo puede forzar la foto y dejar solo el cristal`() {
        rule.setContent {
            MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, demoPhoto = SkyPhoto.STORM to SkyEffects.NONE, bare = true)
        }
        rule.onNodeWithTag("sky-photo-sky_tormenta").assertExists()
        rule.onNodeWithTag("bare_glass").assertExists()
        rule.onAllNodesWithText("Próximas", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test fun `sin reloj la escena esta quieta (H8)`() {
        showSky(content(), null)
        rule.onNodeWithTag("sky-still").assertExists()
        rule.onNodeWithTag("sky-moving").assertDoesNotExist()
    }

    // ---------- 007 · H15: la entrada al abrir ----------

    /** Como `MainActivity`: la entrada se decide con [gate] y el primer estado que no sea la carga. */
    private fun showIntro(state: () -> UiState, gate: IntroGate, allowed: Boolean = true, onRefresh: () -> Unit = {}) = rule.setContent {
        val s = state()
        val intro = rememberMainIntro(s, gate, MotionSettings { allowed })
        MainScreen(s, onRefresh, {}, {}, phoneZone = TestData.zone, intro = intro)
    }

    @Test fun `al abrir en frio con datos sale el velo y a 1 s ya no esta (H15)`() {
        var state by mutableStateOf<UiState>(UiState.Loading)
        val gate = IntroGate()
        rule.mainClock.autoAdvance = false
        showIntro({ state }, gate)
        rule.mainClock.advanceTimeByFrame()
        // Mientras carga, el velo tapa del todo.
        rule.onNodeWithTag("intro-veil").assertExists()
        state = content()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("intro-veil").assertExists()
        rule.onNodeWithText("Villa Ejemplo").assertExists()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        // Gastada.
        assertTrue(!gate.pending)
    }

    @Test fun `durante la entrada tocar el panel abre el dia (H15)`() {
        val gate = IntroGate()
        rule.mainClock.autoAdvance = false
        showIntro({ content() }, gate)
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("intro-veil").assertExists()
        rule.onNodeWithTag("main_panel").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("day_window").assertExists()
    }

    @Test fun `durante la entrada deslizar actualiza y desplazar desplaza (H15)`() {
        var refreshes = 0
        val gate = IntroGate()
        rule.mainClock.autoAdvance = false
        showIntro({ content() }, gate, onRefresh = { refreshes++ })
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("intro-veil").assertExists()
        rule.onNodeWithTag("main_scroll").performTouchInput { swipeDown() }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, refreshes)
        val before = rule.onNodeWithText("Villa Ejemplo").fetchSemanticsNode().positionInRoot.y
        rule.onNodeWithTag("main_scroll").performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(1500)
        val after = rule.onNodeWithText("Villa Ejemplo").fetchSemanticsNode().positionInRoot.y
        assertTrue("$before -> $after", after < before - 50)
    }

    @Test fun `si el primer estado es un mensaje no hay velo, tampoco despues (H15)`() {
        var state by mutableStateOf<UiState>(UiState.NeedsPermission)
        val gate = IntroGate()
        showIntro({ state }, gate)
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        state = content()
        rule.waitForIdle()
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        assertTrue(!gate.pending)
    }

    @Test fun `si el primer estado es un guardado descargando no hay velo (H15)`() {
        val gate = IntroGate()
        val place = SavedPlace("p1", "Pueblo Ejemplo", "Provincia", "ES", LatLon(41.0, -4.0), savedAt = 0)
        showIntro({ UiState.SavedDownloading(place) }, gate)
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        assertTrue(!gate.pending)
    }

    @Test fun `si Android no deja animar no hay velo (H8 y H15)`() {
        var state by mutableStateOf<UiState>(UiState.Loading)
        showIntro({ state }, IntroGate(), allowed = false)
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        state = content()
        rule.waitForIdle()
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
    }

    @Test fun `ya gastada no hay velo, ni con otro Content (H15)`() {
        var state by mutableStateOf<UiState>(content())
        val gate = IntroGate().also { it.take() }
        showIntro({ state }, gate)
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        // Un refresco, otro sitio: tampoco.
        state = content(refreshing = true)
        rule.waitForIdle()
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        state = savedContent()
        rule.waitForIdle()
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
    }

    @Test fun `tras la entrada, otro Content o abrir un dia y volver no la repite (H15)`() {
        var state by mutableStateOf<UiState>(content())
        showIntro({ state }, IntroGate())
        rule.waitForIdle()
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        state = content(refreshing = true)
        rule.waitForIdle()
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
        rule.onNodeWithTag("main_panel").performClick()
        rule.onNodeWithTag("day_window").assertExists()
        rule.onNodeWithTag("intro-veil").assertDoesNotExist()
    }

    @Test fun `el velo y el cielo no se leen ni se tocan (TalkBack, H15)`() {
        rule.mainClock.autoAdvance = false
        showIntro({ content() }, IntroGate())
        rule.mainClock.advanceTimeBy(100)
        for (tag in listOf("intro-veil", "sky-still")) {
            rule.onNodeWithTag(tag, useUnmergedTree = true)
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        }
    }

    @Test fun `el pie acaba en Luna NASA (H12)`() {
        show(content())
        rule.onNodeWithText("Datos del tiempo de Open-Meteo.com", substring = true).performScrollTo()
            .assert(SemanticsMatcher("acaba en · Luna: NASA") { node ->
                node.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().any { it.text.endsWith("· Luna: NASA") }
            })
    }

    // ---------- 018 P4 · la pregunta ----------

    private val withPill get() = content().copy(ask = AskEntry.Open)

    @Test fun `sin clave no hay pildora y la tarjeta es la de hoy (H7)`() {
        show(content())
        rule.onNodeWithTag("summary_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ask_pill").assertDoesNotExist()
        assertEquals(1, rule.onAllNodesWithTag("summary_line").fetchSemanticsNodes().size)
    }

    @Test fun `con clave la pildora lleva el nombre del sitio (H1)`() {
        show(withPill)
        rule.onNodeWithTag("ask_pill").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ask_pill").assert(hasText("Pregunta por el tiempo de Villa Ejemplo"))
    }

    @Test fun `en un sitio guardado lleva el nombre del guardado`() {
        show(withPill.copy(place = PlaceRef.Saved("p1"), savedName = "Sevilla"))
        rule.onNodeWithTag("ask_pill").performScrollTo().assert(hasText("Pregunta por el tiempo de Sevilla"))
    }

    @Test fun `con el tope esta apagada, lo dice y no abre nada (H5)`() {
        show(content().copy(ask = AskEntry.Capped(20)))
        rule.onNodeWithTag("ask_pill").performScrollTo().assert(hasText("Mañana más · ya van 20 preguntas hoy"))
        rule.onNodeWithTag("ask_pill").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ask_sheet").assertDoesNotExist()
    }

    @Test fun `con clave y sin frases ni avisos la tarjeta sale con la pildora`() {
        val base = content()
        val old = base.copy(snapshot = base.snapshot.copy(hourly = emptyList()), ask = AskEntry.Open)
        assertTrue(com.trastealabs.tiempo.domain.summaryLines(old.snapshot, old.now, TestData.zone).isEmpty())
        show(old)
        rule.onNodeWithTag("summary_card").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("ask_pill").assertIsDisplayed()
        // Sin la píldora, esa misma tarjeta no sale.
        rule.runOnUiThread { }
    }

    @Test fun `sin clave y sin frases ni avisos no hay tarjeta`() {
        val base = content()
        show(base.copy(snapshot = base.snapshot.copy(hourly = emptyList())))
        rule.onNodeWithTag("summary_card").assertDoesNotExist()
    }

    @Test fun `tocar la pildora abre la hoja con el sitio y enviar llama a onAsk`() {
        val asked = mutableListOf<String>()
        rule.setContent {
            MainScreen(withPill, {}, {}, {}, phoneZone = TestData.zone, onAsk = { asked += it })
        }
        rule.onNodeWithTag("ask_sheet").assertDoesNotExist()
        rule.onNodeWithTag("ask_pill").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ask_sheet").assertIsDisplayed()
        rule.onNodeWithTag("ask_title").assert(hasText("Pregunta por Villa Ejemplo"))
        rule.onNodeWithTag("ask_field").performTextInput("¿Puedo tender mañana?")
        rule.onNodeWithTag("ask_send").performClick()
        assertEquals(listOf("¿Puedo tender mañana?"), asked)
    }

    @Test fun `la hoja enseña el estado que le da la pantalla`() {
        var askState by mutableStateOf<AskState>(AskState.Writing)
        rule.setContent {
            MainScreen(withPill, {}, {}, {}, phoneZone = TestData.zone, askState = askState, initialAskOpen = true)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("ask_field").assertIsDisplayed()
        askState = AskState.Asking("¿Llueve?")
        rule.waitForIdle()
        rule.onNodeWithTag("ask_working").assert(hasText("Mirando la previsión de Villa Ejemplo…"))
    }

    @Test fun `cerrar la hoja avisa para que se olvide la pregunta (H7)`() {
        var closed = 0
        rule.setContent {
            MainScreen(withPill, {}, {}, {}, phoneZone = TestData.zone, onCloseAsk = { closed++ }, initialAskOpen = true)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("ask_sheet").assertIsDisplayed()
        clickScrim(rule)
        rule.waitForIdle()
        assertEquals(1, closed)
        rule.onNodeWithTag("ask_sheet").assertDoesNotExist()
    }

    @Test fun `al salir de la app (o girar) la hoja se cierra y se cancela lo que haya en curso (H7)`() {
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        rule.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        var closed = 0
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                MainScreen(withPill, {}, {}, {}, phoneZone = TestData.zone, askState = AskState.Asking("¿Llueve?"), onCloseAsk = { closed++ }, initialAskOpen = true)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("ask_sheet").assertIsDisplayed()
        rule.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        rule.waitForIdle()
        assertEquals(1, closed)
        rule.onNodeWithTag("ask_sheet").assertDoesNotExist()
    }

    @Test fun `la hoja de una pregunta no se queda al cambiar de sitio`() {
        var state by mutableStateOf<UiState>(withPill)
        rule.setContent { MainScreen(state, {}, {}, {}, phoneZone = TestData.zone, initialAskOpen = true) }
        rule.waitForIdle()
        rule.onNodeWithTag("ask_sheet").assertIsDisplayed()
        state = withPill.copy(place = PlaceRef.Saved("p1"), savedName = "Sevilla")
        rule.waitForIdle()
        rule.onNodeWithTag("ask_sheet").assertDoesNotExist()
        // Y al volver al primero no reaparece sola.
        state = withPill
        rule.waitForIdle()
        rule.onNodeWithTag("ask_sheet").assertDoesNotExist()
    }
}

/**
 * «Atrás» como lo manda el sistema: la tecla a la ventana de más arriba (las hojas de M3 son una ventana propia). La usan
 * los tests de cierre de todas las hojas (012, P5).
 */
internal fun pressSystemBack(rule: androidx.compose.ui.test.junit4.ComposeTestRule) {
    rule.runOnUiThread {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)!!
        @Suppress("UNCHECKED_CAST")
        val names = global.javaClass.getMethod("getViewRootNames").invoke(global) as Array<String>
        val top = global.javaClass.getMethod("getRootView", String::class.java).invoke(global, names.last()) as android.view.View
        val t = android.os.SystemClock.uptimeMillis()
        top.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK, 0))
        top.dispatchKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK, 0))
    }
}

/** El velo de una hoja de M3: se toca arriba del todo, donde solo está él. */
internal fun clickScrim(rule: androidx.compose.ui.test.junit4.ComposeTestRule) {
    rule.onNode(androidx.compose.ui.test.hasContentDescription("Close sheet"))
        .performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 10f)) }
}
