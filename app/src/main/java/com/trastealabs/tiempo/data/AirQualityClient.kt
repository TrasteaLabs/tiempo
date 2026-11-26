package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.AirHour
import com.trastealabs.tiempo.domain.AirQuality
import com.trastealabs.tiempo.domain.LatLon
import kotlinx.coroutines.Dispatchers
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
import kotlinx.serialization.json.long
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

interface AirSource {
    /** Lanza IOException si no llega o no se entiende la respuesta. */
    suspend fun fetch(position: LatLon): AirQuality
}

/**
 * H1 del 005. El aire y el polen por horas de CAMS, vía Open-Meteo: 10 variables y 5 días, que pesan 1,0 llamadas
 * (H2). Solo sale la posición redondeada (C9). La lectura es tolerante: lo que falta o llega null queda null, y una
 * hora sin ningún valor no se guarda (la cola del polen llega vacía).
 */
class OpenMeteoAirClient(
    private val http: OkHttpClient,
    baseUrl: String = "https://air-quality-api.open-meteo.com/v1/air-quality",
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : AirSource {
    private val base = baseUrl.toHttpUrl()

    internal fun requestUrl(position: LatLon): HttpUrl {
        val p = position.rounded()
        return base.newBuilder()
            .addQueryParameter("latitude", p.lat.toString())
            .addQueryParameter("longitude", p.lon.toString())
            .addQueryParameter("hourly", HOURLY.joinToString(","))
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("forecast_days", "5")
            .build()
    }

    override suspend fun fetch(position: LatLon): AirQuality {
        val p = position.rounded()
        val body = get(requestUrl(p))
        return withContext(Dispatchers.Default) {
            try {
                parse(body, p)
            } catch (e: IllegalArgumentException) {
                throw IOException("Respuesta del aire de Open-Meteo no válida", e)
            } catch (e: NoSuchElementException) {
                throw IOException("Respuesta del aire de Open-Meteo incompleta", e)
            }
        }
    }

    private fun parse(body: String, position: LatLon): AirQuality {
        val hourly = Json.parseToJsonElement(body).jsonObject.getValue("hourly").jsonObject
        val times = hourly.getValue("time").jsonArray.map { it.jsonPrimitive.long }
        val hours = times.mapIndexedNotNull { i, time ->
            fun value(key: String) = hourly.valueAt(key, i)
            fun index(key: String) = value(key)?.roundToInt()
            val hour = AirHour(
                time = time,
                aqi = index("european_aqi"),
                pm25Index = index("european_aqi_pm2_5"),
                pm10Index = index("european_aqi_pm10"),
                no2Index = index("european_aqi_nitrogen_dioxide"),
                ozoneIndex = index("european_aqi_ozone"),
                so2Index = index("european_aqi_sulphur_dioxide"),
                pm10 = value("pm10"),
                dust = value("dust"),
                olivePollen = value("olive_pollen"),
                grassPollen = value("grass_pollen"),
            )
            hour.takeUnless { it == AirHour(time) }
        }
        return AirQuality(position, nowSeconds(), hours)
    }

    private fun JsonObject.valueAt(key: String, i: Int): Double? =
        ((get(key) as? JsonArray)?.getOrNull(i) as? JsonPrimitive)?.doubleOrNull

    /** Cancelable de verdad: si vence el tope de 15 s del repositorio, la petición se corta en vez de seguir bajando. */
    private suspend fun get(url: HttpUrl): String = suspendCancellableCoroutine { cont ->
        val call = http.newCall(Request.Builder().url(url).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw IOException("El aire de Open-Meteo respondió ${it.code}")
                        it.body?.string() ?: throw IOException("El aire de Open-Meteo respondió sin cuerpo")
                    }
                    cont.resume(text)
                } catch (e: IOException) {
                    cont.resumeWithException(e)
                }
            }
        })
    }

    private companion object {
        /** En este orden (plan del 005): 10 variables, 1,0 llamadas con 5 días. */
        val HOURLY = listOf(
            "european_aqi", "european_aqi_pm2_5", "european_aqi_pm10", "european_aqi_nitrogen_dioxide",
            "european_aqi_ozone", "european_aqi_sulphur_dioxide", "pm10", "dust", "olive_pollen", "grass_pollen",
        )
    }
}
