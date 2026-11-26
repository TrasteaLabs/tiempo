package com.trastealabs.tiempo.ui.theme

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.data.PlaceHit
import com.trastealabs.tiempo.data.SearchResults
import com.trastealabs.tiempo.domain.AirHour
import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.ApiUsage
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.Scene
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WarningLevel
import com.trastealabs.tiempo.ui.DaysSheetKind
import com.trastealabs.tiempo.ui.HourlySheetKind
import com.trastealabs.tiempo.ui.MainScreen
import com.trastealabs.tiempo.ui.SearchState
import com.trastealabs.tiempo.ui.SearchUi
import com.trastealabs.tiempo.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * La letra de Cristal 1.2 (010, H2): todo texto de la app lleva Google Sans Flex con un rol de la escala de M3 (o una
 * de las tres cifras propias), pesos de 300 a 700, el 300 solo desde 40 sp, sin medios puntos, sin espaciado y sin
 * mayúsculas. Se recorre cada pantalla con el texto del sistema al 100 %.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1800dp")
class CristalTypeTest {
    @get:Rule val rule = createComposeRule()

    private val now = TestData.at(2025, 6, 10, 10, 20)

    private data class Metrics(val size: Float, val line: Float, val weight: Int)

    private fun TextStyle.metrics() = Metrics(fontSize.value, lineHeight.value, fontWeight?.weight ?: -1)

    private val scale: Map<String, Metrics> = mapOf(
        "displayLarge" to CristalType.displayLarge.metrics(),
        "displayMedium" to CristalType.displayMedium.metrics(),
        "displaySmall" to CristalType.displaySmall.metrics(),
        "headlineLarge" to CristalType.headlineLarge.metrics(),
        "headlineMedium" to CristalType.headlineMedium.metrics(),
        "headlineSmall" to CristalType.headlineSmall.metrics(),
        "titleLarge" to CristalType.titleLarge.metrics(),
        "titleMedium" to CristalType.titleMedium.metrics(),
        "titleSmall" to CristalType.titleSmall.metrics(),
        "bodyLarge" to CristalType.bodyLarge.metrics(),
        "bodyMedium" to CristalType.bodyMedium.metrics(),
        "bodySmall" to CristalType.bodySmall.metrics(),
        "labelLarge" to CristalType.labelLarge.metrics(),
        "labelMedium" to CristalType.labelMedium.metrics(),
        "labelSmall" to CristalType.labelSmall.metrics(),
        "labelLarge16" to CristalType.labelLargeBig.metrics(),
        "mainTemp" to AppFigures.mainTemp.metrics(),
        "hereTemp" to AppFigures.hereTemp.metrics(),
        "usageFigure" to AppFigures.usageFigure.metrics(),
    )

    private val acronyms = setOf("AEMET", "NASA", "UV", "CAMS", "OFL")

    // ---------- la escala en sí (§4 de cristal.md) ----------

    @Test fun `los quince roles de CristalType tienen los numeros de la tabla de 4`() {
        data class Row(val style: TextStyle, val size: Int, val line: Int, val weight: Int)
        val rows = listOf(
            Row(CristalType.displayLarge, 57, 64, 300), Row(CristalType.displayMedium, 45, 52, 400),
            Row(CristalType.displaySmall, 36, 44, 400), Row(CristalType.headlineLarge, 32, 40, 700),
            Row(CristalType.headlineMedium, 28, 36, 700), Row(CristalType.headlineSmall, 24, 32, 500),
            Row(CristalType.titleLarge, 22, 28, 500), Row(CristalType.titleMedium, 16, 24, 600),
            Row(CristalType.titleSmall, 14, 20, 500), Row(CristalType.bodyLarge, 16, 24, 400),
            Row(CristalType.bodyMedium, 14, 20, 400), Row(CristalType.bodySmall, 12, 16, 400),
            Row(CristalType.labelLarge, 14, 20, 500), Row(CristalType.labelMedium, 12, 16, 500),
            Row(CristalType.labelSmall, 11, 16, 500),
        )
        for (r in rows) {
            assertEquals(Metrics(r.size.toFloat(), r.line.toFloat(), r.weight), r.style.metrics())
            assertEquals(SansFlex, r.style.fontFamily)
            assertEquals(0f, r.style.letterSpacing.value, 0f)
        }
        assertEquals(Metrics(16f, 24f, 500), CristalType.labelLargeBig.metrics())
        assertEquals(Metrics(112f, 112f, 300), AppFigures.mainTemp.metrics())
        assertEquals(Metrics(68f, 70f, 300), AppFigures.hereTemp.metrics())
        assertEquals(Metrics(64f, 68f, 300), AppFigures.usageFigure.metrics())
    }

