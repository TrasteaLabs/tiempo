package com.trastealabs.tiempo.ui

import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performImeAction
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.trastealabs.tiempo.data.PlaceHit
import com.trastealabs.tiempo.data.SearchResults
import com.trastealabs.tiempo.data.key
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.Sky
import androidx.test.core.app.ApplicationProvider
import com.trastealabs.tiempo.ui.scene.PhotoBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La pantalla Sitios del 006 (diseño B): postales, buscador y borrar (H6–H8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1800dp")
class PlacesScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun card(
        ref: PlaceRef,
        name: String,
        photo: SkyPhoto? = SkyPhoto.CLEAR_MILD,
        age: String? = null,
        localTime: String? = null,
        noPermission: Boolean = false,
        shown: Boolean = false,
    ) = PlaceCardModel(
        ref = ref, name = name, photo = photo, sky = photo?.let { Sky.PARTLY_CLOUDY },
        temp = photo?.let { 22 }, max = photo?.let { 24 }, min = photo?.let { 11 },
        ageLabel = age, localTime = localTime, noPermission = noPermission, isShown = shown,
    )

    private val here = card(PlaceRef.Here, "Córdoba", shown = true)
    private val five = listOf("sev" to "Sevilla", "mad" to "Madrid", "lco" to "A Coruña", "lpa" to "Las Palmas de Gran Canaria", "bue" to "Buenos Aires")
        .map { (id, name) -> card(PlaceRef.Saved(id), name) }

    private fun hit(name: String, region: String, cc: String = "ES", country: String = "España", lat: Double = 38.0) =
        PlaceHit(name, region, cc, country, LatLon(lat, -5.0), 1000, bold = listOf(0 until 3))

    private val badajoz = hit("Villanueva de la Serena", "Badajoz")
    private val cordobaVilla = hit("Villanueva de Córdoba", "Córdoba", lat = 38.3)
    private val argentina = hit("Villa María", "Córdoba", "AR", "Argentina", lat = -32.4)
    private val results = SearchResults(listOf(badajoz, cordobaVilla), listOf(argentina))

    private val selected = mutableListOf<PlaceRef>()
    private val saved = mutableListOf<PlaceHit>()
    private val deleted = mutableListOf<String>()
    private val queries = mutableListOf<String>()
    private var backs = 0

    private val bands get() = PhotoBands(ApplicationProvider.getApplicationContext())

    private fun show(cards: List<PlaceCardModel> = listOf(here) + five, search: SearchUi = SearchUi()) = rule.setContent {
        PlacesScreen(
            cards = cards,
            search = search,
            onBack = { backs++ },
            onSelect = { selected += it },
            onQueryChange = { queries += it },
            onSaveAndOpen = { saved += it },
            onDelete = { deleted += it },
            bands = bands,
        )
    }

    private fun top(text: String) = rule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    private fun openSearch() {
        rule.onNodeWithText("Buscar un sitio").performClick()
        rule.waitForIdle()
    }

    @Test fun `primero la postal de donde estoy, luego Guardados 5 de 10 y los 5 en su orden (H8)`() {
        show()
        val order = listOf("Córdoba", "Guardados · 5 de 10", "Sevilla", "Madrid", "A Coruña", "Las Palmas de Gran Canaria", "Buenos Aires")
        val tops = order.map(::top)
        assertEquals(tops.sorted(), tops)
        rule.onNodeWithText("Donde estoy", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Editar").assertExists()
    }

    @Test fun `tocar una postal llama a select con su sitio (H8)`() {
        show()
        rule.onNodeWithText("Madrid", useUnmergedTree = true).performClick()
        assertEquals(listOf<PlaceRef>(PlaceRef.Saved("mad")), selected)
    }

    @Test fun `la flecha vuelve sin cambiar de sitio (H8)`() {
        show()
        rule.onNode(hasContentDescription("Volver")).performClick()
        assertEquals(1, backs)
        assertTrue(selected.isEmpty())
    }

    @Test fun `sin guardados sale la frase y no sale Editar`() {
        show(listOf(here))
        rule.onNodeWithText("Aún no has guardado ningún sitio", substring = true).assertExists()
        rule.onNodeWithText("Editar").assertDoesNotExist()
    }

    @Test fun `postal sin datos, donde estoy sin permiso y datos viejos`() {
        show(
            listOf(
                card(PlaceRef.Here, "Donde estoy", photo = null, noPermission = true),
                card(PlaceRef.Saved("grx"), "Granada", photo = null),
                card(PlaceRef.Saved("bue"), "Buenos Aires", age = "Datos del domingo", localTime = "13:20"),
            ),
        )
        rule.onNodeWithText("Sin permiso de ubicación", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Aún sin datos", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Datos del domingo", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Parcialmente nuboso · 13:20 allí", useUnmergedTree = true).assertExists()
    }

    @Test fun `TalkBack de la postal como en el diseño`() {
        show(listOf(here, card(PlaceRef.Saved("sev"), "Sevilla", age = "Datos de las 15:10")))
        rule.onNode(hasContentDescription("Sevilla, parcialmente nuboso, 22 grados, máxima 24, mínima 11, datos de las 15:10"))
            .assert(SemanticsMatcher("abre Sevilla") { it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "Abre Sevilla" })
        rule.onNode(hasContentDescription("Córdoba, donde estoy, parcialmente nuboso, 22 grados, máxima 24, mínima 11")).assertExists()
    }

    // ---------- 019 bloque 4: zona táctil y teclado ----------

    @Test fun `volver y Editar tienen 48 dp de zona (019)`() {
        show()
        rule.onNode(hasContentDescription("Volver")).assertTouchAtLeast48("volver")
        rule.onNodeWithText("Editar").assertTouchAtLeast48("Editar")
    }

    @Test fun `la papelera tiene 48 dp de zona (019)`() {
        show()
        rule.onNodeWithText("Editar").performClick()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Borrar Madrid")).assertTouchAtLeast48("papelera")
    }

    @Test fun `la flecha y la X del buscador tienen 48 dp de zona (019)`() {
        show(search = SearchUi(query = "villa", state = SearchState.Results(results)))
        openSearch()
        rule.onNode(hasSetTextAction()).performTextInput("v")
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Volver")).assertTouchAtLeast48("flecha del buscador")
        rule.onNode(hasContentDescription("Borrar lo escrito")).assertTouchAtLeast48("X del buscador")
    }

    @Test fun `la tecla Buscar cierra el teclado y deja los resultados (019)`() {
        show(search = SearchUi(query = "villa", state = SearchState.Results(results)))
        openSearch()
        rule.onNode(hasSetTextAction()).assertIsFocused()
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assertIsNotFocused()
        rule.onNodeWithText("En España").assertExists()
        rule.onNodeWithText("Badajoz, España", useUnmergedTree = true).assertExists()
    }

    @Test fun `con 1 letra sale la pista`() {
        show(search = SearchUi(query = "v", state = SearchState.Hint))
        openSearch()
        rule.onNodeWithText("Escribe al menos 2 letras.").assertIsDisplayed()
        rule.onNodeWithText("no sale nada a internet", substring = true).assertExists()
    }

    @Test fun `cada letra llega a setQuery (H2)`() {
        show()
        openSearch()
        rule.onNode(hasSetTextAction()).performTextInput("v")
        assertEquals("v", queries.last())
    }

    @Test fun `con resultados salen los dos grupos y la provincia con el pais (H3)`() {
        show(search = SearchUi(query = "villa", state = SearchState.Results(results)))
        openSearch()
        rule.onNodeWithText("En España").assertExists()
        rule.onNodeWithText("En el resto del mundo").assertExists()
        rule.onNodeWithText("Badajoz, España", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Córdoba, Argentina", useUnmergedTree = true).assertExists()
        assertTrue(top("Villanueva de la Serena") < top("En el resto del mundo"))
        rule.onNode(hasContentDescription("Guardar y abrir Villanueva de la Serena, Badajoz, España")).assertExists()
    }

    @Test fun `los ya guardados salen con Guardado (H6)`() {
        show(search = SearchUi(query = "villa", state = SearchState.Results(results), savedKeys = setOf(badajoz.key)))
        openSearch()
        rule.onNodeWithText("Guardado", useUnmergedTree = true).assertExists()
        rule.onNode(hasContentDescription("Abrir Villanueva de la Serena, Badajoz, España. Ya guardado")).assertExists()
    }

    @Test fun `sin resultados lo dice`() {
        show(search = SearchUi(query = "sevila", state = SearchState.NoResults))
        openSearch()
        rule.onNodeWithText("No encuentro «sevila».").assertExists()
        rule.onNodeWithText("Los barrios no están en la lista", substring = true).assertExists()
    }

    @Test fun `la primera busqueda lenta dice Buscando (H4)`() {
        show(search = SearchUi(query = "co", state = SearchState.Loading))
        openSearch()
        rule.onNodeWithText("Buscando…").assertExists()
    }

    @Test fun `tocar un resultado llama a saveAndOpen (H6)`() {
        show(search = SearchUi(query = "villa", state = SearchState.Results(results)))
        openSearch()
        rule.onNodeWithText("Badajoz, España", useUnmergedTree = true).performClick()
        assertEquals(listOf(badajoz), saved)
        // Lo escrito se olvida también en el ViewModel: al volver a buscar no salen los resultados de antes.
        assertEquals("", queries.last())
    }

    @Test fun `con 10 la franja del tope, el no guardado no hace nada y el guardado se abre (H6)`() {
        show(search = SearchUi(query = "villa", state = SearchState.Results(results), full = true, savedKeys = setOf(cordobaVilla.key)))
        openSearch()
        rule.onNodeWithText("Ya tienes 10 sitios guardados", substring = true).assertExists()
        rule.onNodeWithText("Badajoz, España", useUnmergedTree = true).performClick()
        assertTrue(saved.isEmpty())
        rule.onNodeWithText("Córdoba, España", useUnmergedTree = true).performClick()
        assertEquals(listOf(cordobaVilla), saved)
    }

    @Test fun `atras en el buscador vuelve a las postales`() {
        lateinit var back: OnBackPressedDispatcher
        rule.setContent {
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            PlacesScreen(listOf(here) + five, SearchUi(), onBack = { backs++ }, onSelect = {}, onQueryChange = {}, onSaveAndOpen = {}, onDelete = {}, bands = bands)
        }
        openSearch()
        rule.runOnUiThread { back.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Sevilla", useUnmergedTree = true).assertExists()
        assertEquals(0, backs)
    }

    @Test fun `Editar saca la papelera y borrar sube la hoja con el nombre (H7)`() {
        show()
        rule.onNodeWithText("Editar").performClick()
        rule.onNodeWithText("Hecho").assertExists()
        // «Donde estoy» no se borra: 5 papeleras, una por guardado.
        rule.onNode(hasContentDescription("Borrar Córdoba")).assertDoesNotExist()
        rule.onNode(hasContentDescription("Borrar Madrid")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Borrar Madrid").assertExists()
        // El velo cierra la hoja sin borrar.
        clickScrim(rule)
        rule.waitForIdle()
        rule.onNodeWithText("Borrar Madrid").assertDoesNotExist()
        assertTrue(deleted.isEmpty())
        // Y «Borrar» de la hoja borra.
        rule.onNode(hasContentDescription("Borrar Madrid")).performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Borrar") and hasClickAction()).performClick()
        assertEquals(listOf("mad"), deleted)
    }

    @Test fun `atras cierra la hoja de borrar sin borrar y dos toques no abren dos`() {
        show()
        rule.onNodeWithText("Editar").performClick()
        rule.onNode(hasContentDescription("Borrar Madrid")).performClick()
        rule.onNode(hasContentDescription("Borrar Madrid")).performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithTag("delete_sheet").fetchSemanticsNodes().size)
        pressSystemBack(rule)
        rule.waitForIdle()
        rule.onNodeWithTag("delete_sheet").assertDoesNotExist()
        assertTrue(deleted.isEmpty())
    }

    @Test fun `con 10 guardados dice Guardados 10 de 10`() {
        val ten = five + listOf("a" to "Granada", "b" to "Valencia", "c" to "Bilbao", "d" to "Tokio", "e" to "Cádiz")
            .map { (id, name) -> card(PlaceRef.Saved(id), name) }
        show(listOf(here) + ten)
        rule.onNodeWithText("Guardados · 10 de 10").assertExists()
        rule.onAllNodesWithTag("place_card").assertCountEquals(11)
    }

    @Test fun `un nombre largo va en una linea con puntos suspensivos`() {
        val long = card(PlaceRef.Saved("vi"), "Villanueva de los Infantes de la Sierra Alta")
        show(listOf(here, long))
        val node = rule.onNodeWithText("Villanueva de los Infantes de la Sierra Alta", useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout = layouts.single()
        // Una línea y «…» al final: lo que de verdad se corta lo mira el OPPO (aquí la letra mide otra cosa).
        assertEquals(1, layout.lineCount)
        assertEquals(1, layout.layoutInput.maxLines)
        assertEquals(androidx.compose.ui.text.style.TextOverflow.Ellipsis, layout.layoutInput.overflow)
    }

    @Test fun `sin guardados sale el texto de la maqueta`() {
        show(listOf(here))
        rule.onNodeWithText("Aún no has guardado ningún sitio. Búscalo por su nombre: el de un viaje, la casa de alguien…").assertExists()
        rule.onNodeWithText("Guardados").assertExists()
    }

    @Test fun `en Editar las postales no se abren`() {
        show()
        rule.onNodeWithText("Editar").performClick()
        rule.onNodeWithText("Sevilla", useUnmergedTree = true).performClick()
        assertTrue(selected.isEmpty())
        rule.onNode(hasContentDescription("Sevilla, parcialmente nuboso", substring = true))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
}
