package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.StoredReliability
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
 * Lee y escribe `reliability.json`, aparte de `snapshot.json` para que el widget no se entere (H7).
 * Como SnapshotStore: escritura atómica y un solo escritor (ReliabilityRepository con su Mutex).
 */
class ReliabilityStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = MutableStateFlow<StoredReliability?>(null)

    val state: Flow<StoredReliability> = flow {
        read()
        emitAll(cache.filterNotNull())
    }

    suspend fun read(): StoredReliability {
        cache.value?.let { return it }
        val fromDisk = readFromDisk()
        cache.compareAndSet(null, fromDisk)
        return cache.value ?: fromDisk
    }

    suspend fun write(s: StoredReliability) {
        withContext(Dispatchers.IO) {
            AtomicFile.write(file, json.encodeToString(StoredReliability.serializer(), s))
        }
        cache.value = s
    }

    /** Sin fichero (datos de antes de esta versión) o roto: sin fiabilidad, sin romper nada. */
    private suspend fun readFromDisk(): StoredReliability = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) EMPTY else json.decodeFromString(StoredReliability.serializer(), file.readText())
        } catch (e: SerializationException) {
            EMPTY
        } catch (e: IllegalArgumentException) {
            EMPTY
        } catch (e: IOException) {
            EMPTY
        }
    }

    private companion object {
        val EMPTY = StoredReliability()
    }
}
