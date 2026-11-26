package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.normalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * La lista de lugares del APK en memoria, cargada una sola vez (006). La usan [AssetPlaceLookup] (el nombre de «donde
 * estoy») y [PlaceSearch]. Todo en el móvil: no recibe cliente HTTP (H5, C9).
 *
 * Formato (ver tools/build_places.py): líneas `R<TAB>cc<TAB>region`, cuyo índice es su orden, y líneas de lugar
 * `name<TAB>regionIdx<TAB>population<TAB>lat<TAB>lon[<TAB>nameEs]`.
 *
 * Ojo: el asset se versiona como `places.tsv.gz`, pero Android Gradle Plugin descomprime los `.gz` al empaquetar y les
 * quita la extensión: dentro del APK es `places.tsv`, ya en texto plano.
 */
class PlaceIndex(private val open: () -> InputStream) {
    private class Data(
        val names: Array<String>,
        val namesEs: Array<String?>,
        val keys: Array<String>,
        val keysEs: Array<String?>,
        val regionIdx: IntArray,
        val population: IntArray,
        val lat: DoubleArray,
        val lon: DoubleArray,
        val regionCc: Array<String>,
        val regionName: Array<String>,
    )

    @Volatile private var data: Data? = null
    private val lock = Any()

    val isLoaded: Boolean get() = data != null

    /** Idempotente. En Dispatchers.Default; la llama la pantalla Sitios al abrirse. */
    suspend fun load() {
        if (data == null) withContext(Dispatchers.Default) { loaded() }
    }

    /** Carga si hace falta, en el hilo que llama (nearest() no es suspend). */
    private fun loaded(): Data = data ?: synchronized(lock) { data ?: read().also { data = it } }

    val size: Int get() = loaded().names.size
    val names: Array<String> get() = loaded().names
    val namesEs: Array<String?> get() = loaded().namesEs
    /** El nombre normalizado ([normalize]); la misma instancia que el nombre si ya lo estaba. */
    val keys: Array<String> get() = loaded().keys
    val keysEs: Array<String?> get() = loaded().keysEs
    val population: IntArray get() = loaded().population
    val lat: DoubleArray get() = loaded().lat
    val lon: DoubleArray get() = loaded().lon

    fun countryOf(i: Int): String = loaded().let { it.regionCc[it.regionIdx[i]] }
    fun regionOf(i: Int): String = loaded().let { it.regionName[it.regionIdx[i]] }

    private fun read(): Data {
        val cap = 260_000
        val names = ArrayList<String>(cap)
        val namesEs = ArrayList<String?>(cap)
        val regionIdx = IntArrayBuilder(cap)
        val population = IntArrayBuilder(cap)
        val lat = DoubleArrayBuilder(cap)
        val lon = DoubleArrayBuilder(cap)
        val regionCc = ArrayList<String>(4_000)
        val regionName = ArrayList<String>(4_000)
        val cols = IntArray(7)
        open().bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                // Posiciones de los tabuladores, sin split (son 250.000 líneas).
                var n = 0
                var from = 0
                while (n < cols.size) {
                    val t = line.indexOf('\t', from)
                    if (t < 0) break
                    cols[n++] = t
                    from = t + 1
                }
                if (line.startsWith("R\t")) {
                    if (n < 2) continue
                    regionCc += line.substring(cols[0] + 1, cols[1])
                    regionName += line.substring(cols[1] + 1)
                    continue
                }
                if (n < 4) continue
                val region = line.substring(cols[0] + 1, cols[1]).toIntOrNull() ?: continue
                val la = line.substring(cols[2] + 1, cols[3]).toDoubleOrNull() ?: continue
                val loEnd = if (n >= 5) cols[4] else line.length
                val lo = line.substring(cols[3] + 1, loEnd).toDoubleOrNull() ?: continue
                names += line.substring(0, cols[0])
                namesEs += if (n >= 5) line.substring(cols[4] + 1, if (n >= 6) cols[5] else line.length).ifEmpty { null } else null
                regionIdx += region
                population += line.substring(cols[1] + 1, cols[2]).toIntOrNull() ?: 0
                lat += la
                lon += lo
            }
        }
        // Un índice de región fuera de la tabla sería un fichero roto: mejor fallar aquí que enseñar otra región.
        val idx = regionIdx.build()
        check(idx.all { it in regionCc.indices }) { "places.tsv: región fuera de la tabla" }
        val nameArr = names.toTypedArray()
        val esArr = namesEs.toTypedArray()
        return Data(
            names = nameArr,
            namesEs = esArr,
            keys = Array(nameArr.size) { normalize(nameArr[it]) },
            keysEs = Array(esArr.size) { esArr[it]?.let(::normalize) },
            regionIdx = idx,
            population = population.build(),
            lat = lat.build(),
            lon = lon.build(),
            regionCc = regionCc.toTypedArray(),
            regionName = regionName.toTypedArray(),
        )
    }

    private class IntArrayBuilder(cap: Int) {
        private var a = IntArray(cap)
        private var n = 0
        operator fun plusAssign(v: Int) {
            if (n == a.size) a = a.copyOf(a.size * 2)
            a[n++] = v
        }
        fun build() = a.copyOf(n)
    }

    private class DoubleArrayBuilder(cap: Int) {
        private var a = DoubleArray(cap)
        private var n = 0
        operator fun plusAssign(v: Double) {
            if (n == a.size) a = a.copyOf(a.size * 2)
            a[n++] = v
        }
        fun build() = a.copyOf(n)
    }
}
