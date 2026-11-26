package com.trastealabs.tiempo.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.trastealabs.tiempo.ui.theme.OnSky
import com.trastealabs.tiempo.ui.theme.CristalType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trastealabs.tiempo.R
import com.trastealabs.tiempo.data.PlaceHit
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.activeWarnings
import com.trastealabs.tiempo.domain.savedWarningsLabel
import com.trastealabs.tiempo.domain.reportLabel
import com.trastealabs.tiempo.domain.summaryLines
import com.trastealabs.tiempo.ui.scene.PHOTO_LOOKS
import com.trastealabs.tiempo.domain.airDays
import com.trastealabs.tiempo.domain.pollenDays
import com.trastealabs.tiempo.domain.level
import com.trastealabs.tiempo.domain.daysFrom
import com.trastealabs.tiempo.domain.next24Hours
import com.trastealabs.tiempo.domain.withCurrentAt
import com.trastealabs.tiempo.ui.day.DayWindow
import com.trastealabs.tiempo.ui.motion.LocalWindowMotion
import com.trastealabs.tiempo.ui.motion.Screen
import com.trastealabs.tiempo.ui.motion.ScreenHost
import com.trastealabs.tiempo.ui.motion.secondaryContent
import com.trastealabs.tiempo.domain.offlineLabel
import com.trastealabs.tiempo.domain.today
import com.trastealabs.tiempo.domain.isSouth
import com.trastealabs.tiempo.domain.moonPhaseToday
import com.trastealabs.tiempo.ui.scene.rememberMoonLook
import com.trastealabs.tiempo.ui.glass.translucent
import com.trastealabs.tiempo.ui.glass.sceneText
import com.trastealabs.tiempo.ui.scene.SceneBackdrop
import com.trastealabs.tiempo.ui.scene.SecondaryBackdrop
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import com.trastealabs.tiempo.ui.scene.PhotoBands
import com.trastealabs.tiempo.ui.scene.PhotoCache
import com.trastealabs.tiempo.domain.LOADING_PHOTO
import com.trastealabs.tiempo.domain.MoonShow
import com.trastealabs.tiempo.domain.SkyEffects
import com.trastealabs.tiempo.domain.SkyPhoto
import com.trastealabs.tiempo.domain.effectsNow
import com.trastealabs.tiempo.domain.skyPhotoNow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.BoxWithConstraints
import com.trastealabs.tiempo.ui.scene.SkyClock
import com.trastealabs.tiempo.update.UpdateState
import java.time.Instant
import java.time.ZoneId