    @Test fun `la Typography de Material 3 lleva los mismos quince roles`() {
        val t = cristalTypography
        assertEquals(CristalType.displayLarge.metrics(), t.displayLarge.metrics())
        assertEquals(CristalType.displayMedium.metrics(), t.displayMedium.metrics())
        assertEquals(CristalType.displaySmall.metrics(), t.displaySmall.metrics())
        assertEquals(CristalType.headlineLarge.metrics(), t.headlineLarge.metrics())
        assertEquals(CristalType.headlineMedium.metrics(), t.headlineMedium.metrics())
        assertEquals(CristalType.headlineSmall.metrics(), t.headlineSmall.metrics())
        assertEquals(CristalType.titleLarge.metrics(), t.titleLarge.metrics())
        assertEquals(CristalType.titleMedium.metrics(), t.titleMedium.metrics())
        assertEquals(CristalType.titleSmall.metrics(), t.titleSmall.metrics())
        assertEquals(CristalType.bodyLarge.metrics(), t.bodyLarge.metrics())
        assertEquals(CristalType.bodyMedium.metrics(), t.bodyMedium.metrics())
        assertEquals(CristalType.bodySmall.metrics(), t.bodySmall.metrics())
        assertEquals(CristalType.labelLarge.metrics(), t.labelLarge.metrics())
        assertEquals(CristalType.labelMedium.metrics(), t.labelMedium.metrics())
        assertEquals(CristalType.labelSmall.metrics(), t.labelSmall.metrics())
    }

    // ---------- lo que pinta la app ----------

