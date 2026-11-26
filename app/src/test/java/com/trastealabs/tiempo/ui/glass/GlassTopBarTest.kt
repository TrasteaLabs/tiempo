package com.trastealabs.tiempo.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.TestData
import com.trastealabs.tiempo.domain.ApiUsage
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.Sky
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.ui.PlacesScreen
import com.trastealabs.tiempo.ui.SearchUi
import com.trastealabs.tiempo.ui.SettingsScreen
import com.trastealabs.tiempo.ui.scene.PhotoBands
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** La barra superior de M3 con cristal (012 P4): título, flecha, y se pliega al desplazar. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
// 800 dp de ancho: la letra de prueba de Robolectric mide 1 em por carácter (más ancha que Google Sans Flex), así el 200 % solo falla si algo se corta de verdad.
@Config(sdk = [34], qualifiers = "w800dp-h640dp")
class GlassTopBarTest {
    @get:Rule val rule = createComposeRule()

    // ---------- el componente suelto ----------

    private lateinit var behavior: TopAppBarScrollBehavior

    private fun showBare(title: String = "Ajustes", fontScale: Float = 1f, onBack: () -> Unit = {}) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                WeatherTheme {
                    behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                    Scaffold(
                        modifier = Modifier.testTag("scaffold").nestedScroll(behavior.nestedScrollConnection),
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = { GlassTopBar(title = title, onBack = onBack, scrollBehavior = behavior) },
                    ) { inner ->
                        Column(Modifier.testTag("content").padding(inner).verticalScroll(rememberScrollState())) {
                            repeat(40) { Text("Fila $it", Modifier.height(48.dp)) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun swipeAllTheWay(tag: String) {
        repeat(3) { rule.onNodeWithTag(tag).performTouchInput { swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 200) } }
        rule.waitForIdle()
    }

    @Test fun `enseña el titulo y la flecha`() {
        showBare()
        rule.onAllNodesWithText("Ajustes").onFirst().assertIsDisplayed()
        rule.onNode(hasContentDescription("Volver")).assertIsDisplayed()
        rule.onNodeWithTag("glass_top_bar").assertIsDisplayed()
    }

    @Test fun `la flecha de la barra llama a volver`() {
        var backs = 0
        showBare(onBack = { backs++ })
        rule.onNode(hasContentDescription("Volver")).performClick()
        assertEquals(1, backs)
    }

    @Test fun `sin desplazar esta desplegada y al desplazar se pliega`() {
        showBare()
        assertEquals(0f, behavior.state.collapsedFraction, 0f)
        swipeAllTheWay("content")
        assertEquals("offset=${behavior.state.heightOffset} limit=${behavior.state.heightOffsetLimit} content=${behavior.state.contentOffset}", 1f, behavior.state.collapsedFraction, 0.01f)
    }

    @Test fun `al volver arriba se despliega otra vez`() {
        showBare()
        swipeAllTheWay("content")
        repeat(3) { rule.onNodeWithTag("content").performTouchInput { swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 200) } }
        rule.waitForIdle()
        assertEquals(0f, behavior.state.collapsedFraction, 0.01f)
    }

    @Test fun `el cristal es lo unico de la barra que pinta fondo`() {
        val text = File("src/main/java/com/trastealabs/tiempo/ui/glass/GlassTopBar.kt").readText()
        val code = text.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") || it.trim().startsWith("/*") }
        // 019: la única otra es la superficie opaca del ahorro de batería, en la misma línea que el cristal.
        val other = code.filter { l -> listOf(".background(", "drawBehind", "drawRect", "Surface(", "Brush.").any { it in l } }
        assertTrue("la barra no pinta fondo propio: $other", other.all { "if (opaque)" in it && ".glass(" in it })
        assertEquals("un solo .glass( en la barra", 1, code.count { ".glass(" in it })
    }

    @Test fun `con ahorro de bateria la barra es opaca y sin cristal (019)`() {
        val power = ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(android.os.PowerManager::class.java)
        org.robolectric.Shadows.shadowOf(power).setIsPowerSaveMode(true)
        showBare()
        rule.onNodeWithTag("glass_top_bar_opaque", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("glass_top_bar_glass", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `sin ahorro de bateria la barra lleva el cristal`() {
        showBare()
        rule.onNodeWithTag("glass_top_bar_glass", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("glass_top_bar_opaque", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `con la letra al 200 por cien el titulo no se sale por los lados`() {
        showBare(fontScale = 2f)
        assertWithin("Ajustes")
    }

    private fun assertWithin(text: String) {
        val node = rule.onAllNodesWithText(text).onFirst().fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("$text se sale por los lados: $bounds en $root", bounds.left >= root.left - 0.5f && bounds.right <= root.right + 0.5f)
    }

    // ---------- Ajustes y Sitios de verdad ----------

    private val zone = TestData.zone

    private fun showSettings(fontScale: Float = 1f) = rule.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            WeatherTheme { Box { SettingsScreen(ApiUsage(), TestData.at(2025, 6, 10, 10, 20), {}, zone) } }
        }
    }

    @Test fun `Ajustes lleva la barra con su titulo y la flecha`() {
        showSettings()
        rule.onNodeWithTag("glass_top_bar").assertIsDisplayed()
        rule.onAllNodesWithText("Ajustes").onFirst().assertIsDisplayed()
        rule.onNode(hasContentDescription("Volver")).assertIsDisplayed()
    }

    @Test fun `Ajustes con la letra al 200 - Ajustes no se corta`() {
        showSettings(fontScale = 2f)
        assertWithin("Ajustes")
    }

    private val bands get() = PhotoBands(ApplicationProvider.getApplicationContext())

    private fun card(ref: PlaceRef, name: String) = PlaceCardModel(
        ref = ref, name = name, photo = SkyPhoto.CLEAR_MILD, sky = Sky.PARTLY_CLOUDY, temp = 22, max = 24, min = 11,
        ageLabel = null, localTime = null, noPermission = false, isShown = ref == PlaceRef.Here,
    )

    private fun showPlaces(fontScale: Float = 1f) = rule.setContent {
        val d = LocalDensity.current
        val cards = listOf(card(PlaceRef.Here, "Córdoba")) +
            listOf("Sevilla", "Madrid", "A Coruña", "Granada", "Bilbao", "Cádiz", "Vigo").mapIndexed { i, n -> card(PlaceRef.Saved("s$i"), n) }
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            WeatherTheme {
                PlacesScreen(
                    cards = cards, search = SearchUi(), onBack = {}, onSelect = {}, onQueryChange = {},
                    onSaveAndOpen = {}, onDelete = {}, bands = bands,
                )
            }
        }
    }

    @Test fun `Sitios lleva la barra con su titulo, la flecha y Editar`() {
        showPlaces()
        rule.onNodeWithTag("glass_top_bar").assertIsDisplayed()
        rule.onAllNodesWithText("Sitios").onFirst().assertIsDisplayed()
        rule.onNode(hasContentDescription("Volver")).assertIsDisplayed()
        rule.onNodeWithText("Editar").assertIsDisplayed()
    }

    @Test fun `Sitios con la letra al 200 - Sitios no se corta`() {
        showPlaces(fontScale = 2f)
        assertWithin("Sitios")
    }

    @Test fun `Sitios con la letra al 200 - Editar sigue a la vista`() {
        showPlaces(fontScale = 2f)
        val edit = rule.onNodeWithText("Editar").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Editar $edit se sale de $root", edit.right <= root.right + 0.5f && edit.left >= root.left - 0.5f)
    }
}