@Composable
fun MainScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    showBatteryTip: Boolean = false,
    onBatteryTipSettings: () -> Unit = {},
    onBatteryTipDismiss: () -> Unit = {},
    /** Solo la demo: abre al empezar la página de ese día. */
    initialDayDate: String? = null,
    /** Solo la demo: empieza en Ajustes. */
    initialSettings: Boolean = false,
    /** Solo la demo: abre al empezar la hoja de 24 h de viento o de humedad. */
    initialHourlySheet: HourlySheetKind? = null,
    /** Solo la demo: abre al empezar la hoja de los próximos días del aire o del polen. */
    initialDaysSheet: DaysSheetKind? = null,
    /** Las postales de la pantalla Sitios (006): «donde estoy» y los guardados. */
    placeCards: List<PlaceCardModel> = emptyList(),
    /** Lo que sale bajo el buscador (006). */
    search: SearchUi = SearchUi(),
    /** Al abrir la pantalla Sitios: carga la lista de lugares. */
    onPlacesOpen: () -> Unit = {},
    onSelectPlace: (PlaceRef) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSaveAndOpen: (PlaceHit) -> Unit = {},
    onDeletePlace: (String) -> Unit = {},
    /** La sección «IA» de Ajustes (016 P3). */
    onAi: (AiAction) -> Unit = {},
    /** «Parte de la mañana» de Ajustes (018 P2). */
    onMorningToggle: (Boolean) -> Unit = {},
    onMorningTime: (Int) -> Unit = {},
    /** La hoja de la pregunta (018 P4): su estado y lo que se hace con ella. Solo en memoria (H7). */
    askState: AskState = AskState.Writing,
    onAsk: (String) -> Unit = {},
    onCancelAsk: () -> Unit = {},
    onCloseAsk: () -> Unit = {},
    /** Solo la demo: empieza con la hoja de la pregunta abierta. */
    initialAskOpen: Boolean = false,
    /** La zona del móvil, para «hora de allí». */
    phoneZone: ZoneId = ZoneId.systemDefault(),
    /** Solo la demo: empieza en la pantalla Sitios. */
    initialPlaces: Boolean = false,
    /** Solo la demo: empieza en el buscador con esto escrito. */
    initialQuery: String? = null,
    /** El permiso para animar los fundidos y la entrada (007); `null` = Quitar animaciones o ahorro de batería (H8). */
    skyClock: SkyClock? = null,
    /** La entrada al abrir (007, H15; [rememberMainIntro]): el velo y el astro que sube. `null` = sin entrada. */
    intro: IntroState? = null,
    /** La última temperatura vista de «donde estoy» (012 P9, H12): de dónde parte la cifra de la cabecera al abrir en frío. */
    lastSeenTemp: Int? = null,
    /** Solo la demo (009): fuerza esta foto del cielo, con lo que lleva encima. */
    demoPhoto: Pair<SkyPhoto, SkyEffects>? = null,
    /** Solo la demo (009): sin textos, solo la foto y el cristal de la zona de las tarjetas, para medir el contraste. */
    bare: Boolean = false,
    /** Solo la demo (009, P3): sin textos ni tarjetas, solo la foto y el velo de la cabecera, para medir su contraste. */
    bareHeader: Boolean = false,
    /** El actualizador (021): la línea de la principal y «Versión de la app» en Ajustes. Null: no se enseña nada. */
    update: UpdateState? = null,
    onUpdate: (UpdateAction) -> Unit = {},
    /** Solo la demo: empieza en Ajustes, en «Versión de la app». */
    initialUpdateFocus: Boolean = false,
) {
    val content = state as? UiState.Content
    // La foto del cielo (009): sin datos todavía, la de nubes, quieta (nunca un fondo liso).
    val photo = demoPhoto?.first ?: content?.let { skyPhotoNow(it.snapshot, it.now) } ?: LOADING_PHOTO
    val effects = demoPhoto?.second ?: content?.let { effectsNow(it.snapshot, it.now) } ?: SkyEffects.NONE
    // La luna de foto (007): la fase de hoy y el hemisferio del sitio, si la foto la lleva.
    val moon = rememberMoonLook(
        phase = content?.let { moonPhaseToday(it.snapshot, it.now) } ?: 0.5,
        south = content?.snapshot?.let(::isSouth) ?: false,
        needed = content != null && effects.moon != MoonShow.NONE,
    )
    val context = LocalContext.current
    val photos = remember(context) { PhotoCache(context) }
    // Las franjas de las postales de Sitios y de la hoja de borrar (010, P2).
    val bands = remember(context) { PhotoBands(context, capacity = 16) }
    val scroll = rememberScrollState()
    // Ajustes es otra «pantalla» dentro de esta, con la misma escena detrás (H11); volver la cierra. Las banderas
    // siguen siendo la verdad (se guardan); el host (011) anima hacia ellas y lleva el «atrás».
    var settingsOpen by rememberSaveable { mutableStateOf(initialSettings || initialUpdateFocus) }
    // Abierto desde la línea de versión nueva (021): Ajustes baja hasta «Versión de la app».
    var focusUpdate by rememberSaveable { mutableStateOf(initialUpdateFocus) }
    val showSettings = settingsOpen && state is UiState.Content
    // La pantalla Sitios (006), también otra «pantalla» dentro de esta; se abre desde cualquier estado.
    var placesOpen by rememberSaveable { mutableStateOf(initialPlaces || initialQuery != null) }
    val showPlaces = placesOpen && !showSettings
    val target = when {
        showSettings -> Screen.SETTINGS
        showPlaces -> Screen.PLACES
        else -> Screen.MAIN
    }
    // Hay una secundaria en pantalla (entrando, entera o saliendo): el cielo se para (la avisa el host).
    var secondaryActive by remember { mutableStateOf(target != Screen.MAIN) }
    LaunchedEffect(showPlaces) { if (showPlaces) onPlacesOpen() }
    // Otro sitio empieza arriba del todo.
    // Solo al pasar de un sitio a otro distinto: no al girar la pantalla ni al volver de un mensaje en el mismo sitio.
    val shownPlace = (state as? UiState.Content)?.place
    var lastPlace by rememberSaveable(stateSaver = PlaceRefSaver) { mutableStateOf(shownPlace) }
    LaunchedEffect(shownPlace) {
        if (shownPlace == null) return@LaunchedEffect
        if (lastPlace != null && lastPlace != shownPlace) scroll.scrollTo(0)
        lastPlace = shownPlace
    }
    // Las hojas del aviso, de 24 h y de los próximos días (012, P5: `ModalBottomSheet`) se componen aquí, junto a la ventana del
    // día y no dentro de la principal: así su velo cubre toda la pantalla. El aviso va por id (así se guarda al girar).
    var sheetWarningId by rememberSaveable { mutableStateOf<String?>(null) }
    var hourlySheet by rememberSaveable { mutableStateOf(initialHourlySheet) }
    var daysSheet by rememberSaveable { mutableStateOf(initialDaysSheet) }
    // La hoja de la pregunta (018 P4) vale solo para el sitio con que se abrió, y no se guarda al girar: girar o salir de la
    // app la cierra y cancela lo que haya en curso (el intento ya cuenta para el tope).
    var askFor by remember { mutableStateOf<PlaceRef?>(if (initialAskOpen) content?.place else null) }
    val askOpen = content != null && target == Screen.MAIN && askFor == content.place
    // Si el sitio que se ve cambia con la hoja abierta (el aviso de la mañana, Sitios), no queda apuntando al viejo: no reaparece.
    LaunchedEffect(content?.place) { if (askFor != null && askFor != content?.place) askFor = null }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && askFor != null) {
                askFor = null
                onCloseAsk()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Sin datos (carga, error) no hay hoja que enseñar, y al volver los datos no reaparece sola.
    LaunchedEffect(content == null) {
        if (content == null) {
            sheetWarningId = null
            hourlySheet = null
            daysSheet = null
        }
    }
    // La ventana del día (009, H13) va por fecha, no por posición (004, H4), encima de la principal, que sigue debajo:
    // al cerrarla, la principal sigue donde estaba. Sube desde abajo (P6, Cristal D22). Su «atrás» lo lleva ella.
    var dayDate by rememberSaveable { mutableStateOf(initialDayDate) }
    val openDay = dayDate.takeIf { state is UiState.Content && target == Screen.MAIN }
    // Se mueve la principal con datos; Ajustes y Sitios (también mientras entran y salen: la principal es entonces una
    // imagen que solo se mueve y se funde, 011), los mensajes y la carga, quietos (H7).
    val veil = colorResource(R.color.splash_background)
    WeatherTheme {
    Box(Modifier.fillMaxSize()) {
    SceneBackdrop(
        photo, effects, photos, clock = skyClock, moon = moon,
        intro = intro.takeIf { target == Screen.MAIN }, scroll = { scroll.value }, light = true,
    ) {
        // La lluvia contra el cristal de las tarjetas de la principal (P2, H8b): gotas fijas.
        CompositionLocalProvider(LocalGlassRain provides GlassRain(effects)) {
        // Ajustes y Sitios van sobre la escena desenfocada (010) y entran de lado (011); el resto, sobre el cielo de siempre.
        // Con una hoja o la ventana del día abiertas, TalkBack no recorre lo de detrás (ya no son ventanas modales).
        val coverOpen = openDay != null ||
            (content != null && target == Screen.MAIN && (sheetWarningId != null || hourlySheet != null || daysSheet != null || askOpen))
        CompositionLocalProvider(LocalWindowMotion provides (skyClock != null)) {
        Box(Modifier.fillMaxSize().then(if (coverOpen) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
        ScreenHost(
            target = target,
            onBack = {
                if (settingsOpen) {
                    settingsOpen = false
                    focusUpdate = false
                } else {
                    placesOpen = false
                }
            },
            onSecondaryActive = { secondaryActive = it },
            main = {
                // El velo de lectura de la cabecera sin tarjeta (009, H5), detrás de los textos; solo en la principal con datos.
                if (bareHeader || (content != null && !bare)) {
                    HeaderVeil(PHOTO_LOOKS[photo]?.headerVeil ?: 0f, scroll = { if (bareHeader) 0 else scroll.value })
                }
                Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
                    when {
                        bareHeader -> Box(Modifier.fillMaxSize().testTag("bare_header"))
                        bare -> BareGlass()
                        else -> MainState(state, onRefresh, onRetry, onRequestPermission, scroll,
                            onOpenDay = { dayDate = it },
                            onOpenSettings = { settingsOpen = true },
                            onWarning = { sheetWarningId = it }, onHourly = { hourlySheet = it }, onDays = { daysSheet = it },
                            onOpenPlaces = { placesOpen = true }, phoneZone = phoneZone,
                            lastSeenTemp = lastSeenTemp, travel = intro,
                            onAsk = { askFor = (state as? UiState.Content)?.place },
                            update = update,
                            onOpenUpdate = {
                                focusUpdate = true
                                settingsOpen = true
                            },
                            onUpdate = onUpdate)
                    }
                }
            },
            secondary = { screen ->
                SecondaryBackdrop(photo) {
                    Box(modifier = modifier.fillMaxSize().secondaryContent().safeDrawingPadding()) {
                        when (screen) {
                            Screen.SETTINGS -> (state as? UiState.Content)?.let {
                                SettingsScreen(
                                    it.usage, it.now, onBack = {
                                        settingsOpen = false
                                        focusUpdate = false
                                    },
                                    refreshes = it.refreshes,
                                    ai = it.ai, onAi = onAi,
                                    morning = it.morning, onMorningToggle = onMorningToggle, onMorningTime = onMorningTime,
                                    update = update, onUpdate = onUpdate, focusUpdate = focusUpdate,
                                )
                            }
                            Screen.PLACES -> PlacesScreen(
                                cards = placeCards,
                                search = search,
                                onBack = { placesOpen = false },
                                onSelect = {
                                    placesOpen = false
                                    onSelectPlace(it)
                                },
                                onQueryChange = onQueryChange,
                                onSaveAndOpen = {
                                    placesOpen = false
                                    onSaveAndOpen(it)
                                },
                                onDelete = onDeletePlace,
                                bands = bands,
                                initialQuery = initialQuery,
                                bare = bare,
                            )
                            Screen.MAIN -> Unit
                        }
                    }
                }
            },
        )
        }
        }
        }
        // Sin animaciones (sin reloj del cielo), la ventana, las hojas y el diálogo funden en lugar de moverse (H12).
        CompositionLocalProvider(LocalWindowMotion provides (skyClock != null)) {
            if (openDay != null) {
                DayWindow(
                    state as UiState.Content,
                    openDay,
                    onDateChange = { dayDate = it },
                    onClose = { dayDate = null },
                    photos = photos,
                )
            }
            if (content != null && target == Screen.MAIN) {
                MainSheets(
                    content,
                    sheetWarningId, { sheetWarningId = null },
                    hourlySheet, { hourlySheet = null },
                    daysSheet, { daysSheet = null },
                )
                if (askOpen) {
                    val snapshot = content.snapshot
                    val zone = ZoneId.of(snapshot.zoneId)
                    AskSheet(
                        place = content.savedName ?: snapshot.place.name,
                        provider = content.ai.provider,
                        state = askState,
                        dataAt = snapshot.fetchedAt,
                        lastDay = daysFrom(snapshot, content.now).lastOrNull()?.date?.let(java.time.LocalDate::parse)
                            ?: Instant.ofEpochSecond(content.now).atZone(zone).toLocalDate(),
                        now = content.now,
                        zone = zone,
                        onSend = onAsk,
                        onCancel = onCancelAsk,
                        onDismiss = {
                            askFor = null
                            onCloseAsk()
                        },
                    )
                }
            }
            // Solo con datos ya en pantalla (tras la primera carga), y una sola vez: lo decide quien llama.
            // Dentro de la escena, para que su cristal oscuro la difumine.
            if (showBatteryTip && state is UiState.Content) {
                BatteryDialog(onOpenSettings = onBatteryTipSettings, onDismiss = onBatteryTipDismiss)
            }
        }
    }
    // El velo del color de arranque, encima de todo: no recoge toques ni se lee (sin semántica propia).
    if (intro != null) {
        Box(Modifier.fillMaxSize().testTag("intro-veil").drawBehind { drawRect(veil, alpha = introVeil(intro.ms)) })
    }
    }
    }
}

