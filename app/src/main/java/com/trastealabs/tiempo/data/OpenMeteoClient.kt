package com.trastealabs.tiempo.data

import com.trastealabs.tiempo.domain.CurrentConditions
import com.trastealabs.tiempo.domain.DayPoint
import com.trastealabs.tiempo.domain.HourPoint
import com.trastealabs.tiempo.domain.LatLon
import com.trastealabs.tiempo.domain.Place
import com.trastealabs.tiempo.domain.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

interface WeatherSource {
    /** El `place` del resultado queda vacío: lo rellena quien llama (WeatherRepository). */
    suspend fun fetch(position: LatLon): WeatherSnapshot
}

/** Redondea a 2 decimales (~1 km): lo único que sale del móvil (C9). */
fun LatLon.rounded(): LatLon = LatLon(lat.round2(), lon.round2())

private fun Double.round2(): Double = BigDecimal(this).setScale(2, RoundingMode.HALF_UP).toDouble()

class OpenMeteoClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.open-meteo.com/v1/forecast",
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : WeatherSource {

    internal fun requestUrl(position: LatLon): HttpUrl {
        val p = position.rounded()
        return baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", p.lat.toString())
            .addQueryParameter("longitude", p.lon.toString())
            .addQueryParameter("current", CURRENT)
            .addQueryParameter("hourly", HOURLY)
            .addQueryParameter("daily", DAILY)
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("past_days", "1")
            .addQueryParameter("forecast_days", "10")
            .build()
    }

    override suspend fun fetch(position: LatLon): WeatherSnapshot = withContext(Dispatchers.IO) {
        val p = position.rounded()
        val url = requestUrl(p)
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Open-Meteo respondió ${response.code}")
            response.body?.string() ?: throw IOException("Open-Meteo respondió sin cuerpo")
        }
        try {
            parse(body, p)
        } catch (e: IllegalArgumentException) {
            throw IOException("Respuesta de Open-Meteo no válida", e)
        } catch (e: NoSuchElementException) {
            throw IOException("Respuesta de Open-Meteo incompleta", e)
        }
    }

    private fun parse(body: String, position: LatLon): WeatherSnapshot {
        val root = Json.parseToJsonElement(body).jsonObject
        val zoneId = root.getValue("timezone").jsonPrimitive.content
        val zone = ZoneId.of(zoneId)

        val cur = root.getValue("current").jsonObject
        val current = CurrentConditions(
            temperatureC = cur.getValue("temperature_2m").jsonPrimitive.double,
            apparentC = cur.getValue("apparent_temperature").jsonPrimitive.double,
            weatherCode = cur.getValue("weather_code").jsonPrimitive.int,
            isDay = cur.getValue("is_day").jsonPrimitive.int == 1,
            windKmh = cur.doubleOrNull("wind_speed_10m"),
            windGustsKmh = cur.doubleOrNull("wind_gusts_10m"),
            windDirectionDeg = cur.doubleOrNull("wind_direction_10m")?.roundToInt(),
            humidityPct = cur.doubleOrNull("relative_humidity_2m")?.roundToInt(),
            dewPointC = cur.doubleOrNull("dew_point_2m"),
            visibilityM = cur.doubleOrNull("visibility"),
        )
        val currentTime = cur.getValue("time").jsonPrimitive.long

        val h = root.getValue("hourly").jsonObject
        val times = h.longs("time")
        val temps = h.doubles("temperature_2m")
        val probs = h.getValue("precipitation_probability").jsonArray
        val precip = h.doubles("precipitation")
        val codes = h.getValue("weather_code").jsonArray
        val isDay = h.getValue("is_day").jsonArray
        // Las de viento y humedad por horas son de la 004: si faltan, las horas quedan sin ellas.
        val winds = h["wind_speed_10m"]?.jsonArray
        val gusts = h["wind_gusts_10m"]?.jsonArray
        val directions = h["wind_direction_10m"]?.jsonArray
        val humidities = h["relative_humidity_2m"]?.jsonArray
        val dewPoints = h["dew_point_2m"]?.jsonArray
        val hourly = times.indices.map { i ->
            HourPoint(
                time = times[i],
                temperatureC = temps[i],
                precipProbability = probs[i].intOrNull(),
                precipMm = precip[i],
                weatherCode = codes[i].jsonPrimitive.int,
                isDay = isDay[i].jsonPrimitive.int == 1,
                windKmh = winds?.getOrNull(i)?.doubleOrNull(),
                windGustsKmh = gusts?.getOrNull(i)?.doubleOrNull(),
                windDirectionDeg = directions?.getOrNull(i)?.doubleOrNull()?.roundToInt(),
                humidityPct = humidities?.getOrNull(i)?.doubleOrNull()?.roundToInt(),
                dewPointC = dewPoints?.getOrNull(i)?.doubleOrNull(),
            )
        }

        val d = root.getValue("daily").jsonObject
        val dayTimes = d.longs("time")
        val maxs = d.doubles("temperature_2m_max")
        val mins = d.doubles("temperature_2m_min")
        val dayProbs = d.getValue("precipitation_probability_max").jsonArray
        val daySums = d.doubles("precipitation_sum")
        // Las variables de sol son de la 002: si faltan, los días quedan sin ellas.
        val sunrises = d["sunrise"]?.jsonArray
        val sunsets = d["sunset"]?.jsonArray
        val daylight = d["daylight_duration"]?.jsonArray
        // Las de la luna, de la 004. Salida o puesta a null: ese día no sale o no se pone.
        val moonrises = d["moonrise"]?.jsonArray
        val moonsets = d["moonset"]?.jsonArray
        val moonPhases = d["moon_phase"]?.jsonArray
        val today = Instant.ofEpochSecond(currentTime).atZone(zone).toLocalDate().toString()
        val allDays = dayTimes.indices.map { i ->
            DayPoint(
                date = Instant.ofEpochSecond(dayTimes[i]).atZone(zone).toLocalDate().toString(),
                maxC = maxs[i],
                minC = mins[i],
                precipProbabilityMax = dayProbs[i].intOrNull(),
                precipSumMm = daySums[i],
                sunrise = sunrises?.getOrNull(i)?.longOrNull(),
                sunset = sunsets?.getOrNull(i)?.longOrNull(),
                daylightSeconds = daylight?.getOrNull(i)?.doubleOrNull(),
                moonrise = moonrises?.getOrNull(i)?.longOrNull(),
                moonset = moonsets?.getOrNull(i)?.longOrNull(),
                moonPhase = moonPhases?.getOrNull(i)?.doubleOrNull(),
            )
        }
        val daily = allDays.filter { it.date >= today }.take(10)
        // El día que trae past_days=1: solo para comparar con ayer.
        val yesterday = allDays.lastOrNull { it.date < today }

        return WeatherSnapshot(
            place = Place("", "", position),
            zoneId = zoneId,
            fetchedAt = nowSeconds(),
            current = current,
            hourly = hourly,
            daily = daily,
            yesterday = yesterday,
        )
    }

    private fun JsonObject.longs(key: String): List<Long> = getValue(key).jsonArray.map { it.jsonPrimitive.long }

    private fun JsonObject.doubles(key: String): List<Double> = getValue(key).jsonArray.map { it.jsonPrimitive.double }

    private fun JsonElement.intOrNull(): Int? = if (this is JsonNull) null else (this as JsonPrimitive).int

    private fun JsonElement.longOrNull(): Long? = if (this is JsonNull) null else (this as JsonPrimitive).long

    private fun JsonElement.doubleOrNull(): Double? = if (this is JsonNull) null else (this as JsonPrimitive).double

    /** Variable que puede faltar o venir a null: lo secundario no tumba la previsión. */
    private fun JsonObject.doubleOrNull(key: String): Double? = get(key)?.doubleOrNull()

    private companion object {
        const val CURRENT = "temperature_2m,apparent_temperature,weather_code,is_day," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,relative_humidity_2m,dew_point_2m,visibility"
        const val HOURLY = "temperature_2m,precipitation_probability,precipitation,weather_code,is_day," +
            "wind_speed_10m,wind_gusts_10m,wind_direction_10m,relative_humidity_2m,dew_point_2m"
        const val DAILY = "temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum," +
            "sunrise,sunset,daylight_duration,moonrise,moonset,moon_phase"
    }
}
