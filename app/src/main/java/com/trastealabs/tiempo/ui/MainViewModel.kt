package com.trastealabs.tiempo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trastealabs.tiempo.data.MIN_QUERY
import com.trastealabs.tiempo.data.MorningSettings
import com.trastealabs.tiempo.data.PlaceHit
import com.trastealabs.tiempo.data.PlaceSearch
import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.ReportStore
import com.trastealabs.tiempo.data.ai.AiFailure
import com.trastealabs.tiempo.data.ai.AI_DAILY_ASK
import com.trastealabs.tiempo.data.ai.AiSettings
import com.trastealabs.tiempo.data.ai.KeyVault
import com.trastealabs.tiempo.data.ai.ReportScope
import com.trastealabs.tiempo.data.SearchResults
import com.trastealabs.tiempo.data.key
import com.trastealabs.tiempo.domain.AiProvider
import com.trastealabs.tiempo.domain.AiReport
import com.trastealabs.tiempo.domain.AskAnswer
import com.trastealabs.tiempo.domain.AskFailure
import com.trastealabs.tiempo.domain.AskResult
import com.trastealabs.tiempo.domain.reportFacts
import com.trastealabs.tiempo.domain.visibleReport
import com.trastealabs.tiempo.domain.ApiUsage
import com.trastealabs.tiempo.domain.MAX_SAVED
import com.trastealabs.tiempo.domain.normalizeQuery
import com.trastealabs.tiempo.domain.PlaceCardModel
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.nextScreenTick
import com.trastealabs.tiempo.domain.placeCard
import com.trastealabs.tiempo.domain.savedRefreshDue
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.validReliability
import com.trastealabs.tiempo.refresh.RefreshResult
import com.trastealabs.tiempo.refresh.ReportRepository
import com.trastealabs.tiempo.refresh.ReliabilityRepository
import com.trastealabs.tiempo.refresh.SavedPlacesRepository
import com.trastealabs.tiempo.refresh.Trigger
import com.trastealabs.tiempo.refresh.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

sealed interface UiState {
    /** Leyendo el fichero guardado (milisegundos): solo el fondo, sin texto. */
    data object Loading : UiState

    /** Primera vez, descargando: esqueleto con "Buscando dónde estás…". */
    data object Searching : UiState
    data object NeedsPermission : UiState
    data object NoConnection : UiState
    data object NoLocation : UiState

    data class Content(
        val snapshot: WeatherSnapshot,
        val now: Long,
        val offline: Boolean,
        val refreshing: Boolean,
        /** Ya filtrada con validReliability (H6): null = sin iconos ni hoja. */
        val reliability: Reliability? = null,
        /** Llamadas a Open-Meteo para Ajustes (H11). */
        val usage: ApiUsage = ApiUsage(),
        /** Los refrescos de «donde estoy» para Ajustes (015 P2, H11). */
        val refreshes: List<RefreshLogEntry> = emptyList(),
        /** El sitio que se ve (006): «donde estoy» o uno guardado. */
        val place: PlaceRef = PlaceRef.Here,
        /** El nombre del guardado; null en «donde estoy». */
        val savedName: String? = null,
        /** La sección «IA» de Ajustes (016 P3). */
        val ai: AiPanel = AiPanel(),
        /** El parte de la IA de este sitio, ya pasado por `visibleReport` (016 P4); null = se ven las frases de reglas. */
        val report: AiReport? = null,
        /** «Parte de la mañana» de Ajustes (018 P2). */
        val morning: MorningPanel = MorningPanel(),
        /** La píldora de la pregunta (018 P4): oculta sin clave o sin horas por delante; apagada con el tope del día. */
        val ask: AskEntry = AskEntry.Hidden,
    ) : UiState

    /** Un guardado sin datos, descargando: «Descargando el tiempo de Granada…» (006). */
    data class SavedDownloading(val place: SavedPlace) : UiState

    /** Un guardado sin datos y el intento falló: «Aún no tengo datos de Granada» (006). */
    data class SavedNoData(val place: SavedPlace) : UiState
}

/** La entrada de la pregunta libre en el Resumen (018 P4, H1, H5, H7). */
sealed interface AskEntry {
    /** Sin clave, o sin horas por delante con que contestar: no hay píldora. */
    data object Hidden : AskEntry

    data object Open : AskEntry

    /** Con las [used] preguntas de hoy ya hechas: la píldora sigue, apagada. */
    data class Capped(val used: Int) : AskEntry
}

