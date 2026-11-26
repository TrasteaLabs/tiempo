package com.trastealabs.tiempo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Los ficheros de cada sitio guardado, en `places/<id>/` con el mismo formato que los de «donde estoy» (006).
 * Una sola instancia de cada almacén por id: SnapshotStore y ReliabilityStore guardan en memoria lo último.
 */
class PlaceStores(private val dir: File) {
    private val snapshots = HashMap<String, SnapshotStore>()
    private val reliabilities = HashMap<String, ReliabilityStore>()
    private val reports = HashMap<String, ReportStore>()

    @Synchronized
    fun snapshot(id: String): SnapshotStore =
        snapshots.getOrPut(id) { SnapshotStore(File(folder(id), "snapshot.json")) }

    @Synchronized
    fun reliability(id: String): ReliabilityStore =
        reliabilities.getOrPut(id) { ReliabilityStore(File(folder(id), "reliability.json")) }

    /** El parte de la IA del sitio (016), en `places/<id>/ai_report.json`. */
    @Synchronized
    fun report(id: String): ReportStore =
        reports.getOrPut(id) { ReportStore(File(folder(id), "ai_report.json")) }

    /** Borra la carpeta entera del sitio y olvida sus almacenes (H7). */
    suspend fun delete(id: String) {
        synchronized(this) {
            snapshots.remove(id)
            reliabilities.remove(id)
            reports.remove(id)
        }
        withContext(Dispatchers.IO) { folder(id).deleteRecursively() }
    }

    private fun folder(id: String): File {
        require(id.isNotEmpty() && id.all { it.isLetterOrDigit() }) { "id de sitio no válido: $id" }
        return File(dir, id)
    }
}
