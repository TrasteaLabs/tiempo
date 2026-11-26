package com.trastealabs.tiempo.demo

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.trastealabs.tiempo.WeatherApp
import com.trastealabs.tiempo.data.MIN_QUERY
import com.trastealabs.tiempo.data.key
import com.trastealabs.tiempo.domain.AskKind
import com.trastealabs.tiempo.domain.MAX_SAVED
import com.trastealabs.tiempo.domain.PlaceRef
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.normalizeQuery
import com.trastealabs.tiempo.domain.placeCard
import com.trastealabs.tiempo.ui.AiPanel
import com.trastealabs.tiempo.ui.AskEntry
import com.trastealabs.tiempo.ui.AskState
import com.trastealabs.tiempo.ui.BatteryDialog
import com.trastealabs.tiempo.ui.UpdateAction
import com.trastealabs.tiempo.ui.theme.WeatherTheme
import com.trastealabs.tiempo.ui.SearchState
import com.trastealabs.tiempo.ui.SearchUi
import kotlinx.coroutines.launch
import com.trastealabs.tiempo.ui.DaysSheetKind
import com.trastealabs.tiempo.ui.HourlySheetKind
import com.trastealabs.tiempo.ui.MainScreen
import com.trastealabs.tiempo.ui.UiState
import com.trastealabs.tiempo.ui.rememberIntro
import com.trastealabs.tiempo.ui.scene.AndroidMotionSettings
import com.trastealabs.tiempo.ui.scene.rememberSkyClock
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import kotlin.concurrent.thread

