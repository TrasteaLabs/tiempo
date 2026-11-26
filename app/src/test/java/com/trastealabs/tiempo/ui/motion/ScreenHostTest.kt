package com.trastealabs.tiempo.ui.motion

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.MainScreen
import com.trastealabs.tiempo.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El paso entre pantallas (011, H3-H6, H10, H12): valores puros y el host con el reloj a mano. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class ScreenHostTest {
    @get:Rule val rule = createComposeRule()

    // ---------- Valores puros (H3, H10, H12) ----------

    @Test fun `el eje en 0, 35 y 100 por ciento (H3)`() {
        val start = axisFrame(0f)
        assertEquals(30f, start.topShiftDp, 0.001f)
        assertEquals(0f, start.topAlpha, 0.001f)
        assertEquals(0f, start.underShiftDp, 0.001f)
        assertEquals(1f, start.underAlpha, 0.001f)
        val split = axisFrame(0.35f)
        assertEquals("la que entra aún no se ve a las 35 %", 0f, split.topAlpha, 0.001f)
        assertEquals("la de debajo ya está apagada", 0f, split.underAlpha, 0.001f)
        assertEquals(30f * 0.65f, split.topShiftDp, 0.001f)
        assertEquals(-30f * 0.35f, split.underShiftDp, 0.001f)
        val end = axisFrame(1f)
        assertEquals(0f, end.topShiftDp, 0.001f)
        assertEquals(1f, end.topAlpha, 0.001f)
        assertEquals(-30f, end.underShiftDp, 0.001f)
        assertEquals(0f, end.underAlpha, 0.001f)
        val mid = axisFrame(0.675f)
        assertEquals("aparece entre el 35 y el 100 %", 0.5f, mid.topAlpha, 0.001f)
    }

    @Test fun `el atras predictivo en 0, 35, 100 y cancelado (H10)`() {
        assertEquals(1f to 1f, backFrame(0f))
        val split = backFrame(0.35f)
        assertEquals(1f - 0.10f * 0.35f, split.first, 0.001f)
        assertEquals("se apaga en el primer 35 %", 0f, split.second, 0.001f)
        val end = backFrame(1f)
        assertEquals(0.90f, end.first, 0.001f)
        assertEquals(0f, end.second, 0.001f)
        assertEquals("cancelado (el gesto vuelve a 0) vuelve a su tamaño", 1f to 1f, backFrame(0f))
        assertEquals(0.5f, backFrame(0.175f).second, 0.001f)
    }

    @Test fun `sin animaciones no hay desplazamiento y el alfa es el del fundido (H12)`() {
        listOf(0f, 0.3f, 0.7f, 1f).forEach { p ->
            val f = reducedFrame(p)
            assertEquals(0f, f.topShiftDp, 0f)
            assertEquals(0f, f.underShiftDp, 0f)
            assertEquals(p, f.topAlpha, 0f)
        }
    }

    // ---------- El host ----------

    private var target by mutableStateOf(Screen.MAIN)
    private var backs = 0
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun showHost(motion: Boolean = true, initial: Screen = Screen.MAIN) {
        target = initial
        rule.mainClock.autoAdvance = false
        rule.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CompositionLocalProvider(LocalWindowMotion provides motion) {
                ScreenHost(
                    target = target,
                    onBack = { backs++; target = Screen.MAIN },
                    main = { Box(Modifier.fillMaxSize().testTag("main_content")) },
                    secondary = { screen ->
                        Box(Modifier.fillMaxSize().testTag("bg")) {
                            Box(Modifier.secondaryContent().size(100.dp).testTag("content_$screen"))
                        }
                    },
                )
            }
        }
        advance(16)
    }

    private fun p(): Float = rule.onNode(SemanticsMatcher.keyIsDefined(ScreenProgressKey)).fetchSemanticsNode().config[ScreenProgressKey]()

    /** Avanza el reloj a pasos de un fotograma: de un salto, la recomposición pendiente no llega a hacerse. */
    private fun advance(ms: Long) {
        rule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        var left = ms
        while (left > 0) {
            val dt = minOf(16L, left)
            rule.mainClock.advanceTimeBy(dt)
            left -= dt
        }
    }

    private fun frame(tag: String): LayerFrame = rule.onNodeWithTag(tag).fetchSemanticsNode().config[LayerFrameKey]()

    private fun exists(tag: String) = runCatching { rule.onNodeWithTag(tag).assertExists() }.isSuccess

    private fun advanceUntil(done: () -> Boolean, maxFrames: Int = 300): Int {
        var frames = 0
        while (!done() && frames < maxFrames) {
            rule.mainClock.advanceTimeBy(16)
            frames++
        }
        return frames
    }

    @Test fun `entra con el muelle normal y vuelve con el rapido por el mismo camino (H3)`() {
        showHost()
        target = Screen.SETTINGS
        val opening = advanceUntil({ p() >= 0.995f })
        assertTrue("entró del todo: ${p()}", p() >= 0.995f)
        target = Screen.MAIN
        val closing = advanceUntil({ p() <= 0.005f })
        assertTrue("salió del todo: ${p()}", p() <= 0.005f)
        assertTrue("volver ($closing fotogramas) es más rápido que entrar ($opening)", closing < opening)
    }

    @Test fun `en reposo solo se ve una capa, y la principal queda compuesta pero oculta`() {
        showHost()
        rule.onNodeWithTag("screen_main").assertExists()
        rule.onNodeWithTag("screen_settings").assertDoesNotExist()
        assertEquals(1f, frame("screen_main").alpha, 0f)
        target = Screen.SETTINGS
        advance(64)
        rule.onNodeWithTag("screen_main").assertExists()
        rule.onNodeWithTag("screen_settings").assertExists()
        advanceUntil({ p() >= 0.999f })
        advance(64)
        assertEquals("oculta: componerla de nuevo al volver era el tirón", 0f, frame("screen_main").alpha, 0f)
        assertEquals(1f, frame("screen_settings").alpha, 0.001f)
        rule.onNodeWithTag("main_content").assertDoesNotExist()   // sin accesibilidad mientras está oculta
        target = Screen.MAIN
        advanceUntil({ !exists("screen_settings") })
        rule.onNodeWithTag("screen_settings").assertDoesNotExist()
        rule.onNodeWithTag("screen_main").assertExists()
        assertEquals(1f, frame("screen_main").alpha, 0f)
    }

    @Test fun `las dos capas salen del mismo progreso (H4)`() {
        showHost()
        target = Screen.SETTINGS
        advance(80)
        val p = p()
        val expected = axisFrame(p)
        assertTrue("a medio entrar: $p", p in 0.05f..0.95f)
        assertEquals(expected.topShiftDp, frame("screen_settings").shiftDp, 0.01f)
        assertEquals(expected.topAlpha, frame("screen_settings").alpha, 0.01f)
        assertEquals(expected.underShiftDp, frame("screen_main").shiftDp, 0.01f)
        assertEquals(expected.underAlpha, frame("screen_main").alpha, 0.01f)
    }

    @Test fun `atras a medio entrar sigue desde donde esta, y abrir volver abrir no salta (H4)`() {
        showHost()
        val samples = mutableListOf<Float>()
        fun step(frames: Int) = repeat(frames) { advance(16); samples += p() }
        target = Screen.SETTINGS
        step(6)
        val atFlip = p()
        target = Screen.MAIN
        step(1)
        assertTrue("sigue desde ${atFlip} y no salta a ${p()}", kotlin.math.abs(p() - atFlip) < 0.2f)
        step(2)
        target = Screen.SETTINGS
        step(3)
        target = Screen.MAIN
        step(2)
        target = Screen.SETTINGS
        step(40)
        samples.zipWithNext().forEach { (a, b) ->
            assertTrue("salto de $a a $b", kotlin.math.abs(b - a) < 0.2f)
        }
        assertTrue(p() > 0.99f)
    }

    @Test fun `un arrastre hacia abajo sobre la secundaria no la cierra (H5)`() {
        showHost(initial = Screen.SETTINGS)
        rule.onNodeWithTag("screen_settings").performTouchInput {
            down(Offset(centerX, 40f))
            repeat(20) { moveBy(Offset(0f, 40f), delayMillis = 16) }
            up()
        }
        advance(500)
        assertEquals(1f, p(), 0.001f)
        assertEquals(0, backs)
        rule.onNodeWithTag("screen_settings").assertExists()
        showHostPlacesDrag()
    }

    private fun showHostPlacesDrag() {
        target = Screen.PLACES
        advance(500)
        rule.onNodeWithTag("screen_places").performTouchInput {
            down(Offset(centerX, 40f))
            repeat(20) { moveBy(Offset(0f, 40f), delayMillis = 16) }
            up()
        }
        advance(500)
        assertEquals(1f, p(), 0.001f)
        assertEquals(0, backs)
    }

    @Test fun `el fondo no se desplaza y el contenido si (H6)`() {
        showHost()
        target = Screen.SETTINGS
        advance(48)
        val bg = rule.onNodeWithTag("bg").getUnclippedBoundsInRoot()
        val content = rule.onNodeWithTag("content_${Screen.SETTINGS}").getUnclippedBoundsInRoot()
        assertEquals("el fondo no lleva traslación", 0.dp, bg.left)
        assertTrue("el contenido sí: ${content.left}", content.left > 1.dp)
        advanceUntil({ p() >= 0.999f })
        advance(100)
        assertEquals(0.dp, rule.onNodeWithTag("content_${Screen.SETTINGS}").getUnclippedBoundsInRoot().left)
    }

    @Test fun `sin animaciones funde en 150 ms y no se mueve (H12)`() {
        showHost(motion = false)
        target = Screen.SETTINGS
        advance(16)
        var maxShift = 0f
        repeat(8) {
            advance(16)
            maxShift = maxOf(maxShift, kotlin.math.abs(frame("screen_settings").shiftDp), kotlin.math.abs(frame("screen_main").shiftDp))
        }
        assertEquals(0f, maxShift, 0f)
        val mid = frame("screen_settings").alpha
        assertTrue("a medio fundido: $mid", mid in 0.05f..0.99f)
        advance(200)
        assertEquals(1f, p(), 0.001f)
    }

    @Test fun `atras (flecha del sistema) vuelve por el eje, no por el encogido`() {
        showHost(initial = Screen.SETTINGS)
        rule.runOnUiThread { dispatcher.onBackPressed() }
        advance(32)
        assertEquals(1, backs)
        assertEquals("sin gesto, la hoja no encoge", 1f, frame("screen_settings").scale, 0.001f)
        advanceUntil({ !exists("screen_settings") })
        rule.onNodeWithTag("screen_settings").assertDoesNotExist()
    }

    @Test fun `dos atras seguidos dejan la principal`() {
        showHost(initial = Screen.SETTINGS)
        rule.runOnUiThread {
            dispatcher.onBackPressed()
            dispatcher.onBackPressed()
        }
        advanceUntil({ !exists("screen_settings") })
        assertEquals(Screen.MAIN, target)
        rule.onNodeWithTag("screen_main").assertExists()
        rule.onNodeWithTag("screen_settings").assertDoesNotExist()
    }

    @Test fun `el gesto de atras encoge la secundaria, soltar a medias la devuelve y acabarlo la cierra (H10)`() {
        showHost(initial = Screen.SETTINGS)
        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        advance(32)
        assertEquals(backFrame(0.5f).first, frame("screen_settings").scale, 0.001f)
        assertEquals("apagada pasado el 35 %", 0f, frame("screen_settings").alpha, 0.001f)
        rule.onNodeWithTag("screen_main").assertExists()
        assertEquals(0, backs)
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        advanceUntil({ frame("screen_settings").scale > 0.9995f })
        assertEquals(1f, frame("screen_settings").scale, 0.001f)
        assertEquals(1f, frame("screen_settings").alpha, 0.001f)
        assertEquals(0, backs)
        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.6f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        advanceUntil({ backs == 1 })
        assertEquals(1, backs)
        advanceUntil({ target == Screen.MAIN && runCatching { rule.onNodeWithTag("screen_settings").assertDoesNotExist() }.isSuccess })
        rule.onNodeWithTag("screen_settings").assertDoesNotExist()
    }

    // ---------- Con la app entera (MainScreen) ----------

    private val now = TestData.at(2025, 6, 10, 10, 20)
    private fun content() = UiState.Content(snapshot = TestData.snapshot(now, currentC = 12.0), now = now, offline = false, refreshing = false)
    private val cards = listOf(
        PlaceCardModel(PlaceRef.Here, "Villa Ejemplo", SkyPhoto.CLOUDS, Sky.CLOUDY, 12, 18, 9, null, null, false, true),
        PlaceCardModel(PlaceRef.Saved("sev"), "Sevilla", SkyPhoto.CLEAR_MILD, Sky.CLEAR, 22, 24, 11, null, null, false, false),
    )
    private val selected = mutableListOf<PlaceRef>()

    private fun showApp(initialPlaces: Boolean = false) {
        rule.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, placeCards = cards, onSelectPlace = { selected += it }, initialPlaces = initialPlaces)
        }
    }

    @Test fun `atras en el buscador con texto cierra el buscador y no Sitios`() {
        showApp(initialPlaces = true)
        rule.waitForIdle()
        rule.onNodeWithText("Buscar un sitio").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("sevi")
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("places_screen").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun `atras con la lista en Editar sale de Editar y no de Sitios`() {
        showApp(initialPlaces = true)
        rule.waitForIdle()
        rule.onNodeWithText("Editar").performClick()
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("places_screen").assertIsDisplayed()
        rule.onNodeWithText("Editar").assertExists()
    }

    @Test fun `tocar una postal mientras Sitios aun entra abre ese sitio sin saltos`() {
        rule.mainClock.autoAdvance = false
        showApp()
        advance(100)
        rule.onNode(hasContentDescription("Villa Ejemplo, donde estoy. Cambiar de sitio")).performClick()
        advance(64)
        val before = rule.onNodeWithTag("screen_places").fetchSemanticsNode().config[LayerFrameKey]().alpha
        assertTrue("Sitios aún entra: $before", before < 0.9f)
        rule.onNodeWithText("Sevilla", useUnmergedTree = true).performClick()
        var last = rule.onNode(SemanticsMatcher.keyIsDefined(ScreenProgressKey)).fetchSemanticsNode().config[ScreenProgressKey]()
        var frames = 0
        while (frames < 120 && runCatching { rule.onNodeWithTag("places_screen").assertDoesNotExist() }.isFailure) {
            advance(16)
            val now = rule.onNode(SemanticsMatcher.keyIsDefined(ScreenProgressKey)).fetchSemanticsNode().config[ScreenProgressKey]()
            assertTrue("salto de $last a $now", kotlin.math.abs(now - last) < 0.35f)
            last = now
            frames++
        }
        assertEquals(listOf<PlaceRef>(PlaceRef.Saved("sev")), selected)
        rule.onNodeWithTag("places_screen").assertDoesNotExist()
    }

    /**
     * Lo que se guarda son las banderas; el host se rehace desde ellas con la pantalla que dicen, entera, esté a mitad del
     * paso o no (su progreso nace en 1 si la bandera está puesta).
     */
    @Test fun `recrear la actividad deja la pantalla que dice su bandera, entera`() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone)
        }
        rule.onNodeWithText("Ajustes").performScrollTo().performClick()
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertExists()
        val f = rule.onNodeWithTag("screen_settings").fetchSemanticsNode().config[LayerFrameKey]()
        assertEquals("entera", 1f, f.alpha, 0.001f)
        assertEquals(0f, f.shiftDp, 0.001f)
    }
}
