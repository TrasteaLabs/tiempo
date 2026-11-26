package com.trastealabs.tiempo.refresh

import android.util.Log
import com.trastealabs.tiempo.data.AirSource
import com.trastealabs.tiempo.data.KnownWarnings
import com.trastealabs.tiempo.data.LocationSource
import com.trastealabs.tiempo.data.PlaceLookup
import com.trastealabs.tiempo.data.RefreshLog
import com.trastealabs.tiempo.data.RefreshLogEntry
import com.trastealabs.tiempo.data.RefreshOutcome
import com.trastealabs.tiempo.data.SnapshotStore
import com.trastealabs.tiempo.data.WarningFetch
import com.trastealabs.tiempo.data.WarningSource
import com.trastealabs.tiempo.data.WeatherSource
import com.trastealabs.tiempo.data.rounded
import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Place
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.toPlace
import com.trastealabs.tiempo.domain.StoredState
import com.trastealabs.tiempo.domain.Warning
import com.trastealabs.tiempo.domain.WeatherSnapshot
import com.trastealabs.tiempo.domain.airDue
import com.trastealabs.tiempo.domain.keptAir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** Quién pide el refresco: la app (ubicación actual) o el worker y la alarma (H7). */
enum class Trigger { APP, BACKGROUND }

/**
 * Quién pidió el refresco de «donde estoy» (015 P2): el trabajo periódico, la alarma de respaldo o la app al abrirla.
 * [MORNING] (018 P2): el que lanza la notificación de la mañana justo antes de avisar.
 */
@Serializable
enum class RefreshOrigin { WORKER, ALARM, APP, MORNING }

/** Qué pasó con AEMET en un refresco (015); en el registro de P2 se escribe tal cual. */
@Serializable
enum class AemetOutcome { UNCHANGED, NEW, FAILED, NOT_APPLICABLE }

/** AEMET en un refresco: qué pasó y cuántos bytes de cuerpo llegaron por la red. */
internal data class AemetReport(val outcome: AemetOutcome, val bytes: Long) {
    /**
     * Lo que se apunta en el registro. Una consulta que no bajó nada (0 bytes) es «sin cambios» aunque `news` fuese
     * `true` (sin fuente conocida y todo salió de la caché, `review-p1`): «nuevos» solo cuando llegó algo.
     */
    fun forLog(): AemetReport =
        if (outcome == AemetOutcome.NEW && bytes == 0L) copy(outcome = AemetOutcome.UNCHANGED) else this

    companion object {
        val NOT_APPLICABLE = AemetReport(AemetOutcome.NOT_APPLICABLE, 0)
    }
}

/** Una previsión recién armada, con lo que pasó con AEMET. */
private class Fetched(val snapshot: WeatherSnapshot, val aemet: AemetReport)

sealed interface RefreshResult {
    data object Ok : RefreshResult
    data object Offline : RefreshResult
    data object NoLocation : RefreshResult
}

/**
 * Único sitio que descarga y escribe datos (C6); app y widget solo leen `state`.
 * [onUpdated] repinta los widgets al terminar cada refresco, haya ido bien o mal.
 * El aire y el polen (005) van dentro del snapshot y se piden solo cuando toca ([airDue]).
 */
