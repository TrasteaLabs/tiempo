package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.AiReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Lee y escribe `ai_report.json` (016), aparte de `snapshot.json` para que la previsión y el widget no se enteren.
 * Como SnapshotStore: escritura atómica y un solo escritor (ReportRepository con su Mutex). Un fichero ausente o roto
 * es «sin parte», nunca un fallo.
 */
class ReportStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    /** `null` = aún sin leer; [Loaded] con `null` dentro = leído y sin parte. */
    private class Loaded(val report: AiReport?)

    private val cache = MutableStateFlow<Loaded?>(null)

    val state: Flow<AiReport?> = flow {
        read()
        emitAll(cache.filterNotNull().map { it.report })
    }

    suspend fun read(): AiReport? {
        cache.value?.let { return it.report }
        val fromDisk = Loaded(readFromDisk())
        cache.compareAndSet(null, fromDisk)
        return (cache.value ?: fromDisk).report
    }

    suspend fun write(report: AiReport) {
        withContext(Dispatchers.IO) {
            AtomicFile.write(file, json.encodeToString(AiReport.serializer(), report))
        }
        cache.value = Loaded(report)
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) { file.delete() }
        cache.value = Loaded(null)
    }

    private suspend fun readFromDisk(): AiReport? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) null else json.decodeFromString(AiReport.serializer(), file.readText())
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }
    }
}
