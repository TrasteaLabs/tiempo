package com.trastealabs.tiempo.refresh

import android.util.Log
import com.trastealabs.tiempo.data.ReliabilitySource
import com.trastealabs.tiempo.data.ReliabilityStore
import com.trastealabs.tiempo.data.SnapshotStore
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.StoredReliability
import com.trastealabs.tiempo.domain.reliabilityDue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/**
 * Pide la fiabilidad después de la previsión y solo cuando toca (H8). La previsión nunca la espera (H9):
 * quien llama ya ha guardado y pintado la suya, y [state] empieza vacío sin esperar a leer el fichero.
 * No avisa al widget ni toca `snapshot.json` (H7).
 */
class ReliabilityRepository(
    private val source: ReliabilitySource,
    private val store: ReliabilityStore,
    private val snapshots: SnapshotStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Los sitios guardados (006); null en los tests de antes. */
    private val saved: SavedPlacesRepository? = null,
) {
    private val mutex = Mutex()
    private val savedMutex = Mutex()

    val state: StateFlow<StoredReliability> = store.state.stateIn(scope, SharingStarted.Eagerly, StoredReliability())

    /**
     * Para el sitio de la previsión guardada; sin previsión no pide. Con tope de 30 s: si tarda, falla o Open-Meteo
     * dice que se ha pasado el límite, se apunta la hora del fallo y se conserva la anterior. Nunca lanza.
     * Devuelve true si guardó una nueva.
     */
    suspend fun refreshIfDue(now: Long): Boolean = try {
        // Mirar fuera del Mutex evita esperar a otra petición en curso cuando no toca; dentro se vuelve a mirar.
        duePosition(now) != null && mutex.withLock { refreshLocked(now) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo guardar la fiabilidad", e)
        false
    }

    /** La fiabilidad de un sitio guardado (006). */
    fun savedState(id: String): Flow<StoredReliability> =
        saved?.stores?.reliability(id)?.state ?: flowOf(StoredReliability())

    /**
     * Como [refreshIfDue], para un guardado: con su posición, su zona y su `reliability.json`, con el ritmo de siempre
     * (H8 del 003) contado sobre lo suyo (H11). Se llama solo al abrirlo. Si se borra mientras pide, no escribe nada.
     */
    suspend fun refreshSavedIfDue(place: SavedPlace, now: Long): Boolean = try {
        val saved = saved
        if (saved == null) false else savedMutex.withLock {
            val snapshot = saved.stores.snapshot(place.id).read().snapshot
            val target = saved.stores.reliability(place.id)
            val previous = target.read()
            if (snapshot == null ||
                !reliabilityDue(previous, snapshot.place.position, now, ZoneId.of(snapshot.zoneId))
            ) {
                false
            } else {
                val fresh = fetchOrNull(snapshot.place.position)
                val next = if (fresh == null) previous.copy(lastFailedAt = now) else StoredReliability(fresh, lastFailedAt = null)
                saved.lock.withLock { if (saved.containsLocked(place.id)) target.write(next) }
                fresh != null
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo guardar la fiabilidad de un guardado", e)
        false
    }

    private suspend fun fetchOrNull(position: LatLon): Reliability? = try {
        withTimeoutOrNull(TIMEOUT_MS) { source.fetch(position) }
            ?: null.also { Log.w(TAG, "La fiabilidad tarda demasiado") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "La fiabilidad no llegó", e)
        null
    }

    private suspend fun refreshLocked(now: Long): Boolean {
        val position = duePosition(now) ?: return false
        val previous = store.read()
        val fresh = fetchOrNull(position)
        if (fresh == null) {
            store.write(previous.copy(lastFailedAt = now))
            return false
        }
        store.write(StoredReliability(fresh, lastFailedAt = null))
        return true
    }

    /** La posición de la previsión si toca pedir; si no, null. */
    private suspend fun duePosition(now: Long): LatLon? {
        val snapshot = snapshots.read().snapshot ?: return null
        val position = snapshot.place.position
        return position.takeIf { reliabilityDue(store.read(), it, now, ZoneId.of(snapshot.zoneId)) }
    }

    private companion object {
        const val TAG = "ReliabilityRepository"
        const val TIMEOUT_MS = 30_000L
    }
}
