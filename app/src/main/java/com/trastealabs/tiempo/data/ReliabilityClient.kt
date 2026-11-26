package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.ENSEMBLE_MODELS
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.ModelDay
import com.trastealabs.tiempo.domain.Reliability
import com.trastealabs.tiempo.domain.WeatherModel
import com.trastealabs.tiempo.domain.summarizeDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

interface ReliabilitySource {
    /** Lanza IOException si no llega el conjunto: sin él no hay nivel. */
    suspend fun fetch(position: LatLon): Reliability
}

/**
 * Los diarios de los seis modelos y los escenarios del conjunto, pedidos a la vez y resumidos por día.
 * Si fallan solo los modelos, los días van sin ellos (se usa lo que llegue).
 */
class OpenMeteoReliabilityClient(
    private val http: OkHttpClient,
    forecastUrl: String = "https://api.open-meteo.com/v1/forecast",
    ensembleUrl: String = "https://ensemble-api.open-meteo.com/v1/ensemble",
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : ReliabilitySource {
    private val forecastBase = forecastUrl.toHttpUrl()
    private val ensembleBase = ensembleUrl.toHttpUrl()

    internal fun modelsUrl(position: LatLon): HttpUrl = forecastBase.query(position)
        .addQueryParameter("daily", "weather_code,temperature_2m_max,precipitation_sum")
        .addQueryParameter("models", WeatherModel.entries.joinToString(",") { it.forecastId })
        .addQueryParameter("timezone", "auto")
        .addQueryParameter("forecast_days", DAYS)
        .build()

    internal fun ensembleUrl(position: LatLon): HttpUrl = ensembleBase.query(position)
        .addQueryParameter("daily", "$MAX,$PRECIP")
        .addQueryParameter("models", ENSEMBLE_MODELS.joinToString(","))
        .addQueryParameter("timezone", "auto")
        .addQueryParameter("forecast_days", DAYS)
        .build()

    /** Solo sale la posición redondeada (C9). */
    private fun HttpUrl.query(position: LatLon): HttpUrl.Builder {
        val p = position.rounded()
        return newBuilder()
            .addQueryParameter("latitude", p.lat.toString())
            .addQueryParameter("longitude", p.lon.toString())
    }

    override suspend fun fetch(position: LatLon): Reliability = withContext(Dispatchers.IO) {
        val p = position.rounded()
        coroutineScope {
            val models = async { fetchModels(p) }
            val ensemble = get(ensembleUrl(p))
            try {
                parseEnsemble(ensemble, p, models.await())
            } catch (e: IllegalArgumentException) {
                throw IOException("Conjunto de Open-Meteo no válido", e)
            } catch (e: NoSuchElementException) {
                throw IOException("Conjunto de Open-Meteo incompleto", e)
            }
        }
    }

    /** Por fecha, los modelos que llegan ese día. Vacío si la petición falla o llega rara: no tumba el nivel. */
    private suspend fun fetchModels(p: LatLon): Map<String, List<ModelDay>> = try {
        parseModels(get(modelsUrl(p)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    private fun parseModels(body: String): Map<String, List<ModelDay>> {
        val daily = Json.parseToJsonElement(body).jsonObject.getValue("daily").jsonObject
        return daily.dates().withIndex().associate { (i, date) ->
            date to WeatherModel.entries.mapNotNull { m ->
                // Más allá de su horizonte el modelo trae null: ese día no entra.
                val code = daily.valueAt("weather_code_${m.forecastId}", i) ?: return@mapNotNull null
                val max = daily.valueAt("${MAX}_${m.forecastId}", i) ?: return@mapNotNull null
                val rain = daily.valueAt("${PRECIP}_${m.forecastId}", i) ?: return@mapNotNull null
                ModelDay(m, code.roundToInt(), max, rain)
            }
        }
    }

    /**
     * Cada clave que empieza por `temperature_2m_max` es un escenario (el control sin número, el resto `_memberNN`,
     * con el nombre del modelo cambiado); su lluvia es la de `precipitation_sum` con el mismo final.
     * Así no depende de cómo llame Open-Meteo a cada uno.
     */
    private fun parseEnsemble(body: String, position: LatLon, models: Map<String, List<ModelDay>>): Reliability {
        val root = Json.parseToJsonElement(body).jsonObject
        val zoneId = root.getValue("timezone").jsonPrimitive.content
        val daily = root.getValue("daily").jsonObject
        val scenarios = daily.keys.filter { it.startsWith(MAX) }
        val days = daily.dates().mapIndexedNotNull { i, date ->
            val maxima = ArrayList<Double>(scenarios.size)
            val precip = ArrayList<Double>(scenarios.size)
            for (key in scenarios) {
                val max = daily.valueAt(key, i) ?: continue
                val rain = daily.valueAt(PRECIP + key.removePrefix(MAX), i) ?: continue
                maxima += max
                precip += rain
            }
            if (maxima.isEmpty()) null else summarizeDay(date, maxima, precip, models[date].orEmpty())
        }
        if (days.isEmpty()) throw IOException("El conjunto de Open-Meteo llegó sin escenarios")
        return Reliability(position, zoneId, nowSeconds(), days)
    }

    /** Sin `timeformat`, las fechas llegan como "2026-11-03" en la hora local del sitio. */
    private fun JsonObject.dates(): List<String> = getValue("time").jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.valueAt(key: String, i: Int): Double? =
        ((get(key) as? JsonArray)?.getOrNull(i) as? JsonPrimitive)?.doubleOrNull

    /** Cancelable de verdad: si vence el tope de 30 s, la petición se corta en vez de seguir bajando. */
    private suspend fun get(url: HttpUrl): String = suspendCancellableCoroutine { cont ->
        val call = http.newCall(Request.Builder().url(url).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use {
                        if (!it.isSuccessful) throw IOException("Open-Meteo respondió ${it.code}")
                        it.body?.string() ?: throw IOException("Open-Meteo respondió sin cuerpo")
                    }
                    cont.resume(body)
                } catch (e: IOException) {
                    cont.resumeWithException(e)
                }
            }
        })
    }

    private companion object {
        const val MAX = "temperature_2m_max"
        const val PRECIP = "precipitation_sum"
        const val DAYS = "10"
    }
}