    /** Cada texto de la pantalla (árbol sin unir): su cadena y el estilo con el que se pinta. */
    private fun texts(): List<Pair<String, TextStyle>> {
        rule.waitForIdle()
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        return nodes.mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.firstOrNull()?.let { it.layoutInput.text.text to it.layoutInput.style }
        }
    }

    private fun assertLetter(screen: String, minTexts: Int = 5) {
        val all = texts()
        assertTrue("$screen: solo ${all.size} textos", all.size >= minTexts)
        val problems = mutableListOf<String>()
        for ((text, style) in all) {
            val label = "«${text.take(40)}» (${style.fontSize}/${style.lineHeight}, peso ${style.fontWeight?.weight}, familia ${style.fontFamily})"
            if (style.fontFamily != SansFlex) problems += "$label no lleva SansFlex"
            val m = style.metrics()
            if (scale.values.none { it == m }) problems += "$label no es ningún rol de la escala"
            val weight = style.fontWeight?.weight
            if (weight == null || weight !in listOf(300, 400, 500, 600, 700)) problems += "$label tiene un peso fuera de 300 a 700"
            if (weight == 300 && style.fontSize.value < 40f) problems += "$label es 300 con menos de 40 sp"
            if (style.fontSize.value % 1f != 0f || style.lineHeight.value % 1f != 0f) problems += "$label tiene medio punto"
            if (style.letterSpacing.value != 0f) problems += "$label tiene espaciado ${style.letterSpacing}"
            val letters = text.filter { it.isLetter() }
            if (letters.length >= 4 && text == text.uppercase() && letters.all { it.isUpperCase() } && text.trim() !in acronyms) {
                problems += "$label va entero en mayúsculas"
            }
        }
        assertTrue("$screen:\n" + problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    private fun content(): UiState.Content {
        val base = TestData.snapshot(now)
        fun hour(h: HourPoint): HourPoint {
            val hh = Instant.ofEpochSecond(h.time).atZone(TestData.zone).hour
            return h.copy(windKmh = 8.0 + hh, windGustsKmh = 20.0, windDirectionDeg = 225, humidityPct = 55 + hh, dewPointC = 9.0)
        }
        fun day(d: DayPoint): DayPoint {
            val date = LocalDate.parse(d.date)
            fun at(h: Int, m: Int) = TestData.at(date.year, date.monthValue, date.dayOfMonth, h, m)
            return d.copy(sunrise = at(6, 44), sunset = at(21, 43), daylightSeconds = 53_940.0, moonrise = at(1, 44), moonset = at(15, 12), moonPhase = 0.84)
        }
        val air = AirQuality(
            LatLon(40.0, -3.0), fetchedAt = now,
            hours = (0 until 96).map { i ->
                AirHour(now - 36_000 + i * 3600L, aqi = 20 + i % 30, pm25Index = 10, pm10Index = 10, ozoneIndex = 30, no2Index = 5, so2Index = 1,
                    pm10 = 10.0, dust = 0.5, olivePollen = if (i % 24 == 15) 80.0 else 1.0, grassPollen = if (i % 24 == 15) 20.0 else 0.0)
            },
        )
        val warning = Warning(
            id = "x", level = WarningLevel.ORANGE, phenomenon = "tormentas", areaName = "Llanura de Ejemplo",
            onset = TestData.at(2025, 6, 10, 16), expires = TestData.at(2025, 6, 10, 22), probability = "40%-70%",
            description = "Tormentas con granizo.", instruction = "Evite zonas inundables.", sent = TestData.at(2025, 6, 10, 9, 30),
        )
        val local = Instant.ofEpochSecond(now).atZone(TestData.zone).toLocalDate().toString()
        val snapshot = base.copy(hourly = base.hourly.map(::hour), daily = base.daily.map(::day), air = air, warnings = listOf(warning))
        return UiState.Content(snapshot, now, offline = false, refreshing = false, usage = ApiUsage(local, 2140, local.take(7), 10460))
    }

    @Test fun `la principal, arriba y abajo, usa solo la escala`() {
        rule.setContent { MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone) }
        assertLetter("principal arriba", minTexts = 20)
        rule.onNodeWithText("Ajustes").performScrollTo()
        assertLetter("principal abajo", minTexts = 20)
    }

    private fun message(state: UiState) {
        rule.setContent { MainScreen(state, {}, {}, {}) }
        assertLetter("mensaje $state", minTexts = 1)
    }

    @Test fun `el mensaje de permiso usa solo la escala`() = message(UiState.NeedsPermission)

    @Test fun `el mensaje sin conexion usa solo la escala`() = message(UiState.NoConnection)

    @Test fun `el mensaje de buscando usa solo la escala`() = message(UiState.Searching)

    @Test fun `la ventana del dia usa solo la escala`() {
        rule.setContent { MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, initialDayDate = LocalDate.of(2025, 6, 11).toString()) }
        rule.mainClock.advanceTimeBy(3_000)
        assertLetter("ventana del día", minTexts = 10)
        assertTrue("la ventana del día no se abrió", texts().any { it.first == "Hora a hora" })
    }

    private fun sheet(name: String, minTexts: Int, hourly: HourlySheetKind? = null, days: DaysSheetKind? = null) {
        rule.setContent {
            MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, initialHourlySheet = hourly, initialDaysSheet = days)
        }
        assertLetter(name, minTexts)
    }

    @Test fun `la hoja de viento usa solo la escala`() = sheet("hoja de viento", 10, hourly = HourlySheetKind.WIND)

    @Test fun `la hoja de humedad usa solo la escala`() = sheet("hoja de humedad", 10, hourly = HourlySheetKind.HUMIDITY)

    @Test fun `la hoja del aire usa solo la escala`() = sheet("hoja del aire", 5, days = DaysSheetKind.AIR)

    @Test fun `la hoja del polen usa solo la escala`() = sheet("hoja del polen", 5, days = DaysSheetKind.POLLEN)

    @Test fun `la hoja del aviso usa solo la escala`() {
        rule.setContent { MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone) }
        rule.onNodeWithText("Tormentas", substring = true, ignoreCase = true).performClick()
        assertLetter("hoja del aviso", minTexts = 10)
    }

    @Test fun `Ajustes usa solo la escala`() {
        rule.setContent { MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, initialSettings = true) }
        assertLetter("Ajustes", minTexts = 10)
        assertTrue(texts().any { it.first.contains("Trastea Labs") })
    }

    private fun card(ref: PlaceRef, name: String, shown: Boolean = false) = PlaceCardModel(
        ref = ref, name = name, photo = SkyPhoto.CLEAR_MILD, sky = Sky.PARTLY_CLOUDY, temp = 22, max = 24, min = 11,
        ageLabel = null, localTime = null, noPermission = false, isShown = shown,
    )

    private val cards = listOf(card(PlaceRef.Here, "Córdoba", shown = true)) +
        listOf("Sevilla", "Las Palmas de Gran Canaria", "Sanlúcar de Barrameda").mapIndexed { i, n -> card(PlaceRef.Saved("s$i"), n) }

    @Test fun `Sitios con postales usa solo la escala`() {
        rule.setContent { MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, initialPlaces = true, placeCards = cards) }
        assertLetter("Sitios", minTexts = 10)
        assertTrue(texts().any { it.first == "Donde estoy" })
    }

    @Test fun `la hoja de borrar usa solo la escala`() {
        rule.setContent { MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, initialPlaces = true, placeCards = cards) }
        rule.onNodeWithText("Editar").performClick()
        rule.onNode(hasContentDescription("Borrar Sevilla")).performClick()
        rule.waitForIdle()
        assertTrue(texts().any { it.first == "Borrar Sevilla" })
        assertLetter("hoja de borrar", minTexts = 5)
    }

    private fun search(query: String, state: SearchState, minTexts: Int, name: String) {
        rule.setContent {
            MainScreen(content(), {}, {}, {}, phoneZone = TestData.zone, placeCards = cards, initialQuery = query, search = SearchUi(query, state))
        }
        assertLetter(name, minTexts)
    }

    @Test fun `el buscador con resultados usa solo la escala`() {
        val hit = PlaceHit("Villanueva de la Serena", "Badajoz", "ES", "España", LatLon(38.0, -5.0), 1000, bold = listOf(0 until 3))
        val world = PlaceHit("Villa María", "Córdoba", "AR", "Argentina", LatLon(-32.4, -5.0), 1000, bold = listOf(0 until 3))
        search("villa", SearchState.Results(SearchResults(listOf(hit), listOf(world))), 5, "buscador con resultados")
    }

    @Test fun `el buscador sin resultados usa solo la escala`() = search("sevila", SearchState.NoResults, 2, "buscador vacío")
}