@Composable
private fun MainState(
    state: UiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    scroll: ScrollState,
    onOpenDay: (date: String) -> Unit,
    onOpenSettings: () -> Unit,
    onWarning: (String) -> Unit,
    onHourly: (HourlySheetKind) -> Unit,
    onDays: (DaysSheetKind) -> Unit,
    onOpenPlaces: () -> Unit,
    phoneZone: ZoneId,
    lastSeenTemp: Int?,
    travel: IntroState?,
    onAsk: () -> Unit,
    update: UpdateState?,
    onOpenUpdate: () -> Unit,
    onUpdate: (UpdateAction) -> Unit,
) {
    // «Donde estoy ⌄» encima de los mensajes de «donde estoy»: sin datos aún se puede ir a los guardados (006).
    val here = stringResource(R.string.place_here)
    when (state) {
        UiState.Loading -> LoadingSkeleton()
        is UiState.SavedDownloading -> WithPlaceButton(state.place.name, PlaceKind.SAVED, onOpenPlaces) {
            MainSkeleton(stringResource(R.string.saved_downloading, state.place.name), withPlace = false)
        }
        is UiState.SavedNoData -> WithPlaceButton(state.place.name, PlaceKind.SAVED, onOpenPlaces) {
            MessageScreen(
                icon = R.drawable.ic_wx_cloud_off,
                title = stringResource(R.string.saved_no_data_title, state.place.name),
                body = stringResource(R.string.saved_no_data_body),
                buttonText = stringResource(R.string.retry),
                onButton = onRetry,
            )
        }
        UiState.Searching -> MainSkeleton(stringResource(R.string.searching_place))
        UiState.NeedsPermission -> WithPlaceButton(here, PlaceKind.PLAIN, onOpenPlaces) {
            MessageScreen(
                icon = R.drawable.ic_wx_location,
                title = stringResource(R.string.permission_title),
                body = stringResource(R.string.permission_body),
                buttonText = stringResource(R.string.grant_permission),
                onButton = onRequestPermission,
            )
        }
        UiState.NoConnection -> WithPlaceButton(here, PlaceKind.PLAIN, onOpenPlaces) {
            MessageScreen(
                icon = R.drawable.ic_wx_cloud_off,
                title = stringResource(R.string.offline_title),
                body = stringResource(R.string.offline_body),
                buttonText = stringResource(R.string.retry),
                onButton = onRetry,
            )
        }
        UiState.NoLocation -> WithPlaceButton(here, PlaceKind.PLAIN, onOpenPlaces) {
            MessageScreen(
                icon = R.drawable.ic_wx_location,
                title = stringResource(R.string.no_location_title),
                body = stringResource(R.string.no_location_body),
                buttonText = stringResource(R.string.retry),
                onButton = onRetry,
            )
        }
        is UiState.Content -> ContentScreen(
            state, onRefresh, scroll, onOpenDay, onOpenSettings, onWarning, onHourly, onDays,
            onOpenPlaces, phoneZone, lastSeenTemp, travel, onAsk,
            update = update, onOpenUpdate = onOpenUpdate, onUpdate = onUpdate,
        )
    }
}

