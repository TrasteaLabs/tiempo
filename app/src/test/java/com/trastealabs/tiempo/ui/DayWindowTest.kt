package com.trastealabs.tiempo.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.DayReliability
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.ModelDay
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.WeatherModel
import com.trastealabs.tiempo.ui.day.ModelDimmedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import com.trastealabs.tiempo.domain.dayPhoto
import com.trastealabs.tiempo.domain.daySummary
import com.trastealabs.tiempo.domain.skyPhotoNow
import com.trastealabs.tiempo.ui.day.DayWindow
import androidx.compose.ui.semantics.SemanticsPropertyKey
import com.trastealabs.tiempo.ui.motion.LocalWindowMotion
import com.trastealabs.tiempo.ui.scene.LightLayer
import com.trastealabs.tiempo.ui.scene.PhotoCache
import com.trastealabs.tiempo.ui.scene.SkyClock

/**
 * La ventana del día (009, H8 y H13), con datos inventados (C5): abrir, deslizar, cerrar (cruz, velo, atrás, arrastre)
 * y lo que llevaba la página del día de la 004 (H4, H5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayWindowTest {
    @get:Rule val rule = createComposeRule()

    private val now = TestData.at(2025, 6, 10, 10, 20)

    /** Viento por horas del martes 10: 8 km/h salvo las 17 (22,2), rachas de 15 salvo las 18 (41,4), del suroeste. */
    private fun withHourly(hour: HourPoint): HourPoint {
        val local = Instant.ofEpochSecond(hour.time).atZone(TestData.zone)
        val h = local.hour
        return hour.copy(
            windKmh = if (h == 17) 22.2 else 8.0,
            windGustsKmh = if (h == 18) 41.4 else 15.0,
            windDirectionDeg = 225,
            humidityPct = 55 + (h * 38) / 23,
            dewPointC = 9.0,
        )
    }

    private fun content(
        now: Long = this.now,
        reliability: Reliability? = null,
        newData: Boolean = true,
        offline: Boolean = false,
    ): UiState.Content {
        val base = TestData.snapshot(now)
        val daily = base.daily.map { withSunAndMoon(it, newData) }
        val snapshot = if (newData) base.copy(hourly = base.hourly.map(::withHourly), daily = daily) else base.copy(daily = daily)
        return UiState.Content(snapshot = snapshot, now = now, offline = offline, refreshing = false, reliability = reliability)
    }

    /**
     * Sol de 06:44 a 21:43 todos los días. Luna (solo con datos nuevos): el martes 10 sale a las 01:44 y se pone a las
     * 15:12; el miércoles 11 no sale y se pone a las 18:39; el jueves 12 se pone a las 10:29 y sale a las 19:52.
     */
    private fun withSunAndMoon(day: DayPoint, newData: Boolean): DayPoint {
        val date = LocalDate.parse(day.date)
        fun at(h: Int, m: Int) = TestData.at(date.year, date.monthValue, date.dayOfMonth, h, m)
        val withSun = day.copy(sunrise = at(6, 44), sunset = at(21, 43), daylightSeconds = 53_940.0)
        if (!newData) return withSun
        val (rise, set) = when (date.dayOfMonth) {
            10 -> at(1, 44) to at(15, 12)
            11 -> null to at(18, 39)
            12 -> at(19, 52) to at(10, 29)
            else -> at(3, 0) to at(16, 0)
        }
        return withSun.copy(moonrise = rise, moonset = set, moonPhase = 0.84)
    }

    private fun relDay(date: String, scenarios: Int = 122, wet: Int = 0, models: List<ModelDay> = emptyList()) =
        DayReliability(date, scenarios, wet, maxLowC = 17.0, maxHighC = 19.0, models = models)

    /** Martes 10: seis modelos que coinciden. Miércoles 11: tres modelos. Jueves 12: el francés se aparta. Jueves 19: 15 escenarios. */
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

    private fun show(state: UiState, initialDayDate: String? = null) =
        rule.setContent { MainScreen(state, {}, {}, {}, initialDayDate = initialDayDate) }

    private fun page(date: String) = hasAnyAncestor(hasTestTag("day_page_$date"))

    private fun inPage(date: String, matcher: SemanticsMatcher) = rule.onNode(matcher and page(date))

    private fun dayRow(i: Int) = rule.onAllNodesWithTag("day")[i]

    private fun texts(node: SemanticsNode): String =
        (node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }).joinToString(" ")

    private fun swipe(left: Boolean) {
        rule.onNodeWithTag("day_pager").performTouchInput { if (left) swipeLeft() else swipeRight() }
        rule.waitForIdle()
    }

    // ---------- H4 · abrir, deslizar, volver ----------

    @Test fun `tocar una fila sin fiabilidad abre ese dia`() {
        show(content())
        dayRow(1).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("day_window").assertIsDisplayed()
        rule.onNodeWithText("Miércoles, 11 de junio").assertIsDisplayed()
        // La principal sigue compuesta debajo, con el velo encima.
        rule.onNodeWithTag("main_scroll").assertExists()
    }

    @Test fun `tocar una fila con fiabilidad abre ese dia`() {
        show(content(reliability = reliability))
        dayRow(2).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Jueves, 12 de junio").assertIsDisplayed()
    }

    @Test fun `el panel y su pildora abren hoy`() {
        show(content(reliability = reliability))
        rule.onNodeWithTag("main_panel").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Martes, 10 de junio").assertIsDisplayed()
        inPage("2025-06-10", hasText("Hoy · Villa Ejemplo")).assertIsDisplayed()

        clickScrim(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("reliability_pill", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Martes, 10 de junio").assertIsDisplayed()
    }

    @Test fun `el panel lleva Abre el dia de hoy para TalkBack`() {
        show(content())
        val node = rule.onNodeWithTag("main_panel").fetchSemanticsNode()
        assertEquals("Abre el día de hoy", node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label)
        // La cifra se lee entera como «12 grados» (019): el «°» suelto ya no es parte de lo que oye TalkBack.
        assertTrue(texts(node), texts(node).contains("12 grados"))
    }

    @Test fun `deslizar a la izquierda pasa al siguiente y a la derecha al anterior`() {
        show(content(), initialDayDate = "2025-06-11")
        rule.onNodeWithText("Miércoles, 11 de junio").assertIsDisplayed()
        swipe(left = true)
        rule.onNodeWithText("Jueves, 12 de junio").assertIsDisplayed()
        swipe(left = false)
        swipe(left = false)
        rule.onNodeWithText("Martes, 10 de junio").assertIsDisplayed()
    }

    @Test fun `no pasa de hoy ni del decimo`() {
        show(content(), initialDayDate = "2025-06-10")
        swipe(left = false)
        rule.onNodeWithText("Martes, 10 de junio").assertIsDisplayed()
    }

    @Test fun `no pasa del decimo dia`() {
        show(content(), initialDayDate = "2025-06-19")
        rule.onNodeWithText("Jueves, 19 de junio").assertIsDisplayed()
        swipe(left = true)
        rule.onNodeWithText("Jueves, 19 de junio").assertIsDisplayed()
        // La tira se ha movido para dejar el décimo en el centro.
        rule.onNode(hasTestTag("day_chip") and hasText("19")).assertIsDisplayed().assertIsSelected()
    }

    @Test fun `el velo cierra la ventana`() {
        show(content(), initialDayDate = "2025-06-12")
        clickScrim(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("day_window").assertDoesNotExist()
        rule.onNodeWithTag("main_scroll").assertExists()
    }

    @Test fun `el gesto de volver vuelve a la principal`() {
        lateinit var back: OnBackPressedDispatcher
        rule.setContent {
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            MainScreen(content(), {}, {}, {}, initialDayDate = "2025-06-12")
        }
        rule.onNodeWithTag("day_window").assertIsDisplayed()
        pressSystemBack(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("day_window").assertDoesNotExist()
        rule.onNodeWithTag("main_scroll").assertExists()
    }

    @Test fun `tocar la tira salta a ese dia y lo marca`() {
        show(content(), initialDayDate = "2025-06-10")
        rule.onAllNodesWithTag("day_chip")[0].assertIsSelected()
        rule.onAllNodesWithTag("day_chip")[3].performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Viernes, 13 de junio").assertIsDisplayed()
        rule.onAllNodesWithTag("day_chip")[3].assertIsSelected()
        assertTrue(texts(rule.onAllNodesWithTag("day_chip")[0].fetchSemanticsNode()).startsWith("Hoy"))
        assertTrue(texts(rule.onAllNodesWithTag("day_chip")[1].fetchSemanticsNode()), texts(rule.onAllNodesWithTag("day_chip")[1].fetchSemanticsNode()) == "X 11")
    }

    @Test fun `la demo abre el dia n`() {
        show(content(), initialDayDate = "2025-06-14")
        rule.onNodeWithText("Sábado, 14 de junio").assertIsDisplayed()
        inPage("2025-06-14", hasText("Dentro de 4 días · Villa Ejemplo")).assertIsDisplayed()
    }

    // ---------- H5 · secciones ----------

    private fun top(date: String, tag: String): Float =
        inPage(date, hasTestTag(tag)).fetchSemanticsNode().positionInRoot.y

    @Test fun `las secciones van en el orden de H5 con sus cifras`() {
        show(content(reliability = reliability), initialDayDate = "2025-06-10")
        val d = "2025-06-10"
        val order = listOf("day_band", "day_date", "day_panel", "day_summary", "day_hours", "day_wind_humidity", "day_sun_moon", "day_models")
            .map { top(d, it) }
        assertEquals(order.sorted(), order)

        inPage(d, hasText("Martes, 10 de junio")).assertIsDisplayed()
        inPage(d, hasText("Hoy · Villa Ejemplo")).assertIsDisplayed()
        inPage(d, hasText("18°") and hasAnyAncestor(hasTestTag("day_panel"))).assertIsDisplayed()
        inPage(d, hasText("Nublado")).assertExists()
        inPage(d, hasText("9°") and hasContentDescription("Mín 9°")).assertIsDisplayed()
        inPage(d, hasText("Lluvia 30 % · 1,5 mm")).assertIsDisplayed()
        inPage(d, hasText("Resumen del día")).assertExists()
        inPage(d, hasText("Hora a hora")).assertExists()
        assertEquals(24, rule.onAllNodes(hasTestTag("hour") and page(d)).fetchSemanticsNodes().size)

        inPage(d, hasTestTag("day_wind_humidity")).performScrollTo()
        inPage(d, hasText("Viento y humedad")).assertIsDisplayed()
        inPage(d, hasText("22 km/h")).assertIsDisplayed()
        inPage(d, hasText("Suroeste · 17:00")).assertIsDisplayed()
        inPage(d, hasText("Rachas de 41")).assertIsDisplayed()
        inPage(d, hasTestTag("wind_arrow")).assertExists()
        inPage(d, hasText("55–93 %")).assertIsDisplayed()

        inPage(d, hasTestTag("day_models")).performScrollTo()
        inPage(d, hasText("Modelos")).assertIsDisplayed()
        inPage(d, hasText("Fiabilidad muy alta") and hasAnyAncestor(hasTestTag("day_models"))).assertExists()
        inPage(d, hasTestTag("model_board")).assertExists()
    }

    @Test fun `sin fiabilidad no sale Modelos y el resto si`() {
        show(content(), initialDayDate = "2025-06-11")
        val d = "2025-06-11"
        listOf("day_date", "day_panel", "day_summary", "day_hours", "day_wind_humidity", "day_sun_moon").forEach { inPage(d, hasTestTag(it)).assertExists() }
        inPage(d, hasTestTag("day_models")).assertDoesNotExist()
        rule.onNodeWithText("Fiabilidad", substring = true).assertDoesNotExist()
    }

    @Test fun `un dia sin nivel no lleva Modelos ni pildora`() {
        show(content(reliability = reliability), initialDayDate = "2025-06-19")
        val d = "2025-06-19"
        inPage(d, hasTestTag("day_panel")).assertExists()
        inPage(d, hasTestTag("day_models")).assertDoesNotExist()
        inPage(d, hasText("Fiabilidad", substring = true)).assertDoesNotExist()
    }

    @Test fun `con datos de antes no sale Viento y humedad, y Sol y luna solo con el sol`() {
        show(content(newData = false, reliability = reliability), initialDayDate = "2025-06-10")
        val d = "2025-06-10"
        inPage(d, hasTestTag("day_wind_humidity")).assertDoesNotExist()
        listOf("day_date", "day_panel", "day_hours", "day_sun_moon", "day_models").forEach { inPage(d, hasTestTag(it)).assertExists() }
        inPage(d, hasTestTag("day_sun_row")).assertExists()
        inPage(d, hasText("06:44 – 21:43")).assertExists()
        inPage(d, hasTestTag("day_moon_row")).assertDoesNotExist()
        inPage(d, hasText("Menguante", substring = true)).assertDoesNotExist()
    }

    // ---------- H5.6 · Sol y luna (P3) ----------

    @Test fun `Sol y luna lleva las horas del sol, la luz y la luna con su fase`() {
        show(content(), initialDayDate = "2025-06-10")
        val d = "2025-06-10"
        inPage(d, hasTestTag("day_sun_moon")).performScrollTo()
        inPage(d, hasText("Sol y luna")).assertIsDisplayed()
        inPage(d, hasTestTag("day_line")).assertExists()
        inPage(d, hasText("06:44 – 21:43")).assertIsDisplayed()
        inPage(d, hasText("14 h 59 min de luz")).assertIsDisplayed()
        inPage(d, hasText("Sale 01:44 · se pone 15:12")).assertIsDisplayed()
        inPage(d, hasText("Menguante · 23 %")).assertIsDisplayed()
    }

    @Test fun `un dia en que la luna no sale lo dice (caso limite)`() {
        show(content(), initialDayDate = "2025-06-11")
        inPage("2025-06-11", hasText("No sale este día · se pone 18:39")).assertExists()
    }

    @Test fun `si la luna se pone antes de salir, en el orden del dia`() {
        show(content(), initialDayDate = "2025-06-12")
        inPage("2025-06-12", hasText("Se pone 10:29 · sale 19:52")).assertExists()
    }

    @Test fun `en hoy Ahora va marcado y las horas pasadas atenuadas`() {
        show(content(), initialDayDate = "2025-06-10")
        val hours = rule.onAllNodes(hasTestTag("hour") and page("2025-06-10")).fetchSemanticsNodes()
        // 10:20: la columna de las 10 es «Ahora», con la temperatura de la cabecera (12°).
        assertTrue(texts(hours[10]), texts(hours[10]).startsWith("Ahora, 12 grados"))
        assertTrue(texts(hours[0]), texts(hours[0]).startsWith("00"))
        assertEquals(10, hours.count { it.config.getOrElse(PastHourKey) { false } })
        assertTrue(hours.drop(10).none { it.config.getOrElse(PastHourKey) { false } })
    }

    @Test fun `otro dia no lleva Ahora ni horas atenuadas`() {
        show(content(), initialDayDate = "2025-06-11")
        val hours = rule.onAllNodes(hasTestTag("hour") and page("2025-06-11")).fetchSemanticsNodes()
        assertEquals(24, hours.size)
        assertTrue(hours.none { texts(it).contains("Ahora") })
        assertTrue(hours.none { it.config.getOrElse(PastHourKey) { false } })
    }

    @Test fun `la nota del cambio de hora solo sale esos dos dias`() {
        val autumn = TestData.at(2026, 10, 24, 12, 0)
        show(content(now = autumn), initialDayDate = "2026-10-25")
        inPage("2026-10-25", hasText("Esta noche se atrasa la hora: las 02 salen dos veces")).assertExists()
        assertEquals(25, rule.onAllNodes(hasTestTag("hour") and page("2026-10-25")).fetchSemanticsNodes().size)
        inPage("2026-10-24", hasTestTag("day_dst_note")).assertDoesNotExist()
        inPage("2026-10-26", hasTestTag("day_dst_note")).assertDoesNotExist()
    }

    @Test fun `al adelantar la hora la nota dice de las 01 a las 03`() {
        val spring = TestData.at(2027, 3, 27, 12, 0)
        show(content(now = spring), initialDayDate = "2027-03-28")
        inPage("2027-03-28", hasText("Esta noche se adelanta la hora: de las 01 se pasa a las 03")).assertExists()
        assertEquals(23, rule.onAllNodes(hasTestTag("hour") and page("2027-03-28")).fetchSemanticsNodes().size)
    }

    // ---------- casos límite ----------

    @Test fun `pasada la medianoche con la pagina abierta sigue en la misma fecha`() {
        var state by mutableStateOf(content())
        rule.setContent { MainScreen(state, {}, {}, {}, initialDayDate = "2025-06-12") }
        rule.onNodeWithText("Jueves, 12 de junio").assertIsDisplayed()
        state = content(now = TestData.at(2025, 6, 11, 0, 30))
        rule.waitForIdle()
        rule.onNodeWithText("Jueves, 12 de junio").assertIsDisplayed()
        inPage("2025-06-12", hasText("Mañana · Villa Ejemplo")).assertIsDisplayed()
    }

    @Test fun `si la fecha abierta deja de estar, va a la primera`() {
        var state by mutableStateOf(content())
        rule.setContent { MainScreen(state, {}, {}, {}, initialDayDate = "2025-06-10") }
        rule.onNodeWithText("Martes, 10 de junio").assertIsDisplayed()
        state = content(now = TestData.at(2025, 6, 11, 0, 30))
        rule.waitForIdle()
        rule.onNodeWithText("Miércoles, 11 de junio").assertIsDisplayed()
        inPage("2025-06-11", hasText("Hoy · Villa Ejemplo")).assertIsDisplayed()
    }

    @Test fun `sin conexion se abre igual`() {
        show(content(offline = true), initialDayDate = "2025-06-13")
        rule.onNodeWithText("Viernes, 13 de junio").assertIsDisplayed()
        inPage("2025-06-13", hasTestTag("day_hours")).assertExists()
    }

    // ---------- «Modelos» (lo que llevaba la hoja del 003) ----------

    @Test fun `Modelos lleva nivel, marcador y solo los modelos que llegan`() {
        show(content(reliability = reliability), initialDayDate = "2025-06-11")
        val d = "2025-06-11"
        inPage(d, hasTestTag("day_models")).performScrollTo()
        inPage(d, hasText("Fiabilidad media") and hasAnyAncestor(hasTestTag("day_models"))).assertIsDisplayed()
        inPage(d, hasTestTag("model_board")).assertExists()
        inPage(d, hasText("Lo que te enseño")).assertExists()
        inPage(d, hasText("La previsión de siempre")).assertExists()
        assertEquals(3, rule.onAllNodes(hasTestTag("model_card") and page(d)).fetchSemanticsNodes().size)
        listOf("Europeo", "Americano", "Canadiense").forEach { inPage(d, hasText(it)).assertExists() }
        listOf("Alemán", "Francés", "Británico").forEach { inPage(d, hasText(it)).assertDoesNotExist() }
        // Coinciden: ninguna atenuada y sin leyenda (H9).
        val cards = rule.onAllNodes(hasTestTag("model_card") and page(d)).fetchSemanticsNodes()
        assertTrue(cards.none { it.config.getOrElse(ModelDimmedKey) { false } })
        inPage(d, hasTestTag("models_dimmed_legend")).assertDoesNotExist()
        inPage(d, hasText("Atenuado: se aparta del resto")).assertDoesNotExist()
    }

    @Test fun `el marcador lleva sus cifras y TalkBack lo lee como frase (H10)`() {
        show(content(reliability = reliability), initialDayDate = "2025-06-12")
        val d = "2025-06-12"
        inPage(d, hasTestTag("model_board")).performScrollTo()
        inPage(
            d,
            hasTestTag("model_board") and hasContentDescription(
                "5 modelos dan el día seco y 1 con lluvia. Máximas de 17 a 20 grados. De 122 escenarios, el 72 % salen secos.",
            ),
        ).assertIsDisplayed()
        // Sin frase de antes: el texto de los modelos ya no sale.
        inPage(d, hasText("El francés", substring = true)).assertDoesNotExist()
    }

    @Test fun `si un modelo se aparta sale atenuado y con la leyenda (H9)`() {
        show(content(reliability = reliability), initialDayDate = "2025-06-12")
        val d = "2025-06-12"
        val cards = rule.onAllNodes(hasTestTag("model_card") and page(d)).fetchSemanticsNodes()
        assertEquals(6, cards.size)
        val dimmed = cards.filter { it.config.getOrElse(ModelDimmedKey) { false } }
        assertEquals(1, dimmed.size)
        assertTrue(texts(dimmed.single()), texts(dimmed.single()).contains("Francés"))
        inPage(d, hasText("Atenuado: se aparta del resto")).assertExists()
    }

    @Test fun `si van divididos no se atenua ninguno ni sale la leyenda (H9)`() {
        val split = listOf(
            ModelDay(WeatherModel.ECMWF, 61, 20.0, 3.4), ModelDay(WeatherModel.ICON, 2, 23.0, 0.2), ModelDay(WeatherModel.GFS, 61, 18.0, 6.1),
            ModelDay(WeatherModel.UKMO, 61, 21.0, 1.2), ModelDay(WeatherModel.GEM, 2, 22.0, 0.4),
        )
        val rel = reliability.copy(days = reliability.days.map { if (it.date == "2025-06-13") relDay(it.date, wet = 66, models = split) else it })
        show(content(reliability = rel), initialDayDate = "2025-06-13")
        val d = "2025-06-13"
        inPage(d, hasTestTag("day_models")).assertExists()
        val cards = rule.onAllNodes(hasTestTag("model_card") and page(d)).fetchSemanticsNodes()
        assertEquals(5, cards.size)
        assertTrue(cards.none { it.config.getOrElse(ModelDimmedKey) { false } })
        inPage(d, hasTestTag("models_dimmed_legend")).assertDoesNotExist()
    }

    @Test fun `si la fiabilidad deja de valer la seccion desaparece`() {
        var state by mutableStateOf(content(reliability = reliability))
        rule.setContent { MainScreen(state, {}, {}, {}, initialDayDate = "2025-06-12") }
        inPage("2025-06-12", hasTestTag("day_models")).assertExists()
        state = content()
        rule.waitForIdle()
        inPage("2025-06-12", hasTestTag("day_models")).assertDoesNotExist()
        rule.onNodeWithText("Jueves, 12 de junio").assertIsDisplayed()
    }


    // ---------- 009 · H8 y H13: la ventana flotante ----------

    private var closes = 0
    private val dates = mutableListOf<String>()

    /** La ventana sola, como la monta la principal. */
    private fun showWindow(
        date: String = "2025-06-12",
        motion: Boolean = true,
        state: UiState.Content = content(),
    ) = rule.setContent {
        val context = LocalContext.current
        val photos = remember { PhotoCache(context) }
        var shown by remember { mutableStateOf(date) }
        CompositionLocalProvider(LocalWindowMotion provides motion) {
            DayWindow(
                state, shown,
                onDateChange = { shown = it; dates += it },
                onClose = { closes++ },
                photos = photos,
            )
        }
    }

    @Test fun `la frase de la IA de un dia sustituye a la de reglas y el dia sin frase usa la suya (016 P4, H2)`() {
        val report = com.trastealabs.tiempo.domain.AiReport(
            now, com.trastealabs.tiempo.domain.AiProvider.CLAUDE, com.trastealabs.tiempo.domain.ReportBasis("2025-06-10", 20, 10, false),
            days = mapOf("2025-06-12" to "Jueves entre 9° y 21°. Sin lluvia."),
        )
        showWindow("2025-06-12", state = content().copy(report = report))
        inPage("2025-06-12", hasTestTag("day_summary")).assertExists()
        rule.onNodeWithText("Jueves entre 9° y 21°. Sin lluvia.").assertIsDisplayed()
    }

    @Test fun `el dia sin frase de la IA enseña la de reglas (016 P4, H2)`() {
        val report = com.trastealabs.tiempo.domain.AiReport(
            now, com.trastealabs.tiempo.domain.AiProvider.CLAUDE, com.trastealabs.tiempo.domain.ReportBasis("2025-06-10", 20, 10, false),
            days = mapOf("2025-06-12" to "Jueves entre 9° y 21°. Sin lluvia."),
        )
        val state = content().copy(report = report)
        val rules = daySummary(state.snapshot, "2025-06-13", state.now, TestData.zone)
        assertTrue(rules.isNotEmpty())
        showWindow("2025-06-13", state = state)
        inPage("2025-06-13", hasText(rules)).assertIsDisplayed()
    }

    @Test fun `abre en la fecha pedida y la tira la marca`() {
        showWindow("2025-06-13")
        rule.onNodeWithText("Viernes, 13 de junio").assertIsDisplayed()
        rule.onNode(hasTestTag("day_chip") and hasText("13")).assertIsSelected()
    }

    @Test fun `deslizar cambia de dia y avisa con onDateChange`() {
        showWindow("2025-06-12")
        swipe(left = true)
        rule.onNodeWithText("Viernes, 13 de junio").assertIsDisplayed()
        assertEquals("2025-06-13", dates.last())
        rule.onNode(hasTestTag("day_chip") and hasText("13")).assertIsSelected()
    }

    @Test fun `el velo llama a onClose al acabar de cerrarse`() {
        showWindow()
        clickScrim(rule)
        rule.waitForIdle()
        assertEquals(1, closes)
    }

    /** 012 P4: la ventana del día es opaca (sin cristal que siga a la hoja): ya no hay trampa de `followMoves`. */
    @Test fun `la ventana del dia es opaca, sin cristal que siga a la hoja`() {
        val source = java.io.File("src/main/java/com/trastealabs/tiempo/ui/day/DayWindow.kt").readText()
        assertTrue(source.contains("CristalColors.SheetSurface") && !source.contains("followMoves"))
    }

    /** 012 P6: la escena de debajo no pide fotogramas, así que ya no hay que pararla con la ventana abierta. */
    @Test fun `la ventana ya no para el cielo de debajo (escena quieta)`() {
        val source = java.io.File("src/main/java/com/trastealabs/tiempo/ui/day/DayWindow.kt").readText()
        assertTrue(!source.contains("SkyCover") && !source.contains("SheetSkyCover"))
    }

    @Test fun `las paginas de al lado quedan preparadas enteras`() {
        showWindow("2025-06-12")
        rule.waitForIdle()
        // Compuestas por adelantado y por tramos (P5, H9): al deslizar ya tienen la gráfica y lo de abajo.
        listOf("2025-06-11", "2025-06-13").forEach { d ->
            listOf("day_band", "day_summary", "day_hours", "day_sun_moon").forEach { inPage(d, hasTestTag(it)).assertExists() }
        }
    }

    @Test fun `saltar a un dia lejano desde la tira lo pinta entero en el primer fotograma`() {
        showWindow("2025-06-12")
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNode(hasTestTag("day_chip") and hasText("16")).performClick()
        rule.mainClock.advanceTimeByFrame()
        listOf("day_band", "day_summary", "day_hours").forEach { inPage("2025-06-16", hasTestTag(it)).assertExists() }
    }

    @Test fun `tocar el velo cierra`() {
        showWindow()
        clickScrim(rule)
        rule.waitForIdle()
        assertEquals(1, closes)
    }

    @Test fun `atras cierra la ventana`() {
        showWindow()
        rule.waitForIdle()
        pressSystemBack(rule)
        rule.waitForIdle()
        assertEquals(1, closes)
    }

    @Test fun `el asa de M3 cierra la ventana`() {
        showWindow()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Drag handle")).performClick()
        rule.waitForIdle()
        assertEquals(1, closes)
    }

    @Test fun `la franja lleva la foto del dia y hoy la de ahora`() {
        val state = content()
        showWindow("2025-06-12", state = state)
        val other = dayPhoto(state.snapshot, "2025-06-12", state.now)
        inPage("2025-06-12", hasTestTag("day_band_photo_${other.file}")).assertExists()
        swipe(left = false)
        swipe(left = false)
        inPage("2025-06-10", hasTestTag("day_band_photo_${skyPhotoNow(state.snapshot, state.now).file}")).assertExists()
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `la franja va quieta, sin escena ni capa de luz`() {
        var calls = 0
        LightLayer.drawProbe = { calls++ }
        try {
            var view: android.view.View? = null
            rule.setContent {
                view = androidx.compose.ui.platform.LocalView.current
                val context = LocalContext.current
                DayWindow(content(), "2025-06-12", onDateChange = {}, onClose = {}, photos = remember { PhotoCache(context) })
            }
            rule.runOnIdle {
                val root = checkNotNull(view).rootView
                root.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)))
            }
            // La de la página que se ve: las de al lado también están compuestas, por adelantado (P5).
            rule.onNode(hasTestTag("day_band") and hasAnyAncestor(hasTestTag("day_page_2025-06-12"))).assertExists()
            rule.onNodeWithTag("sky-moving").assertDoesNotExist()
            rule.onNodeWithTag("sky-still").assertDoesNotExist()
            assertEquals("la ventana pinta capa de luz", 0, calls)
        } finally {
            LightLayer.drawProbe = null
        }
    }

    @Test fun `cambiar de dia con el paginador y desplazar no cierran la ventana`() {
        showWindow("2025-06-12")
        rule.waitForIdle()
        swipe(left = true)
        assertEquals("el pase de lado cambia de día", "2025-06-13", dates.last())
        assertEquals(0, closes)
        val changes = dates.size
        // Desplazar hacia arriba (el dedo sube) empezando sobre el paginador: la lista se mueve y no se cierra.
        rule.onNodeWithTag("day_pager").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(0, closes)
        // Y, con la lista ya desplazada, bajar el dedo un poco la devuelve hacia arriba sin cerrar la hoja.
        rule.onNodeWithTag("day_pager").performTouchInput { swipeDown(startY = top + 100f, endY = top + 300f, durationMillis = 400) }
        rule.waitForIdle()
        assertEquals(0, closes)
        // Y desde la tira de días.
        rule.onNodeWithTag("day_strip").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(0, closes)
        // Un arrastre vertical despacio y quieto al soltar, sobre el paginador: ni cambia de día ni cierra.
        rule.onNodeWithTag("day_pager").performTouchInput {
            down(Offset(centerX, centerY))
            repeat(10) { moveBy(Offset(0f, -2f * density), delayMillis = 16) }
            moveBy(Offset.Zero, delayMillis = 200)
            up()
        }
        rule.waitForIdle()
        assertEquals("el vertical no cambia de día", changes, dates.size)
        rule.onNodeWithText("Viernes, 13 de junio").assertExists()
        assertEquals(0, closes)
    }

    @Test fun `lleva Resumen del dia con su frase`() {
        val state = content()
        showWindow("2025-06-12", state = state)
        val text = daySummary(state.snapshot, "2025-06-12", state.now, TestData.zone)
        assertTrue(text.isNotEmpty())
        inPage("2025-06-12", hasText("Resumen del día")).assertExists()
        inPage("2025-06-12", hasText(text)).assertExists()
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `con la ventana abierta solo pinta movimiento la principal`() {
        val drawn = mutableSetOf<LightLayer>()
        LightLayer.drawProbe = { drawn += it }
        try {
            var view: android.view.View? = null
            rule.setContent {
                view = androidx.compose.ui.platform.LocalView.current
                MainScreen(content(), {}, {}, {}, initialDayDate = "2025-06-12", skyClock = SkyClock())
            }
            rule.runOnIdle {
                val root = checkNotNull(view).rootView
                root.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)))
            }
            assertTrue("pintan movimiento ${drawn.size} capas", drawn.size <= 1)
        } finally {
            LightLayer.drawProbe = null
        }
    }

    @Test fun `cerrar vuelve a la principal donde estaba`() {
        show(content())
        rule.onNodeWithTag("main_scroll").performTouchInput { swipeUp() }
        rule.waitForIdle()
        dayRow(3).performScrollTo()
        val before = rule.onAllNodesWithTag("day")[3].fetchSemanticsNode().positionInRoot.y
        dayRow(3).performClick()
        rule.waitForIdle()
        swipe(left = true)
        clickScrim(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("day_window").assertDoesNotExist()
        assertEquals(before, rule.onAllNodesWithTag("day")[3].fetchSemanticsNode().positionInRoot.y, 1f)
    }
}