/** Lo que enseña la hoja de la pregunta. Solo en memoria: nada de esto se guarda (H7). */
sealed interface AskState {
    data object Writing : AskState
    data class Asking(val question: String) : AskState
    data class Answered(val question: String, val answer: AskAnswer) : AskState
    data class Failed(val question: String, val failure: AskFailure) : AskState
}

/** El estado de lo que sale bajo el buscador (006). */
sealed interface SearchState {
    /** Menos de 2 letras: la ayuda de siempre. */
    data object Hint : SearchState

    /** La primera búsqueda tras arrancar, mientras se carga la lista (solo si tarda más de 150 ms, H4). */
    data object Loading : SearchState
    data class Results(val results: SearchResults) : SearchState
    data object NoResults : SearchState
}

data class SearchUi(
    val query: String = "",
    val state: SearchState = SearchState.Hint,
    /** Ya hay 10 guardados: no se deja guardar otro (H6). */
    val full: Boolean = false,
    /** Claves ([key]) de los guardados, para marcar «✓ Guardado». */
    val savedKeys: Set<String> = emptySet(),
)

class MainViewModel(
    private val repository: WeatherRepository,
    private val reliability: ReliabilityRepository,
    private val hasLocationPermission: () -> Boolean,
    private val usage: StateFlow<ApiUsage> = MutableStateFlow(ApiUsage()),
    /** El registro de refrescos (015 P2); vacío en los tests de antes. */
    private val refreshLog: Flow<List<RefreshLogEntry>> = flowOf(emptyList()),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Los sitios guardados (006); null en los tests de antes, y entonces solo hay «donde estoy». */
    private val saved: SavedPlacesRepository? = null,
    /** La búsqueda de lugares (006); null en los tests de antes, y entonces el buscador se queda en la ayuda. */
    private val placeSearch: PlaceSearch? = null,
    /** El nombre de la postal de «donde estoy» sin ningún dato aún (`R.string.place_here`); vacío en los tests. */
    private val hereName: String = "",
    /** El parte con IA (016 P3); null en los tests de antes, y entonces no se pide nada. */
    private val reports: ReportRepository? = null,
    /** El parte de «donde estoy» (`ai_report.json`); el de un guardado sale de [saved]. */
    private val reportStore: ReportStore? = null,
    private val aiSettings: AiSettings? = null,
    private val keyVault: KeyVault? = null,
    /** Donde se toca el Keystore (cifrar y descifrar), lejos del hilo principal. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** La notificación de la mañana (018 P2); null en los tests de antes, y entonces la sección sale apagada. */
    private val morning: MorningSettings? = null,
    /** ¿Deja el sistema publicar notificaciones? (el permiso se puede quitar desde los ajustes del móvil). */
    private val notificationsAllowed: () -> Boolean = { true },
    /** Tras cambiar el interruptor o la hora: vuelve a armar la alarma de la mañana. */
    private val onMorningChanged: () -> Unit = {},
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val lastResult = MutableStateFlow<RefreshResult?>(null)
    private val permission = MutableStateFlow(hasLocationPermission())

    /** Los guardados que están descargando ahora mismo. */
    private val savedBusy = MutableStateFlow<Set<String>>(emptySet())

    private val _selected = MutableStateFlow<PlaceRef>(PlaceRef.Here)

    /**
     * El sitio que se ve. Empieza siempre en «donde estoy» y no se guarda en disco: un ViewModel nuevo (la app
     * arranca) vuelve a «donde estoy» (H9).
     */
    val selected: StateFlow<PlaceRef> = _selected.asStateFlow()

    /**
     * Un latido en cada cambio visible sin datos nuevos (H8): medianoche, amanecer, anochecer y salida o puesta de la
     * luna. Con la app abierta, «hoy», el contador de Ajustes y la ficha de sol o luna cambian sin esperar a nada.
     * Con cada snapshot nuevo se vuelve a calcular el siguiente. Va con el snapshot del sitio que se ve, así que la
     * medianoche es la suya (006).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ticks(states: Flow<StoredState>): Flow<Unit> =
        states.map { it.snapshot }.distinctUntilChanged().transformLatest { snapshot ->
            while (true) {
                emit(Unit)
                val now = nowSeconds()
                delay((nextScreenTick(now, zone(), snapshot) - now + 1).coerceAtLeast(1) * 1000)
            }
        }

    private fun Flow<UiState>.withTicks(states: Flow<StoredState>): Flow<UiState> =
        combine(ticks(states)) { state, _ -> if (state is UiState.Content) state.copy(now = nowSeconds()) else state }

    private val hereState: Flow<UiState> =
        combine(repository.state, refreshing, lastResult, permission, reliability.state) { stored, busy, result, allowed, rel ->
            val snapshot = stored.snapshot
            when {
                snapshot != null -> nowSeconds().let { now ->
                    UiState.Content(
                        snapshot, now, stored.lastAttemptFailed, busy,
                        reliability = validReliability(rel.reliability, snapshot, now),
                    )
                }
                !allowed -> UiState.NeedsPermission
                busy || result == null -> UiState.Searching
                result == RefreshResult.NoLocation -> UiState.NoLocation
                result == RefreshResult.Offline || stored.lastAttemptFailed -> UiState.NoConnection
                else -> UiState.Searching
            }
        }.withTicks(repository.state)

    /** Solo los ficheros de ese guardado: al cambiar de sitio nunca se ven datos del otro (H10). */
    private fun savedUi(id: String): Flow<UiState> {
        val places = saved?.places ?: return flowOf(UiState.Loading)
        val stored = repository.savedState(id)
        return combine(
            stored,
            reliability.savedState(id),
            savedBusy.map { id in it }.distinctUntilChanged(),
            places.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged(),
        ) { st, rel, busy, place ->
            val snapshot = st.snapshot
            when {
                place == null -> UiState.Loading
                snapshot != null -> nowSeconds().let { now ->
                    UiState.Content(
                        snapshot, now, st.lastAttemptFailed, busy,
                        reliability = validReliability(rel.reliability, snapshot, now),
                        place = PlaceRef.Saved(id),
                        savedName = place.name,
                    )
                }
                !busy && st.lastAttemptFailed -> UiState.SavedNoData(place)
                else -> UiState.SavedDownloading(place)
            }
        }.withTicks(stored)
    }

    /** Lo de la sección «IA» que no vive en [AiSettings]: las pistas de las claves y la prueba en curso. */
    private data class AiLocal(
        val hints: Map<AiProvider, String> = emptyMap(),
        val testing: Boolean = false,
        val testText: String? = null,
    )

    private val aiLocal = MutableStateFlow(AiLocal())
    private val vaultLock = Mutex()

    private val aiPanel: Flow<AiPanel> = aiSettings?.let { s ->
        combine(s.provider, s.usage, s.status, aiLocal) { provider, used, status, local ->
            AiPanel(provider, local.hints, used, status, local.testing, local.testText)
        }
    } ?: flowOf(AiPanel())

    init {
        viewModelScope.launch(ioDispatcher) { vaultLock.withLock { reloadHints() } }
    }

    private val morningPermission = MutableStateFlow(notificationsAllowed())

    private val morningPanel: Flow<MorningPanel> = morning?.let { m ->
        combine(m.enabled, m.minuteOfDay, morningPermission, refreshLog.onStart { emit(emptyList()) }) { enabled, minute, allowed, entries ->
            morningPanelOf(enabled, minute, allowed, entries)
        }
    } ?: flowOf(MorningPanel())

    /** Al volver a la app: el permiso de notificaciones pudo cambiar desde los ajustes del móvil. */
    fun morningPermissionMayHaveChanged() {
        morningPermission.value = notificationsAllowed()
    }

    /** El interruptor de «Parte de la mañana». Encender con la hora de hoy ya pasada empieza mañana. */
    fun setMorning(on: Boolean) {
        val m = morning ?: return
        m.setEnabled(on, nowSeconds(), zone())
        morningPermission.value = notificationsAllowed()
        onMorningChanged()
    }

    /** La hora de la notificación, en minutos desde medianoche. */
    fun setMorningTime(minuteOfDay: Int) {
        val m = morning ?: return
        m.setMinuteOfDay(minuteOfDay, nowSeconds(), zone())
        onMorningChanged()
    }

    /** El parte guardado del sitio que se ve, con el sitio al que pertenece. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val shownReport: Flow<Pair<PlaceRef, AiReport?>> = _selected.flatMapLatest { ref ->
        (reportStoreOf(ref)?.state ?: flowOf(null)).map { ref to it }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> =
        _selected.flatMapLatest { ref ->
            when (ref) {
                PlaceRef.Here -> hereState
                is PlaceRef.Saved -> savedUi(ref.id)
            }
        }.combine(usage) { state, calls ->
            if (state is UiState.Content) state.copy(usage = calls) else state
        }.combine(refreshLog.onStart { emit(emptyList()) }) { state, refreshes ->
            // El registro se lee del disco: la pantalla no espera a esa lectura, la lista llega cuando esté.
            if (state is UiState.Content) state.copy(refreshes = refreshes) else state
        }.combine(aiPanel) { state, ai ->
            if (state is UiState.Content) state.copy(ai = ai) else state
        }.combine(morningPanel) { state, panel ->
            if (state is UiState.Content) state.copy(morning = panel) else state
        }.combine(shownReport) { state, shown ->
            // Solo el parte del sitio que se ve (con la pareja se descarta el del anterior al cambiar de sitio), y solo si
            // sigue valiendo con la previsión y la hora de ahora (H8). Leer lo guardado no llama a ningún proveedor (H5).
            if (state is UiState.Content && shown.first == state.place) {
                val zone = ZoneId.of(state.snapshot.zoneId)
                state.copy(report = visibleReport(shown.second, reportFacts(state.snapshot, state.now, zone)?.basis))
            } else state
        }.map { state ->
            if (state is UiState.Content) state.copy(ask = askEntryOf(state)) else state
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /**
     * La píldora de la pregunta (018 P4): con clave del proveedor elegido y algo con que contestar. Sin horas por
     * delante (`reportFacts` nulo) `ask` no llamaría y reintentar no lo arreglaría: no hay píldora. Con 20 hechas, apagada.
     */
    private fun askEntryOf(state: UiState.Content): AskEntry {
        if (reports == null || state.ai.hints[state.ai.provider] == null) return AskEntry.Hidden
        if (reportFacts(state.snapshot, state.now, ZoneId.of(state.snapshot.zoneId)) == null) return AskEntry.Hidden
        val used = state.ai.usage.at(state.now, zone()).dayAsk
        return if (used >= AI_DAILY_ASK) AskEntry.Capped(used) else AskEntry.Open
    }

    private val _askState = MutableStateFlow<AskState>(AskState.Writing)

    /** La hoja de la pregunta: solo en memoria (H7). Al cambiar de sitio o cerrarla se olvida. */
    val askState: StateFlow<AskState> = _askState.asStateFlow()

    private var askJob: Job? = null

    /** Pregunta por el sitio que se ve, con su previsión guardada (no descarga nada). Una pregunta a la vez. */
    fun ask(question: String) {
        val repo = reports ?: return
        val text = question.trim()
        if (text.isEmpty() || _askState.value is AskState.Asking) return
        val ref = _selected.value
        _askState.value = AskState.Asking(text)
        askJob = viewModelScope.launch {
            val result = try {
                val snapshot = storedOf(ref).first().snapshot
                if (snapshot == null) AskResult.Failed(AskFailure.OTHER) else withContext(ioDispatcher) { repo.ask(snapshot, text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AskResult.Failed(AskFailure.OTHER)
            }
            // Si mientras tanto se cambió de sitio, se cerró o se canceló, esta respuesta ya no es de nadie.
            if (_selected.value == ref && _askState.value is AskState.Asking) {
                _askState.value = when (result) {
                    is AskResult.Ok -> AskState.Answered(text, result.answer)
                    is AskResult.Failed -> AskState.Failed(text, result.failure)
                }
            }
        }
    }

    /** «Cancelar»: corta la llamada (el intento ya cuenta para el tope) y vuelve a escribir. */
    fun cancelAsk() {
        askJob?.cancel()
        askJob = null
        _askState.value = AskState.Writing
    }

    /** Cierra la hoja: cancela lo que haya en curso y olvida pregunta y respuesta (H7). */
    fun closeAsk() = cancelAsk()

    /** Las postales de la pantalla Sitios: «donde estoy» primero y luego los guardados en su orden (006). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val placeCards: StateFlow<List<PlaceCardModel>> = run {
        val savedStates: Flow<List<Pair<SavedPlace, StoredState>>> = (saved?.places ?: flowOf(emptyList()))
            .flatMapLatest { list ->
                if (list.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(list.map { p -> repository.savedState(p.id).map { p to it } }) { it.toList() }
                }
            }
        // Con el latido de «donde estoy» (medianoche incluida), para que «hoy» y «Datos de ayer» cambien solos.
        combine(repository.state, permission, _selected, savedStates, ticks(repository.state)) { here, allowed, shown, others, _ ->
            val now = nowSeconds()
            val phone = zone()
            listOf(
                placeCard(
                    PlaceRef.Here, here.snapshot?.place?.name ?: hereName, here, now, phone,
                    shown = shown == PlaceRef.Here, noPermission = !allowed,
                ),
            ) + others.map { (p, st) ->
                val ref = PlaceRef.Saved(p.id)
                placeCard(ref, p.name, st, now, phone, shown = shown == ref, noPermission = false)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    private val query = MutableStateFlow("")

    /**
     * Lo que sale bajo el buscador. Se recalcula con cada letra (H2) y solo en el móvil (H5); una letra más cancela la
     * búsqueda anterior.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val search: StateFlow<SearchUi> = run {
        val states: Flow<Pair<String, SearchState>> = query.transformLatest { q ->
            val search = placeSearch
            if (search == null || normalizeQuery(q).length < MIN_QUERY) {
                emit(q to SearchState.Hint)
                return@transformLatest
            }
            coroutineScope {
                // Si la lista no se puede leer (asset roto), «sin resultados» en vez de cerrar la app.
                val running = async {
                    try {
                        search.search(q)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        SearchResults(emptyList(), emptyList())
                    }
                }
                if (!search.isLoaded && withTimeoutOrNull(LOADING_AFTER_MS) { running.await() } == null) {
                    emit(q to SearchState.Loading)
                }
                val results = running.await()
                emit(q to if (results.isEmpty) SearchState.NoResults else SearchState.Results(results))
            }
        }
        combine(states, saved?.places ?: flowOf(emptyList())) { (q, state), list ->
            SearchUi(q, state, full = list.size >= MAX_SAVED, savedKeys = list.mapTo(HashSet()) { it.key })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi())
    }

    /** Lo escrito en el buscador, letra a letra. */
    fun setQuery(q: String) {
        query.value = q
    }

    /** Guarda un resultado y lo abre; si ya estaba, solo lo abre; con 10 guardados, nada (H6). */
    fun saveAndOpen(hit: PlaceHit) {
        val repo = saved ?: return
        viewModelScope.launch {
            when (val r = repo.save(hit.name, hit.region, hit.countryCode, hit.position)) {
                is SavedPlacesRepository.SaveResult.Saved -> select(PlaceRef.Saved(r.place.id))
                is SavedPlacesRepository.SaveResult.AlreadySaved -> select(PlaceRef.Saved(r.place.id))
                SavedPlacesRepository.SaveResult.Full -> Unit
            }
        }
    }

    /** Cambia el sitio que se ve. Un guardado se vuelve a pedir si lo suyo tiene más de 30 min (H11). */
    fun select(ref: PlaceRef) {
        if (ref != _selected.value) closeAsk()
        _selected.value = ref
        if (ref is PlaceRef.Saved) refreshSavedIfStale(ref.id)
    }

    /** Al abrir o volver a la app: «donde estoy» como siempre; un guardado, solo si lo suyo tiene más de 30 min. */
    fun onStart() {
        when (val ref = _selected.value) {
            PlaceRef.Here -> refresh()
            is PlaceRef.Saved -> {
                permission.value = hasLocationPermission()
                refreshSavedIfStale(ref.id)
            }
        }
    }

    /**
     * El sitio que se ve. «Donde estoy»: al abrir la app, al deslizar (H5), al reintentar y al conceder el permiso.
     * Un guardado: siempre (deslizar, reintentar). La fiabilidad va después y sin la ruedita: la previsión no la
     * espera (H9).
     */
    fun refresh() {
        val allowed = hasLocationPermission()
        permission.value = allowed
        when (val ref = _selected.value) {
            PlaceRef.Here -> refreshHere(allowed)
            is PlaceRef.Saved -> refreshSaved(ref.id)
        }
    }

    /** Borra un guardado con sus datos (H7). Si era el que se ve, se vuelve a «donde estoy». */
    fun delete(id: String) {
        val repo = saved ?: return
        if (_selected.value == PlaceRef.Saved(id)) {
            closeAsk()
            _selected.value = PlaceRef.Here
        }
        viewModelScope.launch { repo.delete(id) }
    }

    private fun refreshHere(allowed: Boolean) {
        if (!allowed || refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            // El resultado se apunta antes de quitar la ruedita, para no enseñar un instante el estado anterior.
            val result = try {
                repository.refresh(Trigger.APP).also { lastResult.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Disco lleno o similar: la app sigue con lo que tenía.
                RefreshResult.Offline.also { lastResult.value = it }
            } finally {
                refreshing.value = false
            }
            if (result is RefreshResult.Ok) {
                reliability.refreshIfDue(nowSeconds())
                requestReport(PlaceRef.Here)
            }
        }
    }

    private fun refreshSavedIfStale(id: String) {
        viewModelScope.launch {
            if (savedRefreshDue(repository.savedState(id).first(), nowSeconds())) refreshSaved(id)
        }
    }

    private fun refreshSaved(id: String) {
        val repo = saved ?: return
        if (id in savedBusy.value) return
        savedBusy.update { it + id }
        viewModelScope.launch {
            val place = repo.find(id)
            if (place == null) {
                savedBusy.update { it - id }
                return@launch
            }
            val result = try {
                repository.refreshSaved(place)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RefreshResult.Offline
            } finally {
                savedBusy.update { it - id }
            }
            if (result is RefreshResult.Ok) {
                reliability.refreshSavedIfDue(place, nowSeconds())
                requestReport(PlaceRef.Saved(id))
            }
        }
    }

    /** El parte de [ref] tras un refresco bueno, si toca. Nunca lanza ni cambia el resultado del refresco (H12). */
    private suspend fun requestReport(ref: PlaceRef) {
        val repo = reports ?: return
        val store = reportStoreOf(ref) ?: return
        val snapshot = storedOf(ref).first().snapshot ?: return
        val scope = if (ref is PlaceRef.Saved) ReportScope.SAVED else ReportScope.HERE
        // Fuera del hilo principal: mirar la clave descifra con el Keystore.
        withContext(ioDispatcher) { repo.refreshIfDue(snapshot, store, scope) }
        // Si el guardado se borró mientras la IA contestaba, no se deja su carpeta con un parte suelto.
        if (ref is PlaceRef.Saved && saved?.find(ref.id) == null) saved?.stores?.delete(ref.id)
    }

    private fun storedOf(ref: PlaceRef): Flow<StoredState> = when (ref) {
        PlaceRef.Here -> repository.state
        is PlaceRef.Saved -> repository.savedState(ref.id)
    }

    private fun reportStoreOf(ref: PlaceRef): ReportStore? = when (ref) {
        PlaceRef.Here -> reportStore
        is PlaceRef.Saved -> saved?.stores?.report(ref.id)
    }

    /** Las acciones de la sección «IA» de Ajustes (016 P3). */
    fun onAi(action: AiAction) {
        when (action) {
            is AiAction.Choose -> aiSettings?.setProvider(action.provider)
            is AiAction.SaveKey -> changeKey { it.put(action.provider, action.key) }
            is AiAction.ClearKey -> changeKey { it.clear(action.provider) }
            AiAction.Test -> testReport()
        }
    }

    /** Cambia una clave y vuelve a leer las pistas. Si el Keystore falla, no se cierra la app: queda dicho en el último intento. */
    private fun changeKey(change: (KeyVault) -> Unit) {
        val vault = keyVault ?: return
        viewModelScope.launch(ioDispatcher) {
            vaultLock.withLock {
                try {
                    change(vault)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aiSettings?.setStatus(nowSeconds(), AiFailure.NO_KEY)
                }
                reloadHints()
            }
        }
    }

    private fun reloadHints() {
        val vault = keyVault ?: return
        val hints = AiProvider.entries.mapNotNull { p -> runCatching { vault.hint(p) }.getOrNull()?.let { p to it } }.toMap()
        aiLocal.update { it.copy(hints = hints) }
    }

    /** «Probar ahora»: una llamada sin tope ni espera sobre el sitio que se ve. El motivo de un fallo queda en el último intento. */
    private fun testReport() {
        val repo = reports ?: return
        val ref = _selected.value
        val store = reportStoreOf(ref) ?: return
        if (aiLocal.value.testing) return
        aiLocal.update { it.copy(testing = true, testText = null) }
        viewModelScope.launch {
            var text: String? = null
            try {
                val snapshot = storedOf(ref).first().snapshot
                if (snapshot != null) {
                    text = withContext(ioDispatcher) { repo.testNow(snapshot, store) }.getOrNull()?.let { r ->
                        r.today ?: r.days.values.firstOrNull() ?: r.warnings.values.firstOrNull()
                    }
                }
            } finally {
                aiLocal.update { it.copy(testing = false, testText = text) }
            }
        }
    }

    private companion object {
        /** H4: «buscando…» solo si la primera búsqueda tarda más que esto. */
        const val LOADING_AFTER_MS = 150L
    }
}