/**
 * Solo debug. La pantalla principal con datos inventados de una escena, sin refrescar ni tocar
 * `snapshot.json`:
 * `adb shell am start -S -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoActivity --es scene STORM [--es badge WARNING|OFFLINE]
 * [--es rel none] [--ei day 2] [--es screen settings] [--es clock 23:04] [--es open wind|humidity|air|pollen]
 * [--es air good|fair|moderate|poor|very_poor|extreme|none] [--es pollen none|olive|grass|both]`. Por defecto, con la fiabilidad de la
 * maqueta del 003; `rel none` la quita, `day <n>` abre la ventana del día n (0 = hoy), `screen settings` empieza en
 * Ajustes (214 llamadas hoy y 1.046 este mes y un día inventado de refrescos, 015) y `clock HH:mm` fija la hora de hoy (la luna sale de noche:
 * `--es scene NIGHT_CLEAR --es clock 23:04`); `open wind` o `open humidity` abre la hoja de 24 h de viento o humedad, y
 * `open air` o `open pollen` la de los próximos días del aire o del polen. `air` elige el caso de la maqueta del 005
 * (por defecto `fair`, 35 por el ozono; `poor` y `extreme` con calima) y `pollen` el polen (por defecto `none`, a 0).
 *
 * Medida de la búsqueda (006, H4): `--es bench search` espera 5 s (para un `dumpsys meminfo` de antes), carga la lista
 * de lugares y busca letra a letra «villanueva» y «cordoba». Escribe los tiempos en `logcat`:
 * `adb logcat -s PlaceBench`. Con `-S`, la app arranca de cero y la carga es la de la primera búsqueda.
 *
 * Varios sitios (006, H15): `--es saved none|some|full` pone 0, 5 o 10 guardados (los de la maqueta; por defecto 5),
 * `--es screen places` empieza en la pantalla Sitios, `--es query villanueva` en el buscador con eso escrito (busca en
 * la lista de verdad del APK) y `--es open-saved <n>` abre la principal del guardado n (0 = el primero). Se puede tocar:
 * abrir postales, buscar y borrar (guardar no hace nada). Por ejemplo:
 * `adb shell am start -S -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoActivity --es saved full --es screen places` o
 * `… --es open-saved 3` (Las Palmas, con «hora de allí»).
 *
 * Escena quieta (012 P6): la escena no pide fotogramas; con la app sin tocar, `adb shell dumpsys gfxinfo com.trastealabs.tiempo.dev`
 * da 0 fotogramas nuevos (`sh tools/medir.sh quieta`). Para probar «Quitar animaciones», el ajuste del sistema.
 *
 * Luna y entrada (007, P3): `--ef moon 0.84` pone la fase de la luna de hoy, `--ez south true` un sitio inventado del
 * hemisferio sur (la luna, girada) y `--ez intro true` la entrada como al abrir en frío (solo sube el astro). Por ejemplo:
 * `adb shell am start -S -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoActivity --es scene NIGHT_CLEAR --es clock 23:04 --ef moon 0.25`.
 * El tiempo de la primera luna, en `adb logcat -s MoonBench`. Con `--ei last <°C>` (012 P9) la cifra de la cabecera cuenta
 * desde esa temperatura hasta la de la escena en 700 ms; sin `last`, entra con un fundido; con `last` igual, no se mueve.
 *
 * Fotos del cielo (009, P1): `--es photo CLEAR_COOL` (o `sky_despejado_frio`) fuerza esa foto, con su luna de noche, sobre
 * los datos de la escena; `--ez bare true` quita los textos y deja solo la foto y un cristal en la zona de las tarjetas,
 * para medir el contraste con `python tools/contraste.py <captura> --zona tarjetas`. Por ejemplo:
 * `adb shell am start -S -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoActivity --es photo NIGHT_CLOUDS --ez bare true`.
 *
 * Sitios en Cristal (010, P2): `--es screen places --es saved full|some|none` (`full` lleva nieve a −5°, niebla, tormenta
 * con datos viejos, Nueva York, Tokio de noche y Lisboa sin datos), `--ez long true` pone «Sanlúcar de Barrameda» como
 * «Donde estoy», `--ez noperm true` deja «Donde estoy» sin permiso de ubicación y `--ez bare true` deja las postales
 * sin textos, solo con la foto y el velo, para `python tools/contraste.py <captura> --rect x,y,w,h`.
 *
 * Cabecera y Resumen (009, P3): `--ei warnings 3` pone de 0 a 3 avisos a la vez (el más grave arriba), `--ez long true`
 * el sitio y los avisos de nombre largo, y `--ez bare-header true` deja solo la foto y el velo de la cabecera, para
 * `python tools/contraste.py <captura> --zona cabecera`. Por ejemplo:
 * `adb shell am start -S -n com.trastealabs.tiempo.dev/com.trastealabs.tiempo.demo.DemoActivity --es scene RAIN --ei warnings 3 --ez long true`.
 *
 * Actualizador (021): `--es update current|never|offline|available|beta|downloading|verifying|ready|permission|installing|error|invalid|install-failed|dev`
 * pinta ese estado con datos inventados (ver `DemoUpdate.kt`); con versión nueva sale además la línea de la principal.
 * `--es screen update` abre Ajustes en «Versión de la app». Por ejemplo: `… --es update downloading --es screen update`.
 *
 * Parte de la IA (016 P4): `--es ai dry|rain|wet` (parte inventado; `rain` y `wet` con la escena de lluvia, `wet` con su aviso).
 * Sin clave de IA (021): `--es aikey none` pinta Ajustes › IA como la ve quien no ha puesto clave.
 *
 * Pregunta (018 P4): `--es ask closed|writing|asking|answer|stale|offtopic|fail|cap` pinta la píldora al pie del Resumen y,
 * salvo `closed` y `cap` (píldora apagada por el tope), abre la hoja en ese estado. En `writing` se puede escribir y enviar:
 * contesta con una respuesta inventada a los 1,5 s. Con `--es open-saved 0` la píldora sale en un guardado.
 */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getStringExtra("bench") == "search") benchSearch()
        // 016 P4: `--es ai dry|rain|wet` pone un parte inventado; sin escena propia, `rain` y `wet` van con la de lluvia, y `wet` con su aviso.
        val ai = intent.getStringExtra("ai")
        // 021: `--es aikey none` pinta Ajustes › IA como la ve alguien sin clave.
        val aiPanel = if (intent.getStringExtra("aikey") == "none") AiPanel() else null
        val scene = demoScene(intent.getStringExtra("scene") ?: if (ai == "rain" || ai == "wet") "RAIN" else null)
        val badge = (demoBadge(intent.getStringExtra("badge")) ?: DemoBadge.WARNING.takeIf { ai == "wet" }).takeIf { it != DemoBadge.EMPTY }
        val now = demoNow(intent.getStringExtra("clock"), System.currentTimeMillis() / 1000)
        // 018 P4: `--es ask closed|writing|asking|answer|stale|offtopic|fail|cap` pinta la píldora y cada estado de la hoja.
        val askDemo = intent.getStringExtra("ask")
        val moonPhase = if (intent.hasExtra("moon")) intent.getFloatExtra("moon", 0.5f).toDouble() else null
        val long = intent.getBooleanExtra("long", false)
        val place = when {
            intent.getBooleanExtra("south", false) -> demoSouthPlace
            long -> demoLongPlace
            else -> demoPlace
        }
        val stored = demoState(scene, badge, now, place = place, moonPhase = moonPhase)
        val warningCount = intent.getIntExtra("warnings", -1)
        val snapshot = checkNotNull(stored.snapshot).let {
            it.copy(air = demoAir(intent.getStringExtra("air"), intent.getStringExtra("pollen"), now, ZoneId.of(it.zoneId)))
        }.let { if (warningCount >= 0) it.copy(warnings = demoWarnings(warningCount, long, now)) else it }
        val reliability = if (intent.getStringExtra("rel") == "none") null else demoReliability(now)
        val state = UiState.Content(
            snapshot = snapshot,
            now = now,
            offline = stored.lastAttemptFailed,
            refreshing = false,
            reliability = reliability,
            usage = demoUsage(now),
            refreshes = demoRefreshes(now),
            ai = aiPanel ?: demoAi(now),
            report = demoReport(ai, snapshot, now),
            ask = demoAskEntry(askDemo),
        )
        val dayDate = snapshot.daily.getOrNull(intent.getIntExtra("day", -1))?.date
        val app = application as WeatherApp
        val initialSaved = demoSaved(intent.getStringExtra("saved"))
        val savedStates = initialSaved.associate { it.id to demoSavedState(it, now) }
        val openSaved = intent.getStringExtra("open-saved")?.toIntOrNull()?.let(initialSaved::getOrNull)
        val initialQuery = intent.getStringExtra("query")
        val motionSettings = AndroidMotionSettings(this)
        // 012 P9: `--ei last <°C>` es la última temperatura vista (la cifra cuenta desde ahí con `--ez intro true`); sin él, no hay anterior.
        val lastSeenTemp = if (intent.hasExtra("last")) intent.getIntExtra("last", 0) else null
        val playIntro = intent.getBooleanExtra("intro", false) && motionSettings.allowed()
        val photo = demoPhoto(intent.getStringExtra("photo"))
        val bare = intent.getBooleanExtra("bare", false)
        val bareHeader = intent.getBooleanExtra("bare-header", false)
        // Sitios (010, P2): «Donde estoy» sin permiso de ubicación.
        val noPerm = intent.getBooleanExtra("noperm", false)
        // 012 P4: el diálogo de la batería encima de la principal (`--ez battery true`), para capturarlo.
        val battery = intent.getBooleanExtra("battery", false)
        setContent {
            var places by remember { mutableStateOf(initialSaved) }
            var selected by remember { mutableStateOf<PlaceRef>(openSaved?.let { PlaceRef.Saved(it.id) } ?: PlaceRef.Here) }
            var query by remember { mutableStateOf("") }
            var askUi by remember { mutableStateOf(demoAskState(askDemo, now, snapshot.fetchedAt)) }
            // 021: el actualizador en cada estado (`--es update …`); la cruz de la principal descarta de verdad.
            var updateUi by remember { mutableStateOf(demoUpdate(intent.getStringExtra("update"), now)) }
            val skyClock = rememberSkyClock(motionSettings)
            val intro = rememberIntro(play = playIntro)
            val phone = ZoneId.systemDefault()
            val shown = (selected as? PlaceRef.Saved)?.let { ref -> places.firstOrNull { it.id == ref.id } }?.let { p ->
                UiState.Content(
                    snapshot = checkNotNull(savedStates.getValue(p.id).snapshot),
                    now = now, offline = false, refreshing = false, usage = demoUsage(now),
                    refreshes = demoRefreshes(now), ai = aiPanel ?: demoAi(now),
                    place = PlaceRef.Saved(p.id), savedName = p.name, ask = demoAskEntry(askDemo),
                )
            } ?: state
            val cards = listOf(
                placeCard(PlaceRef.Here, snapshot.place.name, StoredState(snapshot, false), now, phone, selected == PlaceRef.Here, noPerm),
            ) + places.map { p ->
                val ref = PlaceRef.Saved(p.id)
                placeCard(ref, p.name, savedStates.getValue(p.id), now, phone, selected == ref, false)
            }
            val search by produceState(SearchUi(), query, places) {
                val full = places.size >= MAX_SAVED
                if (normalizeQuery(query).length < MIN_QUERY) {
                    value = SearchUi(query, SearchState.Hint, full)
                    return@produceState
                }
                if (!app.placeSearch.isLoaded) value = SearchUi(query, SearchState.Loading, full)
                val r = app.placeSearch.search(query)
                // «✓ Guardado» en los que se llaman como un guardado de la demo (sus posiciones son de ejemplo).
                val keys = (r.spain + r.world).filter { h -> places.any { it.name == h.name && it.countryCode == h.countryCode } }
                    .mapTo(HashSet()) { it.key }
                value = SearchUi(query, if (r.isEmpty) SearchState.NoResults else SearchState.Results(r), full, keys)
            }
            MainScreen(
                state = shown, onRefresh = {}, onRetry = {}, onRequestPermission = {}, initialDayDate = dayDate,
                placeCards = cards,
                search = search,
                onPlacesOpen = { lifecycleScope.launch { app.placeIndex.load() } },
                onSelectPlace = { selected = it },
                onQueryChange = { query = it },
                // La hoja de la demo se puede tocar: enviar contesta con una respuesta inventada tras 1,5 s.
                askState = askUi,
                onAsk = { q ->
                    askUi = AskState.Asking(q)
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(1_500)
                        if (askUi is AskState.Asking) askUi = AskState.Answered(q, demoAnswer(AskKind.ANSWER, now, snapshot.fetchedAt))
                    }
                },
                onCancelAsk = { askUi = AskState.Writing },
                onCloseAsk = { askUi = AskState.Writing },
                initialAskOpen = demoAskOpens(askDemo),
                onDeletePlace = { id ->
                    places = places.filterNot { it.id == id }
                    if (selected == PlaceRef.Saved(id)) selected = PlaceRef.Here
                },
                initialPlaces = intent.getStringExtra("screen") == "places",
                initialQuery = initialQuery,
                initialSettings = intent.getStringExtra("screen") == "settings",
                initialUpdateFocus = intent.getStringExtra("screen") == "update",
                update = updateUi,
                onUpdate = { action ->
                    val u = updateUi
                    if (action == UpdateAction.Dismiss && u?.available != null) updateUi = u.copy(dismissedCode = u.available!!.versionCode)
                },
                skyClock = skyClock,
                intro = intro,
                lastSeenTemp = lastSeenTemp,
                demoPhoto = photo,
                bare = bare,
                bareHeader = bareHeader,
                initialHourlySheet = when (intent.getStringExtra("open")) {
                    "wind" -> HourlySheetKind.WIND
                    "humidity" -> HourlySheetKind.HUMIDITY
                    else -> null
                },
                initialDaysSheet = when (intent.getStringExtra("open")) {
                    "air" -> DaysSheetKind.AIR
                    "pollen" -> DaysSheetKind.POLLEN
                    else -> null
                },
            )
            if (battery) WeatherTheme { BatteryDialog(onOpenSettings = {}, onDismiss = {}) }
        }
    }

    private fun benchSearch() {
        val app = application as WeatherApp
        thread(name = "PlaceBench") {
            runBlocking {
                Log.i(TAG, "esperando 5 s (dumpsys meminfo de antes)")
                Thread.sleep(5_000)
                val t0 = SystemClock.elapsedRealtimeNanos()
                app.placeIndex.load()
                Log.i(TAG, "carga del índice: ${ms(t0)} ms, ${app.placeIndex.size} lugares")
                for (word in listOf("villanueva", "cordoba")) {
                    for (n in 1..word.length) {
                        val q = word.take(n)
                        val t = SystemClock.elapsedRealtimeNanos()
                        val r = app.placeSearch.search(q)
                        Log.i(TAG, "«$q»: ${ms(t)} ms, ${r.spain.size} + ${r.world.size}")
                    }
                }
                Log.i(TAG, "listo (dumpsys meminfo de después)")
            }
        }
    }

    private fun ms(start: Long) = "%.1f".format((SystemClock.elapsedRealtimeNanos() - start) / 1e6)

    private companion object {
        const val TAG = "PlaceBench"
    }
}