/** Un mensaje con el nombre que se toca arriba, donde va en la cabecera (diseño B). */
@Composable
private fun WithPlaceButton(name: String, kind: PlaceKind, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        PlaceButton(name, kind, onClick, Modifier.padding(start = 22.dp, end = 14.dp, top = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentScreen(
    state: UiState.Content,
    onRefresh: () -> Unit,
    scroll: ScrollState,
    onOpenDay: (date: String) -> Unit,
    onOpenSettings: () -> Unit,
    onWarning: (String) -> Unit,
    onHourly: (HourlySheetKind) -> Unit,
    onDays: (DaysSheetKind) -> Unit,
    onOpenPlaces: () -> Unit,
    phoneZone: ZoneId,
    lastSeenTemp: Int?,
    travel: IntroState?,
    onAsk: () -> Unit,
    update: UpdateState? = null,
    onOpenUpdate: () -> Unit = {},
    onUpdate: (UpdateAction) -> Unit = {},
) {
    val snapshot = state.snapshot
    val zone = ZoneId.of(snapshot.zoneId)
    // El Resumen (009, H12): los avisos en vigor o de las próximas 24 h, el más grave arriba, y las frases.
    val warnings = remember(snapshot.warnings, state.now) { activeWarnings(snapshot.warnings, state.now) }
    val summary = remember(snapshot, state.now) { summaryLines(snapshot, state.now, zone) }
    // 016: el parte de la IA, ya comprobado y vigente; sin él (o sin su «hoy»), las frases de reglas de siempre.
    val reportToday = state.report?.today
    val todayDate = today(snapshot, state.now)?.date
    val reliabilityDays = state.reliability?.days.orEmpty().associateBy { it.date }
    // Hojas de 24 h (H6 de la 004): solo si la primera de las 24 horas trae ese dato (con datos de antes, no).
    val next24 = next24Hours(snapshot.hourly, state.now)
    val hasWind = next24.firstOrNull()?.windKmh != null
    val hasHumidity = next24.firstOrNull()?.humidityPct != null
    // Hojas de los próximos días (005, H9): solo si hay algún día completo que enseñar.
    val airDays = remember(snapshot, state.now) { airDays(snapshot, state.now) }
    val pollenDays = remember(snapshot, state.now) { pollenDays(snapshot, state.now) }
    val localToday = Instant.ofEpochSecond(state.now).atZone(zone).toLocalDate().toString()
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .testTag("main_scroll")
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 12.dp, end = 12.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // La entrada (H15): 0 la cabecera (solo aparece), 1 el Resumen, 2 las 24 horas, 3 los 10 días y, con índice 4,
            // las fichas y el pie (009: el orden de la pantalla D, H11).
            Box {
            Header(
                snapshot,
                state.now,
                status = when {
                    state.refreshing -> stringResource(R.string.updating)
                    state.offline -> offlineLabel(snapshot.fetchedAt, zone, state.now)
                    else -> null
                },
                offline = state.offline && !state.refreshing,
                todayLevel = todayDate?.let { reliabilityDays[it]?.level() },
                onPanelClick = { (todayDate ?: daysFrom(snapshot, state.now).firstOrNull()?.date)?.let(onOpenDay) },
                placeName = state.savedName ?: snapshot.place.name,
                saved = state.place is PlaceRef.Saved,
                onPlaceClick = onOpenPlaces,
                phoneZone = phoneZone,
                // La cifra solo viaja en «donde estoy»: la última vista es la de ese sitio.
                lastSeenTemp = lastSeenTemp,
                travel = travel.takeIf { state.place == PlaceRef.Here },
            )
            }
            // Sin avisos (o fuera de España), solo las frases; sin nada que decir (datos muy viejos), sin tarjeta, salvo que
            // haya píldora de la pregunta (018 P4): con clave y previsión guardada la tarjeta sale al menos con ella.
            if (warnings.isNotEmpty() || summary.isNotEmpty() || reportToday != null || state.ask != AskEntry.Hidden) {
                SummaryCard(
                    warnings = warnings,
                    lines = summary,
                    report = reportToday,
                    reportLabel = reportToday?.let { state.report?.let { r -> reportLabel(r, zone) } },
                    now = state.now,
                    zone = zone,
                    onWarningClick = { onWarning(it.id) },
                    modifier = Modifier.padding(top = 12.dp),
                    savedLabel = if (state.offline) savedWarningsLabel(snapshot.warningsFetchedAt ?: snapshot.fetchedAt, state.now, zone) else null,
                    ask = state.ask,
                    onAsk = onAsk,
                    askPlace = state.savedName ?: snapshot.place.name,
                )
            }
            GlassCard(stringResource(R.string.next_24_hours), Modifier, seed = 1) {
                HourlyChart(withCurrentAt(next24, 0, snapshot), zone)
            }
            GlassCard(stringResource(R.string.next_10_days), Modifier, seed = 2) {
                DailyList(
                    days = daysFrom(snapshot, state.now),
                    hourly = snapshot.hourly,
                    zone = zone,
                    today = todayDate.orEmpty(),
                    nowC = snapshot.current.temperatureC,
                    reliability = state.reliability,
                    onDayClick = onOpenDay,
                )
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WeatherCards(
                snapshot,
                state.now,
                onWindClick = { onHourly(HourlySheetKind.WIND) }.takeIf { hasWind },
                onHumidityClick = { onHourly(HourlySheetKind.HUMIDITY) }.takeIf { hasHumidity },
                onAirClick = { onDays(DaysSheetKind.AIR) }.takeIf { airDays.isNotEmpty() },
                onPollenClick = { onDays(DaysSheetKind.POLLEN) }.takeIf { pollenDays.isNotEmpty() },
            )
            // 021: la versión nueva, en una línea discreta al pie, junto a Ajustes, adonde lleva.
            val newRelease = update?.available
            if (update != null && update.showNotice && newRelease != null) {
                UpdateNotice(newRelease, onOpen = onOpenUpdate, onDismiss = { onUpdate(UpdateAction.Dismiss) }, Modifier.padding(top = 6.dp))
            }
            // 021: «Datos del tiempo de Open-Meteo.com» es un enlace a su web, como piden sus términos.
            val openMeteo = stringResource(R.string.footer_openmeteo)
            val footer = stringResource(R.string.footer_sources, openMeteo)
            Text(
                text = remember(footer, openMeteo) {
                    androidx.compose.ui.text.buildAnnotatedString {
                        val at = footer.indexOf(openMeteo)
                        append(footer)
                        if (at >= 0) {
                            addLink(
                                androidx.compose.ui.text.LinkAnnotation.Url(
                                    "https://open-meteo.com/",
                                    androidx.compose.ui.text.TextLinkStyles(
                                        androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                                    ),
                                ),
                                at,
                                at + openMeteo.length,
                            )
                        }
                    }
                },
                color = OnSky.Secondary,
                style = CristalType.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            SettingsPill(onOpenSettings, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

/**
 * Las hojas del aviso, de 24 h y de los próximos días (012, P5), cada una en su `ModalBottomSheet`. Si el dato que enseña ya
 * no está, no se compone.
 */
@Composable
private fun MainSheets(
    state: UiState.Content,
    warningId: String?,
    onWarningDismiss: () -> Unit,
    hourly: HourlySheetKind?,
    onHourlyDismiss: () -> Unit,
    days: DaysSheetKind?,
    onDaysDismiss: () -> Unit,
) {
    val snapshot = state.snapshot
    val zone = ZoneId.of(snapshot.zoneId)
    val warning = warningId?.let { id -> activeWarnings(snapshot.warnings, state.now).firstOrNull { it.id == id } }
    if (warning != null) {
        WarningSheet(warning, state.now, zone, onDismiss = onWarningDismiss, plain = state.report?.warnings?.get(warning.id))
    }
    val first = next24Hours(snapshot.hourly, state.now).firstOrNull()
    if (hourly != null && (if (hourly == HourlySheetKind.WIND) first?.windKmh else first?.humidityPct) != null) {
        HourlySheet(hourly, snapshot.hourly, state.now, zone, onDismiss = onHourlyDismiss)
    }
    val localToday = Instant.ofEpochSecond(state.now).atZone(zone).toLocalDate().toString()
    when (days) {
        DaysSheetKind.AIR -> airDays(snapshot, state.now).let { if (it.isNotEmpty()) AirSheet(it, localToday, onDismiss = onDaysDismiss) }
        DaysSheetKind.POLLEN -> pollenDays(snapshot, state.now).let { if (it.isNotEmpty()) PollenSheet(it, localToday, onDismiss = onDaysDismiss) }
        null -> Unit
    }
}

/** «⚙ Ajustes» bajo las fuentes, al final de la pantalla (diseño B): se mira poco y no ocupa sitio arriba. */
@Composable
private fun SettingsPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .padding(top = 2.dp)
            .minimumInteractiveComponentSize()
            .translucent(RoundedCornerShape(percent = 50))
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = press, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(painterResource(R.drawable.ic_wx_settings), contentDescription = null, Modifier.size(16.dp))
        Text(text = stringResource(R.string.settings), color = Color.White, style = sceneText(CristalType.labelLarge))
    }
}

/** Tarjeta de cristal (24 horas, 10 días), con su título tal cual (009, H11); con lluvia, el agua encima ([seed]). */
@Composable
private fun GlassCard(title: String, modifier: Modifier = Modifier, seed: Int = 0, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .translucent(RoundedCornerShape(26.dp))
            .rainOnGlass(seed, RoundedCornerShape(26.dp))
            .padding(top = 12.dp, bottom = 10.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            style = sceneText(SectionTitle),
            modifier = Modifier.padding(horizontal = 18.dp).padding(top = 2.dp, bottom = 6.dp),
        )
        content()
    }
}

/** Para guardar el último sitio visto al girar la pantalla: «here» o el id del guardado. */
private val PlaceRefSaver = androidx.compose.runtime.saveable.Saver<PlaceRef?, String>(
    save = { ref -> when (ref) { null -> ""; PlaceRef.Here -> "here"; is PlaceRef.Saved -> "s:" + ref.id } },
    restore = { s -> when { s == "here" -> PlaceRef.Here; s.startsWith("s:") -> PlaceRef.Saved(s.removePrefix("s:")); else -> null } },
)

/**
 * La demo con `--ez bare true` (009, H5): sin textos, un solo panel translúcido (012 P4: sin desenfoque) sobre toda la zona de las tarjetas (del
 * 27 % de la pantalla hacia abajo, con el margen de las tarjetas), para medir con `tools/contraste.py` el texto blanco
 * sobre el cristal de cada foto.
 */
@Composable
private fun BareGlass() {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("bare_glass")) {
        Box(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = maxHeight * 0.27f)
                .fillMaxSize()
                .translucent(RoundedCornerShape(24.dp)),
        )
    }
}
