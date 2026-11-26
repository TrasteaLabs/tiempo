package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.SavedPlace
import com.trastealabs.tiempo.domain.boldRanges
import com.trastealabs.tiempo.domain.matches
import com.trastealabs.tiempo.domain.normalizeQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

const val MIN_QUERY = 2
const val MAX_PER_GROUP = 30

/** Un resultado de la búsqueda (006). [name] es el que se enseña y se guarda: el español si lo hay. */
data class PlaceHit(
    val name: String,
    val region: String,
    val countryCode: String,
    /** El país en español, de Android: «España», «Reino Unido». */
    val country: String,
    val position: LatLon,
    val population: Int,
    /** Tramos de [name] que coinciden con lo escrito (para la negrita). */
    val bold: List<IntRange>,
)

data class SearchResults(val spain: List<PlaceHit>, val world: List<PlaceHit>) {
    val isEmpty: Boolean get() = spain.isEmpty() && world.isEmpty()
}

/** La clave de «ya guardado», la misma regla que `SavedPlace.sameSpot`: nombre, país y posición a 2 decimales. */
fun spotKey(name: String, countryCode: String, position: LatLon): String =
    "$name|$countryCode|${Math.round(position.lat * 100)}|${Math.round(position.lon * 100)}"

val PlaceHit.key: String get() = spotKey(name, countryCode, position)
val SavedPlace.key: String get() = spotKey(name, countryCode, position)

/**
 * La búsqueda de lugares, toda en el móvil (H2, H3, H5): por inicio de nombre o de palabra, sin tildes ni mayúsculas,
 * en el nombre principal y en el español. España primero; en cada grupo, por habitantes (los de 0 al final) y a
 * igualdad por nombre. Como mucho [MAX_PER_GROUP] por grupo.
 */
class PlaceSearch(private val index: PlaceIndex) {
    val isLoaded: Boolean get() = index.isLoaded

    private val countries = ConcurrentHashMap<String, String>()

    suspend fun search(query: String): SearchResults {
        val q = normalizeQuery(query)
        if (q.length < MIN_QUERY) return EMPTY
        return withContext(Dispatchers.Default) {
            index.load()
            val keys = index.keys
            val keysEs = index.keysEs
            val spain = ArrayList<Int>()
            val world = ArrayList<Int>()
            for (i in keys.indices) {
                if (i and 0x3FFF == 0) coroutineContext.ensureActive()
                if (matches(keys[i], q) || keysEs[i]?.let { matches(it, q) } == true) {
                    if (index.countryOf(i) == "ES") spain += i else world += i
                }
            }
            SearchResults(top(spain, query), top(world, query))
        }
    }

    private fun shownName(i: Int): String = index.namesEs[i] ?: index.names[i]
    private fun shownKey(i: Int): String = index.keysEs[i] ?: index.keys[i]

    private val order: Comparator<Int> = compareBy<Int> { index.population[it] == 0 }
        .thenByDescending { index.population[it] }
        .thenBy { shownKey(it) }
        .thenBy { shownName(it) }
        .thenBy { index.regionOf(it) }
        .thenBy { it }

    private fun top(found: List<Int>, query: String): List<PlaceHit> =
        found.sortedWith(order).take(MAX_PER_GROUP).map { i ->
            val name = shownName(i)
            val cc = index.countryOf(i)
            PlaceHit(
                name = name,
                region = index.regionOf(i),
                countryCode = cc,
                country = countryName(cc),
                position = LatLon(index.lat[i], index.lon[i]),
                population = index.population[i],
                bold = boldRanges(name, query),
            )
        }

    private fun countryName(cc: String): String = countries.getOrPut(cc) {
        runCatching { Locale.Builder().setRegion(cc).build().getDisplayCountry(SPANISH) }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: cc
    }

    private companion object {
        val EMPTY = SearchResults(emptyList(), emptyList())
        val SPANISH: Locale = Locale.Builder().setLanguage("es").build()
    }
}
