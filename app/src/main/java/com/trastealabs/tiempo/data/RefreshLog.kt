package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.refresh.AemetOutcome
import com.trastealabs.tiempo.refresh.RefreshOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** Cuántos días se guarda el registro (C8, 015). */
const val REFRESH_LOG_DAYS = 7

/** Cómo acabó un refresco de «donde estoy» (015 P2). */
@Serializable
enum class RefreshOutcome { OK, OFFLINE, NO_LOCATION }

/**
 * 018 P2: la marca de una línea que no es un refresco sino una notificación de la mañana enviada. [askedAt] es la hora
 * pedida (epoch s, la que eligió el usuario para hoy); la hora real en que salió es el `at` de la línea. [withAi] dice si
 * el texto fue el parte de la IA (`true`) o las frases de reglas (`false`).
 */
@Serializable
data class MorningMark(val askedAt: Long, val withAi: Boolean)

/**
 * Una línea del registro: sin ubicación ni nombre de sitio (H13).
 * [at] es epoch en segundos (UTC); [aemetBytes], bytes de cuerpo que bajó AEMET por la red en ese refresco.
 * [morning] (018 P2): `null` en un refresco; con valor, la línea es un aviso enviado y no cuenta como refresco.
 */
@Serializable
data class RefreshLogEntry(
    val at: Long,
    val origin: RefreshOrigin,
    val outcome: RefreshOutcome,
    val aemet: AemetOutcome,
    val aemetBytes: Long = 0,
    val morning: MorningMark? = null,
)

/**
 * El registro de refrescos (015 P2, H9, H10): una línea JSON por refresco de «donde estoy» en `refresh_log.jsonl`.
 * Al escribir se borra lo de más de [REFRESH_LOG_DAYS] días y el fichero se reescribe entero (unas 340 líneas, ~30 KB)
 * con un fichero temporal que se renombra, para que nadie lea nunca líneas cortadas aunque dos refrescos se pisen.
 * Una línea ilegible se salta; un fichero ausente o roto es un registro vacío.
 */
class RefreshLog(
    private val file: File,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val cache = MutableStateFlow<List<RefreshLogEntry>?>(null)

    /** De la más vieja a la más nueva; vacía si no hay fichero o está roto. */
    val entries: Flow<List<RefreshLogEntry>> = flow {
        // Si la lectura falla no se cachea nada: se enseña vacío ahora y se vuelve a intentar en el siguiente append.
        if (mutex.withLock { loaded() } == null) emit(emptyList())
        emitAll(cache.filterNotNull())
    }

    /** Añade [entry], poda lo viejo y escribe. Lanza si no se puede escribir (quien llama decide qué hacer). */
    suspend fun append(entry: RefreshLogEntry) {
        mutex.withLock {
            val cutoff = nowSeconds() - REFRESH_LOG_DAYS * 24 * 3600L
            // Si no se puede leer lo que había, no se reescribe el fichero con una sola línea: se pierde esta, no la semana.
            val current = loaded() ?: throw IOException("No se pudo leer el registro de refrescos")
            val next = (current + entry).filter { it.at >= cutoff }
            write(next)
            cache.value = next
        }
    }

    /** Lo guardado; null si el disco no dejó leerlo (y entonces no se cachea). */
    private suspend fun loaded(): List<RefreshLogEntry>? {
        cache.value?.let { return it }
        val fromDisk = readFromDisk() ?: return null
        cache.value = fromDisk
        return fromDisk
    }

    private suspend fun readFromDisk(): List<RefreshLogEntry>? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                emptyList()
            } else {
                file.readLines().mapNotNull { line ->
                    if (line.isBlank()) null else try {
                        json.decodeFromString(RefreshLogEntry.serializer(), line)
                    } catch (e: SerializationException) {
                        null
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            }
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun write(list: List<RefreshLogEntry>) = withContext(Dispatchers.IO) {
        AtomicFile.write(file, list.joinToString("") { json.encodeToString(RefreshLogEntry.serializer(), it) + "\n" })
    }
}
