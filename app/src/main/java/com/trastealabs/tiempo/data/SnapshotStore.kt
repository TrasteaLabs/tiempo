package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.StoredState
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
 * Lee y escribe `snapshot.json`, la única fuente de verdad (C6). La escritura es atómica
 * (fichero temporal + renombrar) para que nadie lea nunca un fichero a medias.
 * El que escribe tiene que ser uno solo; de eso se encarga WeatherRepository con su Mutex.
 */
class SnapshotStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = MutableStateFlow<StoredState?>(null)

    val state: Flow<StoredState> = flow {
        read()
        emitAll(cache.filterNotNull())
    }

    suspend fun read(): StoredState {
        cache.value?.let { return it }
        val fromDisk = readFromDisk()
        cache.compareAndSet(null, fromDisk)
        return cache.value ?: fromDisk
    }

    suspend fun write(s: StoredState) {
        withContext(Dispatchers.IO) {
            AtomicFile.write(file, json.encodeToString(StoredState.serializer(), s))
        }
        cache.value = s
    }

    private suspend fun readFromDisk(): StoredState = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) EMPTY else json.decodeFromString(StoredState.serializer(), file.readText())
        } catch (e: SerializationException) {
            EMPTY
        } catch (e: IllegalArgumentException) {
            EMPTY
        } catch (e: IOException) {
            EMPTY
        }
    }

    private companion object {
        val EMPTY = StoredState(snapshot = null, lastAttemptFailed = false)
    }
}
