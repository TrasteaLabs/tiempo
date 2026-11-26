package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.SavedPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Lee y escribe `saved_places.json`, la lista de sitios guardados (006). Como SnapshotStore: escritura atómica y un
 * solo escritor (SavedPlacesRepository con su Mutex). Sin fichero o roto: lista vacía, sin romper nada.
 */
class SavedPlacesStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = MutableStateFlow<SavedPlaces?>(null)

    val state: Flow<SavedPlaces> = flow {
        read()
        emitAll(cache.filterNotNull())
    }

    suspend fun read(): SavedPlaces {
        cache.value?.let { return it }
        val fromDisk = readFromDisk()
        cache.compareAndSet(null, fromDisk)
        return cache.value ?: fromDisk
    }

    suspend fun write(s: SavedPlaces) {
        withContext(Dispatchers.IO) {
            AtomicFile.write(file, json.encodeToString(SavedPlaces.serializer(), s))
        }
        cache.value = s
    }

    private suspend fun readFromDisk(): SavedPlaces = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) SavedPlaces() else json.decodeFromString(SavedPlaces.serializer(), file.readText())
        } catch (e: SerializationException) {
            SavedPlaces()
        } catch (e: IllegalArgumentException) {
            SavedPlaces()
        } catch (e: IOException) {
            SavedPlaces()
        }
    }
}