class WeatherRepository(
    private val weather: WeatherSource,
    private val warnings: WarningSource,
    private val air: AirSource,
    private val places: PlaceLookup,
    private val location: LocationSource,
    private val store: SnapshotStore,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val onUpdated: suspend () -> Unit = {},
    /** Los sitios guardados (006); null en los tests de antes, y entonces no hay guardados. */
    private val saved: SavedPlacesRepository? = null,
    /** El registro de refrescos de «donde estoy» (015 P2); null en los tests de antes, y entonces no se escribe. */
    private val log: RefreshLog? = null,
) {
    private val mutex = Mutex()

    /** Las descargas de los guardados, de una en una; aparte del de «donde estoy» para que el fondo no las retrase. */
    private val savedMutex = Mutex()

    val state: Flow<StoredState> get() = store.state

    /** Lo guardado de un sitio guardado; vacío si no hay guardados. */
    fun savedState(id: String): Flow<StoredState> =
        saved?.stores?.snapshot(id)?.state ?: flowOf(StoredState(null, lastAttemptFailed = false))

    /**
     * Descarga un sitio guardado con su nombre y su posición, sin GPS ni lista de lugares (006). Escribe solo en su
     * carpeta y no avisa al widget (H13). Si el sitio se borra mientras descarga, no se escribe nada.
     */
    suspend fun refreshSaved(place: SavedPlace): RefreshResult {
        val saved = saved ?: return RefreshResult.Offline
        return savedMutex.withLock {
            val target = saved.stores.snapshot(place.id)
            val previous = target.read()
            val next = try {
                StoredState(fetchSnapshot(place.toPlace(), previous.snapshot).snapshot, lastAttemptFailed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo actualizar un sitio guardado", e)
                previous.copy(lastAttemptFailed = true)
            }
            saved.lock.withLock { if (saved.containsLocked(place.id)) target.write(next) }
            if (next.lastAttemptFailed) RefreshResult.Offline else RefreshResult.Ok
        }
    }

    /**
     * [origin] solo sirve para el registro (015 P2): por defecto, la app es [RefreshOrigin.APP] y el segundo plano
     * [RefreshOrigin.WORKER]; la alarma de respaldo pasa [RefreshOrigin.ALARM].
     */
    suspend fun refresh(
        trigger: Trigger,
        origin: RefreshOrigin = if (trigger == Trigger.APP) RefreshOrigin.APP else RefreshOrigin.WORKER,
    ): RefreshResult {
        val (result, aemet) = mutex.withLock { refreshLocked(trigger) }
        val at = nowSeconds()
        try {
            onUpdated()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron repintar los widgets", e)
        }
        // Después de repintar y sin que un fallo cambie el resultado (H14).
        writeLog(at, origin, result, aemet)
        return result
    }

    private suspend fun writeLog(at: Long, origin: RefreshOrigin, result: RefreshResult, aemet: AemetReport) {
        val log = log ?: return
        val outcome = when (result) {
            RefreshResult.Ok -> RefreshOutcome.OK
            RefreshResult.Offline -> RefreshOutcome.OFFLINE
            RefreshResult.NoLocation -> RefreshOutcome.NO_LOCATION
        }
        val shown = if (result == RefreshResult.Ok) aemet.forLog() else AemetReport.NOT_APPLICABLE
        try {
            log.append(RefreshLogEntry(at, origin, outcome, shown.outcome, shown.bytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir el registro de refrescos", e)
        }
    }

    private suspend fun refreshLocked(trigger: Trigger): Pair<RefreshResult, AemetReport> {
        val previous = store.read()
        val position = when (trigger) {
            Trigger.APP -> location.current() ?: location.lastKnown()
            // Sin permiso "todo el tiempo" Android no da la ubicación en segundo plano: el último sitio (H7).
            Trigger.BACKGROUND ->
                (if (location.hasBackgroundPermission()) location.current() ?: location.lastKnown() else null)
                    ?: previous.snapshot?.place?.position
        }?.rounded() ?: return RefreshResult.NoLocation to AemetReport.NOT_APPLICABLE

        return try {
            val nearest = withContext(Dispatchers.Default) { places.nearest(position) }
            val place = Place(nearest.name, nearest.countryCode, position)
            val fetched = fetchSnapshot(place, previous.snapshot)
            store.write(StoredState(fetched.snapshot, lastAttemptFailed = false))
            RefreshResult.Ok to fetched.aemet
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Open-Meteo falla = sin conexión (H13): no se toca la previsión, solo se marca el intento.
            Log.w(TAG, "No se pudo actualizar", e)
            store.write(previous.copy(lastAttemptFailed = true))
            RefreshResult.Offline to AemetReport.NOT_APPLICABLE
        }
    }

    /**
     * La previsión de [place], con AEMET (solo en España) y el aire (si toca) a la vez que Open-Meteo para no alargar el
     * "Actualizando…". Lanza si Open-Meteo falla. [previous] es lo guardado de ese mismo sitio (avisos y aire de antes);
     * de ahí sale lo que se le dice a AEMET para no bajar ni analizar lo que ya se tiene (015).
     */
    private suspend fun fetchSnapshot(place: Place, previous: WeatherSnapshot?): Fetched {
        val position = place.position
        val previousAir = previous?.air
        val airNeeded = airDue(previousAir, position, nowSeconds())
        val known = previous?.knownWarnings()
        val (fetched, newWarnings, newAir) = coroutineScope {
            val aemet = if (place.countryCode == "ES") async { fetchWarnings(position, known) } else null
            val airCall = if (airNeeded) async { fetchAir(position) } else null
            Triple(weather.fetch(position), aemet?.await(), airCall?.await())
        }
        val outcome = when {
            place.countryCode != "ES" -> AemetReport(AemetOutcome.NOT_APPLICABLE, 0)
            newWarnings == null -> AemetReport(AemetOutcome.FAILED, 0)
            newWarnings.news -> AemetReport(AemetOutcome.NEW, newWarnings.bytes)
            else -> AemetReport(AemetOutcome.UNCHANGED, newWarnings.bytes)
        }
        // Si el aire no llegó, se queda el guardado del mismo sitio (H3 del 005).
        val snapshot = fetched.copy(place = place).withWarnings(newWarnings, previous)
            .copy(air = keptAir(newAir, previousAir, position))
        return Fetched(snapshot, outcome)
    }

    /** Lo que ya se sabe de los avisos de este sitio; nada si no es de España o es de antes de la 015. */
    private fun WeatherSnapshot.knownWarnings(): KnownWarnings? =
        warningsSource?.takeIf { place.countryCode == "ES" }?.let { KnownWarnings(it, place.position, warnings) }

    /** null = AEMET no respondió; no es un error para el usuario (H16). */
    private suspend fun fetchWarnings(position: LatLon, known: KnownWarnings?): WarningFetch? = try {
        // Con tope: un tar.gz que llegue muy lento no debe retrasar la previsión ni el widget.
        withTimeoutOrNull(AEMET_TIMEOUT_MS) { warnings.fetch(position, known) }
            ?: null.also { Log.w(TAG, "AEMET tarda demasiado") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "AEMET no respondió", e)
        null
    }

    /** null = el aire no llegó a tiempo o falló; la previsión sale igual (H3 del 005). */
    private suspend fun fetchAir(position: LatLon): AirQuality? = try {
        // Con tope y llamada cancelable: la previsión no espera nunca más de lo que ya espera a AEMET.
        withTimeoutOrNull(AIR_TIMEOUT_MS) { air.fetch(position) }
            ?: null.also { Log.w(TAG, "El aire tarda demasiado") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "El aire no respondió", e)
        null
    }

    /**
     * Fuera de España no hay avisos. Si AEMET falló, siguen los guardados del mismo sitio
     * (activeWarning ya descarta los caducados), con su fuente solo si el sitio no se ha movido: si se ha movido,
     * esos avisos no se daran por buenos para la posición nueva en el refresco siguiente.
     */
    private fun WeatherSnapshot.withWarnings(fresh: WarningFetch?, previous: WeatherSnapshot?): WeatherSnapshot = when {
        place.countryCode != "ES" -> copy(warnings = emptyList(), warningsFetchedAt = null, warningsSource = null)
        fresh != null -> copy(warnings = fresh.warnings, warningsFetchedAt = fetchedAt, warningsSource = fresh.source)
        previous != null && previous.place.name == place.name && previous.place.countryCode == place.countryCode ->
            copy(
                warnings = previous.warnings,
                warningsFetchedAt = previous.warningsFetchedAt,
                warningsSource = previous.warningsSource.takeIf { previous.place.position == place.position },
            )
        else -> copy(warnings = emptyList(), warningsFetchedAt = null, warningsSource = null)
    }

    private companion object {
        const val TAG = "WeatherRepository"
        const val AEMET_TIMEOUT_MS = 30_000L
        const val AIR_TIMEOUT_MS = 15_000L
    }
}
