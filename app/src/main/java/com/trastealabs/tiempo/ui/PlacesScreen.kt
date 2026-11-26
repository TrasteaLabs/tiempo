package com.trastealabs.tiempo.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.data.PlaceHit
import com.trastealabs.tiempo.data.key
import com.trastealabs.tiempo.domain.MAX_SAVED
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.ui.glass.GlassTopBar
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.scene.PhotoBands

/**
 * La pantalla Sitios (010, Cristal): como Ajustes, dentro de la misma ventana y con la escena desenfocada detrás. Arriba,
 * volver y «Editar»/«Hecho»; «Sitios» grande; el campo «Buscar un sitio»; la postal de «donde estoy» y las de los
 * guardados en su orden, cada una con el trozo de su foto ([bands]). Borrar sube la hoja [DeleteSheet].
 *
 * Al tocar el campo, las postales se van y queda el buscador ([SearchField] y los resultados en una tarjeta de cristal
 * entre el campo y el teclado). Atrás o la flecha del campo vuelven a las postales. [initialQuery] (solo la demo)
 * empieza en el buscador con eso escrito.
 */
@Composable
fun PlacesScreen(
    cards: List<PlaceCardModel>,
    search: SearchUi,
    onBack: () -> Unit,
    onSelect: (PlaceRef) -> Unit,
    onQueryChange: (String) -> Unit,
    onSaveAndOpen: (PlaceHit) -> Unit,
    onDelete: (String) -> Unit,
    bands: PhotoBands,
    modifier: Modifier = Modifier,
    initialQuery: String? = null,
    /** Solo la demo: postales sin textos (solo la foto y su velo), para medir el contraste. */
    bare: Boolean = false,
) {
    var searching by rememberSaveable { mutableStateOf(initialQuery != null) }
    var query by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    LaunchedEffect(Unit) { if (initialQuery != null) onQueryChange(initialQuery) }
    Box(modifier.testTag("places_screen").fillMaxSize()) {
        if (searching) {
            val close = {
                searching = false
                query = ""
                onQueryChange("")
            }
            BackHandler(onBack = close)
            SearchPane(
                query = query,
                onQueryChange = {
                    query = it
                    onQueryChange(it)
                },
                search = search,
                onBack = close,
                onPick = {
                    // Lo escrito se olvida también en el ViewModel: al volver al buscador, empieza limpio.
                    query = ""
                    onQueryChange("")
                    onSaveAndOpen(it)
                },
            )
        } else {
            CardsPane(cards, bands, bare, onBack, onSelect, onDelete, onSearch = { searching = true })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardsPane(
    cards: List<PlaceCardModel>,
    bands: PhotoBands,
    bare: Boolean,
    onBack: () -> Unit,
    onSelect: (PlaceRef) -> Unit,
    onDelete: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val here = cards.firstOrNull { it.ref == PlaceRef.Here }
    val saved = cards.filter { it.ref is PlaceRef.Saved }
    var editMode by rememberSaveable { mutableStateOf(false) }
    // Al borrar el último, «Editar» se va y con él el modo.
    val editing = editMode && saved.isNotEmpty()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    // La hoja se acuerda del sitio mientras baja, aunque ya no esté en la lista (al borrar).
    var doomed by remember { mutableStateOf<PlaceCardModel?>(null) }
    confirm?.let { id -> saved.firstOrNull { it.ref == PlaceRef.Saved(id) } }?.let { doomed = it }
    BackHandler(enabled = editing && confirm == null) { editMode = false }
    val pill = RoundedCornerShape(percent = 50)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.testTag("places_scroll").nestedScroll(scroll.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.places),
                onBack = onBack,
                scrollBehavior = scroll,
                actions = {
                    if (saved.isNotEmpty()) {
                        Text(
                            text = stringResource(if (editing) R.string.places_done else R.string.places_edit),
                            color = Color.White,
                            style = sceneText(CristalType.labelLarge),
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .minimumInteractiveComponentSize()
                                .translucent(pill)
                                .clip(pill)
                                .clickable(role = Role.Button) { editMode = !editing }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                },
            )
        },
    ) { inner ->
      Column(Modifier.padding(inner).verticalScroll(rememberScrollState())) {
        // El campo de búsqueda en cristal: tocarlo abre el buscador.
        val field = RoundedCornerShape(percent = 50)
        val fieldPress = remember { MutableInteractionSource() }
        Row(
            Modifier
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)
                .fillMaxWidth()
                .height(52.dp)
                .translucent(field)
                .clip(field)
                .clickable(interactionSource = fieldPress, role = Role.Button, onClick = onSearch)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(painterResource(R.drawable.ic_wx_search), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White))
            Text(stringResource(R.string.search_place), color = Color.White, style = sceneText(CristalType.bodyLarge))
        }
        if (here != null) {
            PlaceCard(here, onClick = { onSelect(here.ref) }, modifier = Modifier.padding(top = 10.dp), bands = bands, bare = bare)
        }
        Text(
            if (saved.isEmpty()) stringResource(R.string.saved_title) else stringResource(R.string.saved_count, saved.size, MAX_SAVED),
            color = Color.White,
            style = sceneText(CristalType.titleMedium),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp),
        )
        if (saved.isEmpty()) {
            Row(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    .fillMaxWidth()
                    .translucent(RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(painterResource(R.drawable.ic_wx_bookmark), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White))
                Text(
                    stringResource(R.string.saved_empty),
                    color = Color.White,
                    style = sceneText(CristalType.bodyMedium),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
            saved.forEach { card ->
                val id = (card.ref as PlaceRef.Saved).id
                PlaceCard(
                    card,
                    onClick = { onSelect(card.ref) },
                    onDelete = if (editing) ({ confirm = id }) else null,
                    bands = bands,
                    bare = bare,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
      }
    }
    val sheetCard = doomed
    if (confirm != null && sheetCard != null) {
        DeleteSheet(
            card = sheetCard,
            bands = bands,
            onDismiss = { confirm = null },
            onConfirm = { onDelete((sheetCard.ref as PlaceRef.Saved).id) },
        )
    }
}

@Composable
private fun SearchPane(
    query: String,
    onQueryChange: (String) -> Unit,
    search: SearchUi,
    onBack: () -> Unit,
    onPick: (PlaceHit) -> Unit,
) {
    val focus = remember { FocusRequester() }
    // El teclado sube al entrar (la ventana es de borde a borde: el hueco del teclado lo quita safeDrawingPadding).
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, onQueryChange, onBack, focus, Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp))
        // Una lista perezosa: con 60 resultados, cada letra solo compone las filas que se ven (H4).
        val strings = SearchStrings(
            hintTitle = stringResource(R.string.search_hint_title),
            hintBody = stringResource(R.string.search_hint_body),
            noneTitle = stringResource(R.string.search_none_title, search.query.trim()),
            noneBody = stringResource(R.string.search_none_body),
            spain = stringResource(R.string.search_spain),
            world = stringResource(R.string.search_world),
        )
        val pick = { hit: PlaceHit -> if (hit.key in search.savedKeys || !search.full) onPick(hit) }
        val list = rememberLazyListState()
        // Cada búsqueda nueva empieza arriba.
        LaunchedEffect(search.state) { list.scrollToItem(0) }
        LazyColumn(
            state = list,
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
                .fillMaxWidth()
                .weight(1f, fill = false)
                .translucent(RoundedCornerShape(26.dp)),
            contentPadding = PaddingValues(6.dp),
        ) {
            // Lo que se enseña es lo que ha calculado el ViewModel; mientras alcanza a lo escrito, lo anterior.
            when (val state = search.state) {
                SearchState.Hint -> item(key = "hint", contentType = "message") { SearchMessage(strings.hintTitle, strings.hintBody, top = 18) }
                SearchState.Loading -> item(key = "loading", contentType = "message") { SearchLoading() }
                SearchState.NoResults -> item(key = "none", contentType = "message") { SearchMessage(strings.noneTitle, strings.noneBody) }
                is SearchState.Results -> {
                    if (search.full) item(key = "limit", contentType = "message") { LimitBanner() }
                    if (state.results.spain.isNotEmpty()) {
                        item(key = "title-es", contentType = "title") { GroupTitle(strings.spain) }
                        items(state.results.spain, key = { "es-" + it.key }, contentType = { "hit" }) { hit ->
                            PlaceResultRow(hit, saved = hit.key in search.savedKeys, full = search.full, onClick = { pick(hit) })
                        }
                    }
                    if (state.results.world.isNotEmpty()) {
                        item(key = "title-w", contentType = "title") { GroupTitle(strings.world) }
                        items(state.results.world, key = { "w-" + it.key }, contentType = { "hit" }) { hit ->
                            PlaceResultRow(hit, saved = hit.key in search.savedKeys, full = search.full, onClick = { pick(hit) })
                        }
                    }
                }
            }
        }
    }
}

/** Los textos del buscador, leídos fuera de la lista perezosa. */
private data class SearchStrings(
    val hintTitle: String,
    val hintBody: String,
    val noneTitle: String,
    val noneBody: String,
    val spain: String,
    val world: String,
)
