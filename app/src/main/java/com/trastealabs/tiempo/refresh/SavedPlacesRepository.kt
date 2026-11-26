package com.trastealabs.tiempo.refresh

import com.trastealabs.tiempo.data.PlaceStores
import com.trastealabs.tiempo.data.SavedPlacesStore
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.MAX_SAVED
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.SavedPlaces
import com.trastealabs.tiempo.domain.sameSpot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * La lista de sitios guardados (006): guardar con tope y sin repetidos (H6) y borrar con sus datos (H7).
 * [lock] es el mismo que usan WeatherRepository y ReliabilityRepository para escribir los ficheros de un guardado:
 * así un refresco que acaba después de un borrado no vuelve a crear su carpeta.
 */
class SavedPlacesRepository(
    private val store: SavedPlacesStore,
    val stores: PlaceStores,
    val lock: Mutex = Mutex(),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val places: StateFlow<List<SavedPlace>> =
        store.state.map { it.places }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    sealed interface SaveResult {
        data class Saved(val place: SavedPlace) : SaveResult
        data class AlreadySaved(val place: SavedPlace) : SaveResult
        data object Full : SaveResult
    }

    suspend fun list(): List<SavedPlace> = store.read().places

    suspend fun find(id: String): SavedPlace? = list().firstOrNull { it.id == id }

    /** Solo se llama con [lock] cogido: si el sitio sigue en la lista, se pueden escribir sus ficheros. */
    internal suspend fun containsLocked(id: String): Boolean = list().any { it.id == id }

    suspend fun save(name: String, region: String, countryCode: String, position: LatLon): SaveResult = lock.withLock {
        val current = store.read().places
        current.firstOrNull { it.sameSpot(name, countryCode, position) }?.let { return SaveResult.AlreadySaved(it) }
        if (current.size >= MAX_SAVED) return SaveResult.Full
        val savedAt = nowSeconds()
        var n = savedAt * 1000
        while (current.any { it.id == "p$n" }) n++
        val place = SavedPlace("p$n", name, region, countryCode, position, savedAt)
        store.write(SavedPlaces(current + place))
        SaveResult.Saved(place)
    }

    /** La quita de la lista y luego borra su carpeta (H7). */
    suspend fun delete(id: String) = lock.withLock {
        val current = store.read().places
        if (current.any { it.id == id }) store.write(SavedPlaces(current.filterNot { it.id == id }))
        stores.delete(id)
    }
}
